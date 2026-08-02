package com.techy.noti_filter.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.techy.noti_filter.AI_Model.NotificationPredictor;
import com.techy.noti_filter.AI_Model.PredictionCSVWriter;
import com.techy.noti_filter.AI_Model.PredictionResult;
import com.techy.noti_filter.AI_Model.Preprocessor;
import com.techy.noti_filter.dao.NotificationDao;
import com.techy.noti_filter.dao.NotificationMapper;
import com.techy.noti_filter.db.AppDatabase;
import com.techy.noti_filter.model.NotificationData;
import com.techy.noti_filter.utils.NotificationIntentCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationService extends NotificationListenerService {

    private final Map<String, NotificationData> notificationMap = new HashMap<>();
    private final Set<String> recentHashes = new HashSet<>();

    private static final String[] IMPORTANT_PEOPLE = {
            "mom", "dad", "amma", "nanna", "mother", "father", "bro", "vadhina"
    };

    private AppDatabase db;
    private NotificationDao dao;
    private ExecutorService executor;

    private Preprocessor preprocessor;
    private NotificationPredictor predictor;
    private BroadcastReceiver modelReloadReceiver;

    /** Sent by ModelDecisionReceiver after the user accepts a retrained
     * model - this is the ONLY way this running service's Preprocessor/
     * NotificationPredictor ever get swapped without a full restart. */
    public static final String ACTION_RELOAD_MODEL = "com.techy.noti_filter.ACTION_RELOAD_MODEL";

    @Override
    public void onCreate() {
        super.onCreate();
        db = AppDatabase.getInstance(getApplicationContext());
        dao = db.notificationDao();
        executor = Executors.newSingleThreadExecutor();

        preprocessor = new Preprocessor(getApplicationContext());
        predictor = new NotificationPredictor(getApplicationContext());

        if (!preprocessor.isReady()) {
            Log.e("MODEL", "Preprocessor failed to load assets");
        }
        int expected = preprocessor.getExpectedInputDim();
        Log.d("MODEL", "Preprocessor produces dim=" + expected
                + " | model expects=" + predictor.getExpectedInputSize());

        modelReloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                android.util.Log.i("NF_TRAIN", "NotificationService received reload broadcast - swapping model now");
                Log.d("MODEL", "Reload signal received - swapping in the accepted model");
                if (predictor != null) predictor.close();
                preprocessor = new Preprocessor(getApplicationContext());
                predictor = new NotificationPredictor(getApplicationContext());
                Log.d("MODEL", "Reloaded. New expected dim=" + preprocessor.getExpectedInputDim()
                        + " | model expects=" + predictor.getExpectedInputSize());

                SharedPreferences status = getApplicationContext()
                        .getSharedPreferences("model_status", Context.MODE_PRIVATE);
                status.edit()
                        .putLong("last_reload_millis", System.currentTimeMillis())
                        .putInt("active_input_dim", preprocessor.getExpectedInputDim())
                        .apply();

                android.util.Log.i("NF_TRAIN", "Reload complete - active model input dim="
                        + preprocessor.getExpectedInputDim() + ", source=downloaded (accepted model)");
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_RELOAD_MODEL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(modelReloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(modelReloadReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (modelReloadReceiver != null) {
            unregisterReceiver(modelReloadReceiver);
            modelReloadReceiver = null;
        }
    }
    // =========================================
    // 🔴 REMOVED
    // =========================================
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {

        String key = sbn.getKey();
        NotificationData data = notificationMap.get(key);

        if (data == null) return;

        String action = mapAction(reason);


        long timeToInteract = System.currentTimeMillis() - data.timestamp;

        data.timeToInteract = timeToInteract;

        int label = generateLabel(action, timeToInteract, data);

        data.app = data.packageName;

        data.actionTaken = action;
        data.label=label;

        executor.execute(() -> {
            long id = dao.insert(NotificationMapper.toEntity(data));
            Log.d("ROOM_DEBUG", "Inserted row id: " + id);
        });

        SharedPreferences favContacts = getSharedPreferences("favContacts", MODE_PRIVATE);
        String favContacts1 = favContacts.getString("favContacts", null);

        Log.d("favContacts", String.valueOf(favContacts1));





        if (data.actionTaken.equals("app_removed")){
            notificationMap.remove(key);
        }else {
            saveToCSV(data, action, timeToInteract, label);
        }
        notificationMap.remove(key);
    }

    // =========================================
    // 🟢 POSTED
    // =========================================
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        Notification n = sbn.getNotification();
        Bundle extras = n.extras;

        String pkg = sbn.getPackageName();

        PackageManager pm = getPackageManager();

        PendingIntent pi = sbn.getNotification().contentIntent;

        if (pi != null) {
            NotificationIntentCache.put(sbn.getKey(), pi);
        }

        Drawable appIcon = null;
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
            appIcon = pm.getApplicationIcon(appInfo);



            // You can use this drawable in ImageView
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        String title = normalizeText(getText(extras, Notification.EXTRA_TITLE));
        String body = normalizeText(getText(extras, Notification.EXTRA_TEXT));

        if (body.isEmpty()) {
            body = normalizeText(getText(extras, Notification.EXTRA_BIG_TEXT));
        }

        // ❌ Skip system noise
        if (isSystemNoise(pkg, title)) return;

        //REMOVE EMPTY NOTIFICATIONS
        if (title.isEmpty() && body.isEmpty()) {
            return; // ⛔ skip useless/system notifications
        }

        // ❌ Deduplication
        String hash = (pkg + title + body).toLowerCase();
        if (recentHashes.contains(hash)) return;
        recentHashes.add(hash);

        NotificationData d = new NotificationData();

        Bitmap bitmap =drawableToBitmap(appIcon);

        d.packageName = pkg;
        if (bitmap != null) {
            d.appIcon = bitmapToByteArray(bitmap);
        }else {
            Log.e("BitmapConversion", "Bitmap is null");
        }

        d.title = title;
        d.body = body;
        d.subText = getText(extras, "android.subText");
        d.sender = getText(extras, "android.title");

        d.timestamp = System.currentTimeMillis();
        d.postTime= System.currentTimeMillis();

        d.category = n.category != null ? n.category : "unknown";
        d.priority = n.priority;

        d.actionCount = (n.actions != null) ? n.actions.length : 0;
        d.badgeCount = n.number;

        d.channelId = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? n.getChannelId() : "";

        Uri sound = n.sound;
        d.soundType = (sound != null) ? sound.toString() : "none";


        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(sbn.getPostTime());

        d.hour = cal.get(Calendar.HOUR_OF_DAY);
        d.day = cal.get(Calendar.DAY_OF_WEEK);

        d.type = detectType(pkg, d.channelId);

        d.titleLength = safe(title).length();
        d.bodyLength = safe(body).length();

        notificationMap.put(sbn.getKey(), d);

        // PREDICTIONS


        Log.d("MODEL", "Expected = " + predictor.getExpectedInputSize());



        float[] input = preprocessor.process(d);

        Log.d("DEBUG", "Input length = " + input.length);

        Log.d("PACKAGE", "app package = " + d.packageName);

        float[] prediction = predictor.predict(input);

        Log.d("MODEL",
                "Prediction = "
                        + Arrays.toString(prediction));

        PredictionResult result =
                predictor.getPredictionResult(prediction);

        Log.d("MODEL",
                "Class="
                        + result.predictedClass
                        + " Confidence="
                        + result.confidence);

        int predictedIndex = 0;
        float max = prediction[0];

        for (int i = 1; i < prediction.length; i++) {
            if (prediction[i] > max) {
                max = prediction[i];
                predictedIndex = i;
            }
        }

        String[] row = {
                d.title,
                d.body,
                String.valueOf(d.title.length()),
                String.valueOf(d.body.length()),
                String.valueOf(d.priority),
                String.valueOf(d.hour),
                d.type,
                d.packageName,
                String.valueOf(predictedIndex)
        };

        PredictionCSVWriter.writePrediction(getApplicationContext(),row);


        // --- Notification suppression - added, does not affect anything above ---
        boolean suppress = NotificationSuppressionPolicy.shouldSuppress(
                getApplicationContext(),
                result.predictedClass,
                result.confidence,
                d.packageName,
                sbn
        );
        if (suppress) {
            cancelNotification(sbn.getKey());
            Log.i("NF_SUPPRESS", "Cancelled notification from " + d.packageName);
        }
    }

    // =========================================
    // 🧠 LABEL LOGIC
    // =========================================
    private int generateLabel(String action, long time, NotificationData d) {

        String text = (safe(d.title) + " " + safe(d.body) + " " +
                safe(d.subText) + " " + safe(d.sender)).toLowerCase();

        String pkg = safe(d.packageName).toLowerCase();

        // 🚨 Spam
        if (contains(text, "sale","offer","discount","free","coupon","promo","buy","price","get","off")) return 0;

        int label = 2;

        if ("replied".equals(action)) {
            if (contains(text,"otp","credited","debited","upi","bank","₹","urgent","delivery","call","verification code","payment")) {
                return 4;
            }
            return 4;
        }
        else if ("opened".equals(action)) {
            if (contains(text,"otp","credited","debited","upi","bank","₹","urgent","delivery","call","verification code","payment")) {
                return 4;
            }
            return (time < 5000) ? 4 : 3;
        }
        else if ("ignored".equals(action)) {
            if (contains(text,"otp","credited","debited","upi","bank","₹","urgent","delivery","call","verification code","payment")) {
                return 4; // slightly higher if important
            }
            return 2;
        }
        else if ("dismissed".equals(action)) {
            if (contains(text,"otp","credited","debited","upi","bank","₹","urgent","delivery","call","verification code","payment")) {
                return 4; // not fully useless if important content
            }
            return (time < 2000) ? 0 : 1;
        }
        else if ("auto_timeout".equals(action)) {
            if (contains(text,"otp","credited","debited","upi","bank","₹","urgent","delivery","call","verification code","payment")) {
                return 2;
            }
            return 1;
        }
        else if ("app_removed".equals(action)) {
            if (contains(text,"otp","credited","debited","upi","bank","₹","urgent","delivery","call","verification code","payment")) {
                return 2;
            }
            return 1;
        }


        // 💰 Urgent / financial
        if (contains(text,"otp","credited","debited","upi","bank","₹","urgent","delivery","call","verification code","payment")) {
            label = Math.max(label, 4);
        }

        // 👨‍👩‍👧 Important people
        String sender = safe(d.sender).toLowerCase();
        for (String p : IMPORTANT_PEOPLE) {
            if (sender.contains(p)) return 4;
        }

        // 💬 Messaging boost
        if (pkg.contains("whatsapp") || pkg.contains("sms") || pkg.contains("messaging")) {
            label = Math.max(label, 3);
        }

        return label;
    }

    // =========================================
    // 📁 CSV
    // =========================================
    private void saveToCSV(NotificationData d, String action, long timeToInteract, int label) {

        File file = new File(getExternalFilesDir(null), "notifications_dataset_28Jul.csv");
        File file_main = new File(getExternalFilesDir(null), "notifications_dataset_main.csv");

        boolean newFile_main = !file_main.exists();
        boolean newFile = !file.exists();


        try (FileWriter writer = new FileWriter(file_main, true)) {
            if (newFile_main) {
                writer.append("title,body,sender,category,priority,")
                        .append("action_taken,action_count,")
                        .append("has_media,channel,")
                        .append("hour,day,app,type,")
                        .append("title_len,body_len,")
                        .append("time_to_interact,label,postTime\n");
            }

            writer.append(clean(d.title)).append(",")
                    .append(clean(d.body)).append(",")
                    .append(clean(d.sender)).append(",")
                    .append(d.category).append(",")
                    .append(String.valueOf(d.priority)).append(",")

                    .append(action).append(",")
                    .append(String.valueOf(d.actionCount)).append(",")

                    .append(String.valueOf(d.hasMedia)).append(",")
                    .append(d.channelId).append(",")
                    .append(String.valueOf(d.hour)).append(",")
                    .append(String.valueOf(d.day)).append(",")
                    .append(d.packageName).append(",")
                    .append(d.type).append(",")
                    .append(String.valueOf(d.titleLength)).append(",")
                    .append(String.valueOf(d.bodyLength)).append(",")
                    .append(String.valueOf(timeToInteract)).append(",")
                    .append(String.valueOf(label)).append(",")
                    .append(String.valueOf(d.postTime)).append("\n");
        }catch (IOException e) {
            e.printStackTrace();
        }



        try (FileWriter writer = new FileWriter(file, true)) {

            if (newFile) {
                writer.append("title,body,sender,category,priority,")
                        .append("action_taken,action_count,")
                        .append("has_media,channel,")
                        .append("hour,day,app,type,")
                        .append("title_len,body_len,")
                        .append("time_to_interact,label,postTime\n");
            }

            writer.append(clean(d.title)).append(",")
                    .append(clean(d.body)).append(",")
                    .append(clean(d.sender)).append(",")
                    .append(d.category).append(",")
                    .append(String.valueOf(d.priority)).append(",")

                    .append(action).append(",")
                    .append(String.valueOf(d.actionCount)).append(",")

                    .append(String.valueOf(d.hasMedia)).append(",")
                    .append(d.channelId).append(",")
                    .append(String.valueOf(d.hour)).append(",")
                    .append(String.valueOf(d.day)).append(",")
                    .append(d.packageName).append(",")
                    .append(d.type).append(",")
                    .append(String.valueOf(d.titleLength)).append(",")
                    .append(String.valueOf(d.bodyLength)).append(",")
                    .append(String.valueOf(timeToInteract)).append(",")
                    .append(String.valueOf(label)).append(",")
                    .append(String.valueOf(d.postTime)).append("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================================
    // 🧰 HELPERS
    // =========================================

    private String normalizeText(String text) {
        if (text == null) return "";

        // Convert to lowercase
        text = text.toLowerCase();

        // Remove replacement characters
        text = text.replace("\uFFFD", " "); // �

        // Replace all non-letter/number with space
        text = text.replaceAll("[^\\p{L}\\p{N}]+", " ");

        // Normalize spaces
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }

    private boolean isSystemNoise(String pkg, String title) {
        return pkg.contains("systemui") ||
                title.toLowerCase().contains("charging") ||
                title.toLowerCase().contains("running");
    }


    public Bitmap drawableToBitmap(Drawable drawable) {
        try {
            if (drawable == null) {
                return null; // nothing to convert
            }

            // Case 1: already bitmap
            if (drawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    return bitmap;
                }
            }

            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();

            // Some system / work profile drawables return invalid size
            if (width <= 0) width = 1;
            if (height <= 0) height = 1;

            Bitmap bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return bitmap;

        } catch (Exception e) {
            Log.e("NotificationService", "drawableToBitmap failed", e);
            return null;
        }
    }
    public byte[] bitmapToByteArray(Bitmap bitmap) {
        try {
            if (bitmap == null) {
                throw new IllegalArgumentException("Bitmap is null");
            }

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();

        } catch (IllegalArgumentException e) {
            Log.e("BitmapConversion", e.getMessage());
        } catch (Exception e) {
            Log.e("BitmapConversion", "Error converting bitmap to byte array", e);
        }

        return null;

    }
    private String getText(Bundle extras, String key) {
        if (extras == null) return "";
        CharSequence cs = extras.getCharSequence(key);
        return cs != null ? cs.toString() : "";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private boolean contains(String text, String... keys) {
        for (String k : keys) if (text.contains(k)) return true;
        return false;
    }

    private String clean(String text) {
        return text == null ? "" : text.replace(",", " ").replace("\n", " ");
    }

    private String detectType(String pkg, String channel) {

        if (pkg == null) return "other";

        pkg = pkg.toLowerCase();
        channel = (channel == null) ? "" : channel.toLowerCase();

        // 🔥 CHANNEL PRIORITY (more accurate than package)
        if (channel.contains("alert")) return "alerts";
        if (channel.contains("reminder")) {
            if (pkg.contains("game")||pkg.contains("games")){
                return "gaming";
            }
            if (pkg.contains("facebook")){
                return "spam";
            }
            return "reminder";
        }
        if (channel.contains("sports")) return "entertainment";
        if (channel.contains("call")) return "call";
        if (channel.contains("offer") || channel.contains("promo")) return "offers";
        if (channel.contains("weather")) return "weather";

        // 📱 PACKAGE-BASED DETECTION

        // Messaging
        if (pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messag"))
            return "message";

        // Email
        if (pkg.contains("gmail") || pkg.contains("outlook") || pkg.contains("mail"))
            return "email";

        // Social
        if (pkg.contains("instagram") || pkg.contains("facebook") ||
                pkg.contains("twitter") || pkg.contains("snapchat") ||
                pkg.contains("linkedin"))
            return "social";

        // Finance
        if (pkg.contains("bank") || pkg.contains("sbi") || pkg.contains("hdfc") ||
                pkg.contains("icici") || pkg.contains("axis"))
            return "finance";

        // Payments / transactional
        if (pkg.contains("phonepe") || pkg.contains("gpay") || pkg.contains("paytm"))
            return "transactional";

        // Shopping
        if (pkg.contains("amazon") || pkg.contains("flipkart") || pkg.contains("meesho"))
            return "shopping";

        // Food
        if (pkg.contains("zomato") || pkg.contains("swiggy"))
            return "food";

        // Transport
        if (pkg.contains("uber") || pkg.contains("ola") || pkg.contains("rapido"))
            return "transport";

        // Grocery
        if (pkg.contains("zepto") || pkg.contains("blinkit") || pkg.contains("bigbasket"))
            return "groceries";

        // Entertainment
        if (pkg.contains("spotify") || pkg.contains("music"))
            return "music";

        if (pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("prime")||pkg.contains("hotstar"))
            return "entertainment";

        // Gaming
        if (pkg.contains("game") || pkg.contains("candycrush"))
            return "gaming";

        // Work
        if (pkg.contains("office") || pkg.contains("slack") || pkg.contains("teams"))
            return "work";

        return "other";
    }

    private String mapAction(int reason) {
        switch (reason) {
            case REASON_CLICK: return "opened";
            case REASON_CANCEL: return "dismissed";
            case REASON_APP_CANCEL: return "app_removed";
            case REASON_TIMEOUT: return "auto_timeout";
            default: return "ignored";
        }
    }
    public Bitmap byteArrayToBitmap(byte[] bytes) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
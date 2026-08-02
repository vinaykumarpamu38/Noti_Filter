package com.techy.noti_filter.service;

import android.app.Notification;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.techy.noti_filter.AI_Model.ModelFileSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether a notification should be suppressed (removed from the
 * shade shortly after it posts) based on the model's own prediction.
 *
 * IMPORTANT - what this class does NOT do: it never touches CSV writing,
 * Room inserts, or label generation. Call shouldSuppress() strictly AFTER
 * your existing data-recording code has already run for this notification
 * - suppression only decides whether to *also* cancel it from the shade,
 * never whether to record it. Every notification still gets stored for
 * the next training run, suppressed or not - that data pipeline is
 * completely unchanged.
 *
 * ALL of the following must be true to suppress:
 *   1. A downloaded/accepted model is currently active (never the bundled
 *      default - the whole feature is inert until then, per design).
 *   2. Predicted label is 0.
 *   3. Confidence is at or above CONFIDENCE_THRESHOLD.
 *   4. The source app is not on the allowlist below.
 *   5. The notification isn't ongoing/foreground-service/system-critical.
 */
public class NotificationSuppressionPolicy {

    private static final String TAG = "NF_SUPPRESS";

    /** Requested range was 0.95-0.98 - 0.97 chosen as a reasonably
     * conservative middle value. Change this single constant to tune it. */
    public static final float CONFIDENCE_THRESHOLD = 0.97f;

    /** Package names that should NEVER be suppressed, regardless of
     * prediction or confidence. Seeded with a few obvious, widely-relevant
     * examples (messaging + common India-market payment apps, since OTP/
     * payment confirmations are exactly the kind of thing that must never
     * be silently hidden) - review and extend this list yourself, since
     * only you know your actual banking/OTP apps. */
    private static final Set<String> APP_ALLOWLIST = new HashSet<>();
    static {
        APP_ALLOWLIST.add("com.whatsapp");
        APP_ALLOWLIST.add("com.google.android.apps.messaging");   // Google Messages (SMS/OTP)
        APP_ALLOWLIST.add("com.google.android.apps.nbu.paisa.user"); // Google Pay
        APP_ALLOWLIST.add("com.phonepe.app");
        APP_ALLOWLIST.add("net.one97.paytm");
        // Add your own bank apps' package names here, e.g.:
        // APP_ALLOWLIST.add("com.yourbank.mobilebanking");
    }

    /**
     * @param context       any context - only used to check which model is active
     * @param predictedLabel result.predictedClass from PredictionResult
     * @param confidence     result.confidence from PredictionResult
     * @param packageName    the notification's source app, e.g. sbn.getPackageName()
     * @param sbn            the StatusBarNotification passed into onNotificationPosted
     */
    private static final String PREFS_NAME = "suppression_settings";
    private static final String KEY_ENABLED = "enabled";

    /** Master on/off switch for the entire feature - defaults to false
     * (disabled) until you explicitly turn it on. This is the actual
     * "flag" - everything else in this class only matters once this is true. */
    public static boolean isEnabled(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.i(TAG, "Suppression feature " + (enabled ? "ENABLED" : "DISABLED") + " by user toggle");
    }

    public static boolean shouldSuppress(Context context, int predictedLabel, float confidence,
                                         String packageName, StatusBarNotification sbn) {

        // Gate -1: the actual on/off flag, checked before anything else
        if (!isEnabled(context)) {
            Log.i(TAG, "Not suppressing - feature is disabled (toggle it on in Settings)");
            return false;
        }

        // Gate 0: only ever active with a downloaded, user-accepted model.
        // Never trust the bundled generic default enough to hide anything.
        boolean usingDownloadedModel = new ModelFileSource(context).isUsingDownloadedModel();
        if (!usingDownloadedModel) {
            Log.i(TAG, "Not suppressing - still on bundled default model, feature is inactive until a retrained model is accepted");
            return false;
        }

        // Gate 1: must be predicted label 0
        if (predictedLabel != 0) {
            Log.i(TAG, "Not suppressing - predicted label is " + predictedLabel + ", not 0");
            return false;
        }

        // Gate 2: confidence threshold
        if (confidence < CONFIDENCE_THRESHOLD) {
            Log.i(TAG, "Not suppressing - confidence " + confidence + " is below threshold " + CONFIDENCE_THRESHOLD);
            return false;
        }

        // Gate 3: app allowlist
        if (APP_ALLOWLIST.contains(packageName)) {
            Log.i(TAG, "Not suppressing - " + packageName + " is on the allowlist");
            return false;
        }

        // Gate 4: ongoing / foreground service / system-critical notifications
        String reason = describeIfProtected(sbn);
        if (reason != null) {
            Log.i(TAG, "Not suppressing - notification is protected (" + reason + ")");
            return false;
        }

        Log.i(TAG, "SUPPRESSING - label=0, confidence=" + confidence
                + " (>= " + CONFIDENCE_THRESHOLD + "), app=" + packageName
                + " not allowlisted, not a protected notification");
        return true;
    }

    /** Returns a short reason string if this notification should never be
     * touched regardless of prediction, or null if it's fair game. */
    private static String describeIfProtected(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();

        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            return "FLAG_ONGOING_EVENT";
        }
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return "FLAG_FOREGROUND_SERVICE";
        }

        String category = n.category;
        if (Notification.CATEGORY_CALL.equals(category)
                || Notification.CATEGORY_ALARM.equals(category)
                || Notification.CATEGORY_SERVICE.equals(category)
                || Notification.CATEGORY_TRANSPORT.equals(category)
                || Notification.CATEGORY_SYSTEM.equals(category)) {
            return "category=" + category;
        }

        return null;
    }
}
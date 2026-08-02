package com.techy.noti_filter.sync;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.techy.noti_filter.AI_Model.ModelFileSource;
import com.techy.noti_filter.R;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs once a week (see TrainingScheduler for the exact Saturday 9am IST
 * timing). Does the entire upload -> train -> download loop that used to
 * require tapping "Sync to Drive" - the button is gone now; this Worker is
 * the only thing that triggers a retrain.
 *
 * Every call here is normally callback-based (DriveSyncManager,
 * CloudTrainingClient), but Worker.doWork() must return synchronously, so
 * each step is bridged with a CountDownLatch. This already runs on a
 * background thread (WorkManager's contract), so blocking here is safe -
 * this is the standard way to adapt callback APIs into a Worker.
 *
 * IMPORTANT LIMITATION, stated plainly rather than hidden: this downloads
 * the retrained files into a STAGING location (pending_model/), not the
 * active one Preprocessor/NotificationPredictor read from. Nothing is used
 * for real inference until the user taps "Yes" on the resulting
 * notification (see ModelDecisionReceiver). Also: because
 * NotificationService.java constructs Preprocessor/NotificationPredictor
 * once and keeps them as long-lived fields, a newly-accepted model only
 * takes effect the next time that service restarts (e.g. next reboot, or
 * the listener getting rebound) - there's no live hot-swap without
 * touching NotificationService.java, which is intentionally left alone.
 */
public class WeeklyTrainingWorker extends Worker {

    private static final String TAG = "WeeklyTrainingWorker";
    private static final String CHANNEL_ID = "model_updates";
    private static final int NOTIFICATION_ID = 4201;

    public static final String ACTION_ACCEPT = "com.techy.noti_filter.ACTION_ACCEPT_MODEL";
    public static final String ACTION_REJECT = "com.techy.noti_filter.ACTION_REJECT_MODEL";
    public static final String PENDING_MODEL_DIR = "pending_model";

    /** Single tag for the whole training pipeline's milestone events -
     * `adb logcat | grep NF_TRAIN` shows the entire flow in order, across
     * every class involved, without needing to grep each class separately. */
    public static final String TRAIN_LOG = "NF_TRAIN";

    public WeeklyTrainingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Log.i(TRAIN_LOG, "===== Training run started =====");

        if (!TrainingRunLock.tryAcquire(context)) {
            Log.i(TRAIN_LOG, "STOPPED: another run is already in progress (mid-flight, hasn't recorded "
                    + "a history entry yet, so hasPendingEntry alone can't catch this)");
            return Result.success(); // correctly-refused duplicate, not a failure
        }

        try {
            if (AccuracyHistoryStore.hasPendingEntry(context)) {
                Log.i(TRAIN_LOG, "STOPPED: a previous run's Yes/No decision is still unanswered - "
                        + "respond to that notification before a new run can start");
                return Result.success(); // correct outcome, not a failure - nothing to retry
            }

            DriveSyncManager driveSyncManager = new DriveSyncManager();

            if (!driveSyncManager.isSignedIn(context)) {
                Log.i(TRAIN_LOG, "STOPPED: Drive not connected yet - connect it from the dashboard first");
                Log.w(TAG, "Drive not connected - skipping this week's training run");
                return Result.success(); // not worth retrying; nothing to do until the user connects Drive
            }

            File localCsv = new File(context.getExternalFilesDir(null), "notifications_dataset_main.csv");
            if (!localCsv.exists()) {
                Log.i(TRAIN_LOG, "STOPPED: no local dataset file found yet at " + localCsv);
                Log.w(TAG, "No local data yet - skipping this week's training run");
                return Result.success();
            }
            Log.i(TRAIN_LOG, "Local dataset found: " + localCsv + " (" + localCsv.length() + " bytes)");

            Log.i(TRAIN_LOG, "Step 1/4: uploading CSV to Drive...");
            String driveFileId = blockingUpload(driveSyncManager, context, localCsv);
            if (driveFileId == null) {
                // Result.failure(), not retry() - a genuine failure ends here.
                // The next attempt only happens via an explicit new trigger
                // (manual tap or next Saturday), never WorkManager's own
                // automatic backoff timer running this again on its own.
                Log.e(TRAIN_LOG, "FAILED at step 1/4 (upload) - stopping, no automatic retry");
                return Result.failure();
            }
            Log.i(TRAIN_LOG, "Step 1/4 OK - Drive file id: " + driveFileId);

            Log.i(TRAIN_LOG, "Step 2/4: calling Cloud Function to retrain...");
            CloudTrainingClient client = new CloudTrainingClient(driveSyncManager);
            RetrainOutcome outcome = blockingTriggerRetrain(client, context, driveFileId);
            if (outcome == null) {
                Log.e(TRAIN_LOG, "FAILED at step 2/4 (retrain) - stopping, no automatic retry");
                return Result.failure();
            }
            Log.i(TRAIN_LOG, "Step 2/4 OK - accuracy=" + outcome.accuracy
                    + " kappa=" + outcome.kappa + " top2=" + outcome.top2Accuracy
                    + " (" + outcome.uploadedFiles.size() + " files uploaded back to Drive)");

            Log.i(TRAIN_LOG, "Step 3/4: downloading retrained files into pending_model/...");
            File pendingDir = new File(context.getFilesDir(), PENDING_MODEL_DIR);
            boolean downloaded = blockingDownload(driveSyncManager, context, outcome.uploadedFiles, pendingDir);
            if (!downloaded) {
                Log.e(TRAIN_LOG, "FAILED at step 3/4 (download) - stopping, no automatic retry");
                return Result.failure();
            }
            Log.i(TRAIN_LOG, "Step 3/4 OK - files staged at " + pendingDir);

            Log.i(TRAIN_LOG, "Step 4/4: recording history entry and showing the Yes/No notification");
            AccuracyHistoryStore.addPendingEntry(context, outcome.accuracy, outcome.kappa, outcome.top2Accuracy);
            showDecisionNotification(context, outcome.accuracy);
            Log.i(TRAIN_LOG, "===== Training run finished successfully - waiting on user decision =====");
            return Result.success();

        } catch (Exception e) {
            Log.e(TRAIN_LOG, "===== Training run threw an exception =====", e);
            Log.e(TAG, "Weekly training run failed", e);
            return Result.failure();
        } finally {
            TrainingRunLock.release(context);
        }
    }

    private static class RetrainOutcome {
        final double accuracy;
        final double kappa;
        final double top2Accuracy;
        final Map<String, String> uploadedFiles;
        RetrainOutcome(double accuracy, double kappa, double top2Accuracy, Map<String, String> uploadedFiles) {
            this.accuracy = accuracy;
            this.kappa = kappa;
            this.top2Accuracy = top2Accuracy;
            this.uploadedFiles = uploadedFiles;
        }
    }

    private String blockingUpload(DriveSyncManager driveSyncManager, Context context, File localCsv) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        driveSyncManager.uploadFile(context, localCsv, "notifications_dataset_main.csv", new DriveSyncManager.UploadCallback() {
            @Override public void onSuccess(String driveFileId) { result.set(driveFileId); latch.countDown(); }
            @Override public void onFailure(Exception e) { Log.e(TAG, "Upload failed", e); latch.countDown(); }
        });
        latch.await(3, TimeUnit.MINUTES);
        return result.get();
    }

    private RetrainOutcome blockingTriggerRetrain(CloudTrainingClient client, Context context, String csvFileId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RetrainOutcome> result = new AtomicReference<>();
        client.triggerRetrain(context, csvFileId, new CloudTrainingClient.RetrainCallback() {
            @Override
            public void onSuccess(double accuracy, double kappa, double top2Accuracy, Map<String, String> uploadedFiles) {
                result.set(new RetrainOutcome(accuracy, kappa, top2Accuracy, uploadedFiles));
                latch.countDown();
            }
            @Override public void onFailure(Exception e) { Log.e(TAG, "Retrain failed", e); latch.countDown(); }
        });
        latch.await(5, TimeUnit.MINUTES); // training genuinely takes a while server-side
        return result.get();
    }

    private boolean blockingDownload(DriveSyncManager driveSyncManager, Context context, Map<String, String> files, File destinationDir) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> success = new AtomicReference<>(false);
        driveSyncManager.downloadFiles(context, files, destinationDir, new DriveSyncManager.MultiDownloadCallback() {
            @Override public void onAllSuccess(File dir) { success.set(true); latch.countDown(); }
            @Override public void onFailure(Exception e) { Log.e(TAG, "Download failed", e); latch.countDown(); }
        });
        latch.await(3, TimeUnit.MINUTES);
        return success.get();
    }

    private void showDecisionNotification(Context context, double accuracy) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Model updates", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        Intent acceptIntent = new Intent(context, ModelDecisionReceiver.class).setAction(ACTION_ACCEPT);
        Intent rejectIntent = new Intent(context, ModelDecisionReceiver.class).setAction(ACTION_REJECT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        PendingIntent acceptPending = PendingIntent.getBroadcast(context, 1, acceptIntent, flags);
        PendingIntent rejectPending = PendingIntent.getBroadcast(context, 2, rejectIntent, flags);

        String accuracyText = String.format("%.1f%%", accuracy * 100);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.info)
                .setContentTitle("This week's model is ready")
                .setContentText("New accuracy: " + accuracyText + " - continue with this result?")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        "This week's retrained model scored " + accuracyText + " accuracy on your data. "
                                + "Continue with this result? If not, this week's update is skipped and the current model keeps running until next week."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(0, "Yes, use it", acceptPending)
                .addAction(0, "No, keep current", rejectPending)
                .setAutoCancel(true);

        nm.notify(NOTIFICATION_ID, builder.build());
    }
}

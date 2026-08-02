package com.techy.noti_filter.sync;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.techy.noti_filter.AI_Model.ModelFileSource;
import com.techy.noti_filter.service.NotificationService;

import java.io.File;

/**
 * "Yes, use it" -> promote pending_model/ to active_model/ (deleting
 * whatever was previously active), then broadcasts ACTION_RELOAD_MODEL so
 * the currently-running NotificationService swaps in the new
 * Preprocessor/NotificationPredictor immediately - a real hot-swap, not
 * just a file promotion that waits for the next restart.
 *
 * "No, keep current" -> just delete pending_model/. Nothing about the
 * active model changes; next week's WeeklyTrainingWorker run tries again.
 */
public class ModelDecisionReceiver extends BroadcastReceiver {

    private static final String TAG = "ModelDecisionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(4201);

        File pendingDir = new File(context.getFilesDir(), WeeklyTrainingWorker.PENDING_MODEL_DIR);
        File activeDir = new File(context.getFilesDir(), ModelFileSource.ACTIVE_MODEL_DIR);

        if (WeeklyTrainingWorker.ACTION_ACCEPT.equals(intent.getAction())) {
            android.util.Log.i(WeeklyTrainingWorker.TRAIN_LOG, "User tapped YES - promoting pending_model/ to active_model/");
            if (!pendingDir.isDirectory()) {
                Log.w(TAG, "Accept tapped but no pending model exists");
                android.util.Log.w(WeeklyTrainingWorker.TRAIN_LOG, "YES tapped but there's no pending model to promote");
                return;
            }
            deleteRecursive(activeDir);
            boolean moved = pendingDir.renameTo(activeDir);
            Log.d(TAG, "Promoted pending model to active: " + moved);
            android.util.Log.i(WeeklyTrainingWorker.TRAIN_LOG, "Promotion " + (moved ? "succeeded" : "FAILED") + " -> " + activeDir);

            if (moved) {
                context.sendBroadcast(new Intent(NotificationService.ACTION_RELOAD_MODEL)
                        .setPackage(context.getPackageName()));
                android.util.Log.i(WeeklyTrainingWorker.TRAIN_LOG, "Sent ACTION_RELOAD_MODEL to NotificationService");
                AccuracyHistoryStore.markLatestStatus(context, AccuracyHistoryStore.STATUS_ACCEPTED);
            }

            Toast.makeText(context,
                    moved ? "New model accepted and applied" : "Couldn't apply the new model, keeping the current one",
                    Toast.LENGTH_LONG).show();

        } else if (WeeklyTrainingWorker.ACTION_REJECT.equals(intent.getAction())) {
            android.util.Log.i(WeeklyTrainingWorker.TRAIN_LOG, "User tapped NO - discarding pending model, trying again next week");
            deleteRecursive(pendingDir);
            AccuracyHistoryStore.markLatestStatus(context, AccuracyHistoryStore.STATUS_REJECTED);
            Log.d(TAG, "Rejected this week's model, staying on current");
            Toast.makeText(context, "Kept the current model - will try again next week", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }
}

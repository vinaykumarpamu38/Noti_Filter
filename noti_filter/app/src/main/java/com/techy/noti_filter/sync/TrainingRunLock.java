package com.techy.noti_filter.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Guarantees only one WeeklyTrainingWorker execution can be "inside" the
 * actual upload/train/download flow at a time - regardless of what
 * triggered it (manual tap, scheduled run, a WorkManager retry, or
 * anything else). AccuracyHistoryStore.hasPendingEntry() alone couldn't
 * close this gap: it only detects a run that already FINISHED and is
 * awaiting a decision, not one that's still mid-flight and hasn't
 * recorded anything yet.
 */
public class TrainingRunLock {

    private static final String PREFS = "training_run_lock";
    private static final String KEY_LOCKED_AT = "locked_at_millis";

    /** A lock older than this is treated as abandoned (e.g. the app was
     * killed mid-run) rather than blocking every future run forever. */
    private static final long STALE_AFTER_MILLIS = 10 * 60 * 1000; // 10 minutes

    /** Returns true if the lock was acquired (safe to proceed), false if
     * another run already holds it. */
    public static synchronized boolean tryAcquire(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lockedAt = prefs.getLong(KEY_LOCKED_AT, 0);
        long now = System.currentTimeMillis();

        if (lockedAt != 0 && (now - lockedAt) < STALE_AFTER_MILLIS) {
            Log.w("NF_TRAIN", "TrainingRunLock: already held (locked "
                    + (now - lockedAt) + "ms ago) - refusing to start a second run");
            return false;
        }

        prefs.edit().putLong(KEY_LOCKED_AT, now).apply();
        return true;
    }

    public static synchronized void release(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_LOCKED_AT).apply();
    }
}

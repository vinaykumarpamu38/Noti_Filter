package com.techy.noti_filter.sync;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class TrainingScheduler {

    public static final String WORK_NAME = "weekly_model_training";

    /** Safe to call every time the app opens (e.g. from MainActivity.onCreate) -
     * KEEP means an already-scheduled job is left alone, so this never
     * resets the timer or creates duplicates. */
    public static void scheduleWeekly(Context context) {
        long initialDelayMillis = millisUntilNextSaturday9amIst();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WeeklyTrainingWorker.class, 7, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    private static long millisUntilNextSaturday9amIst() {
        TimeZone ist = TimeZone.getTimeZone("Asia/Kolkata"); // fixed UTC+5:30, no DST
        Calendar now = Calendar.getInstance(ist);

        Calendar target = Calendar.getInstance(ist);
        target.set(Calendar.HOUR_OF_DAY, 9);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        int currentDay = now.get(Calendar.DAY_OF_WEEK); // Calendar.SATURDAY = 7
        int daysUntilSaturday = (Calendar.SATURDAY - currentDay + 7) % 7;
        target.add(Calendar.DAY_OF_MONTH, daysUntilSaturday);

        // If today IS Saturday but 9am has already passed, push to next week
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_MONTH, 7);
        }

        return target.getTimeInMillis() - now.getTimeInMillis();
    }
}

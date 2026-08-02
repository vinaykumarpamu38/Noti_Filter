package com.techy.noti_filter.sync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Records one entry per weekly training run: when it happened, what
 * accuracy/kappa/top-2 it scored, and whether the user accepted it. Stored
 * as a JSON array in its own SharedPreferences file - no Room schema
 * changes, nothing shared with the notification database.
 */
public class AccuracyHistoryStore {

    private static final String PREFS_NAME = "accuracy_history";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 52; // about a year of weekly runs

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    public static class Entry {
        public final long timestampMillis;
        public final double accuracy;
        public final double kappa;
        public final double top2Accuracy;
        public final String status;

        public Entry(long timestampMillis, double accuracy, double kappa, double top2Accuracy, String status) {
            this.timestampMillis = timestampMillis;
            this.accuracy = accuracy;
            this.kappa = kappa;
            this.top2Accuracy = top2Accuracy;
            this.status = status;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Called right after a weekly retrain completes, before the user has
     * decided anything - status starts as "pending". */
    public static synchronized void addPendingEntry(Context context, double accuracy, double kappa, double top2Accuracy) {
        List<Entry> entries = getAll(context);
        entries.add(0, new Entry(System.currentTimeMillis(), accuracy, kappa, top2Accuracy, STATUS_PENDING));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
        save(context, entries);
    }

    /** Updates the most recent entry's status - called from
     * ModelDecisionReceiver when the user taps Yes or No. */
    /** True if the most recent training run is still awaiting a Yes/No
     * answer. Both the scheduled job and the manual test button should
     * refuse to start a new run while this is true - otherwise a second
     * run silently overwrites the first one's still-unanswered
     * notification (same notification id), and the user ends up looking
     * at a different run's results than the one they thought they saw. */
    public static synchronized boolean hasPendingEntry(Context context) {
        List<Entry> entries = getAll(context);
        return !entries.isEmpty() && STATUS_PENDING.equals(entries.get(0).status);
    }

    public static synchronized void markLatestStatus(Context context, String status) {
        List<Entry> entries = getAll(context);
        if (entries.isEmpty()) return;
        Entry latest = entries.get(0);
        entries.set(0, new Entry(latest.timestampMillis, latest.accuracy, latest.kappa, latest.top2Accuracy, status));
        save(context, entries);
    }

    /** Most recent first. */
    public static synchronized List<Entry> getAll(Context context) {
        List<Entry> result = new ArrayList<>();
        try {
            String json = prefs(context).getString(KEY_ENTRIES, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                result.add(new Entry(
                        obj.getLong("timestampMillis"),
                        obj.getDouble("accuracy"),
                        obj.getDouble("kappa"),
                        obj.getDouble("top2Accuracy"),
                        obj.getString("status")
                ));
            }
        } catch (Exception ignored) {
            // corrupt/missing prefs - just show no history rather than crash
        }
        return result;
    }

    private static void save(Context context, List<Entry> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                JSONObject obj = new JSONObject();
                obj.put("timestampMillis", e.timestampMillis);
                obj.put("accuracy", e.accuracy);
                obj.put("kappa", e.kappa);
                obj.put("top2Accuracy", e.top2Accuracy);
                obj.put("status", e.status);
                arr.put(obj);
            }
            prefs(context).edit().putString(KEY_ENTRIES, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}

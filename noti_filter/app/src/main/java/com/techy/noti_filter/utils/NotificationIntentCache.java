package com.techy.noti_filter.utils;

import android.app.PendingIntent;

import java.util.concurrent.ConcurrentHashMap;

public class NotificationIntentCache {

    // Thread-safe map
    private static final ConcurrentHashMap<String, PendingIntent> intentMap =
            new ConcurrentHashMap<>();

    // Store PendingIntent
    public static void put(String key, PendingIntent pendingIntent) {
        if (key != null && pendingIntent != null) {
            intentMap.put(key, pendingIntent);
        }
    }

    // Retrieve PendingIntent
    public static PendingIntent get(String key) {
        if (key == null) return null;
        return intentMap.get(key);
    }

    // Remove (cleanup)
    public static void remove(String key) {
        if (key != null) {
            intentMap.remove(key);
        }
    }

    // Optional: clear all (debug / reset)
    public static void clear() {
        intentMap.clear();
    }
}
package com.techy.noti_filter.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.techy.noti_filter.R;

public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "logger_channel";

    static {
        Log.d("KeepAliveService", "Class loaded");
    }

    public KeepAliveService() {
        Log.d("KeepAliveService", "Constructor called");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("KeepAliveService", "onCreate started");

        createNotificationChannel();

        Log.d("KeepAliveService", "channel created");

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Notification Logger Running")
                .setContentText("Collecting Notification Data")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        Log.d("KeepAliveService", "notification built");

        startForeground(1, notification);

        Log.d("KeepAliveService", "startForeground called");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("KeepAliveService", "onStartCommand");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Logger Service",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            manager.createNotificationChannel(channel);
        }
    }
}
package com.techy.noti_filter.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
            Intent serviceIntent= new Intent(context, KeepAliveService.class);
            ContextCompat.startForegroundService(context,serviceIntent);
        }
    }
}

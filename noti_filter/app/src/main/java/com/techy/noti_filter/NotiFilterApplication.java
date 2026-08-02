package com.techy.noti_filter;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/**
 * Registered in AndroidManifest.xml as android:name=".NotiFilterApplication".
 *
 * Uses the Debug App Check provider for debug builds (works immediately,
 * no Play Console setup needed - just requires adding the debug token it
 * prints to Logcat as a "debug token" in the Firebase console once) and
 * Play Integrity for release builds (the real production attestation,
 * requires the app to be signed with its real release key and ideally
 * already have some presence in Play Console).
 */
public class NotiFilterApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Log.e("DEBUG_CHECK", "BuildConfig.DEBUG = " + BuildConfig.DEBUG);
        FirebaseApp.initializeApp(this);

        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance());
        }
    }
}

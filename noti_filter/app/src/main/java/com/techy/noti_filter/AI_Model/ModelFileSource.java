package com.techy.noti_filter.AI_Model;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Single source of truth for "where does the currently-active model live."
 *
 * assets/ is baked into the APK and is read-only at runtime - a freshly
 * downloaded model can never be written there. So the active model is
 * either:
 *   - filesDir/active_model/<filename>, if the user has accepted a
 *     retrained model (see ModelDecisionReceiver), or
 *   - the bundled default in assets/, otherwise.
 *
 * Both Preprocessor and NotificationPredictor read through this class so
 * they can never disagree about which generation of the model is active.
 */
public class ModelFileSource {

    public static final String ACTIVE_MODEL_DIR = "active_model";

    private final Context context;
    private final File activeDir;

    public ModelFileSource(Context context) {
        this.context = context.getApplicationContext();
        this.activeDir = new File(this.context.getFilesDir(), ACTIVE_MODEL_DIR);
    }

    /** True if a downloaded/accepted model is currently active (as opposed
     * to still running the bundled default from assets/). */
    public boolean isUsingDownloadedModel() {
        return activeDir.isDirectory() && new File(activeDir, "notification_model.tflite").exists();
    }

    public InputStream open(String filename) throws IOException {
        File local = new File(activeDir, filename);
        if (local.exists()) {
            return new FileInputStream(local);
        }
        return context.getAssets().open(filename);
    }

    /** For the .tflite file specifically - NotificationPredictor needs a
     * File (or something mmap-able), not just an InputStream, and assets
     * vs internal-storage files are memory-mapped differently. */
    public boolean hasLocalFile(String filename) {
        return new File(activeDir, filename).exists();
    }

    public File localFile(String filename) {
        return new File(activeDir, filename);
    }

    public Context getContext() {
        return context;
    }
}

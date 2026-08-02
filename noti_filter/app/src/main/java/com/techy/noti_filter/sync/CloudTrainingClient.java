package com.techy.noti_filter.sync;

import android.content.Context;
import android.util.Log;

import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.AppCheckToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Calls the Phase 5 Cloud Function to trigger a real retrain.
 *
 * Two independent tokens travel with every request, serving two different
 * purposes - don't confuse them:
 *   - X-Firebase-AppCheck header: proves this request comes from a genuine,
 *     unmodified build of this app (app attestation via Play Integrity/Debug
 *     provider - see NotiFilterApplication).
 *   - "access_token" in the JSON body: the user's own Drive OAuth token,
 *     proving THIS user authorized Drive access (see DriveSyncManager).
 * The backend needs both: App Check to reject non-app traffic before doing
 * any work, the Drive token to actually read/write that user's Drive files.
 */
public class CloudTrainingClient {

    private static final String TAG = "CloudTrainingClient";

    // Specific to this project's deployed Cloud Function - update if the
    // function is ever redeployed to a different project/region/name.
    private static final String RETRAIN_URL =
            "https://us-central1-notifilter-503609.cloudfunctions.net/retrain";

    private final DriveSyncManager driveSyncManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface RetrainCallback {
        /** uploadedFiles maps filename -> Drive file id for every artifact
         * the Cloud Function just wrote back (notification_model.tflite,
         * feature_spec.json, etc.) - pass these to
         * DriveSyncManager.downloadFiles() to actually pull the retrained
         * model down into the app. */
        void onSuccess(double accuracy, double quadraticWeightedKappa, double top2Accuracy, Map<String, String> uploadedFiles);
        void onFailure(Exception e);
    }

    public CloudTrainingClient(DriveSyncManager driveSyncManager) {
        this.driveSyncManager = driveSyncManager;
    }

    /** csvFileId is the Drive file id returned by DriveSyncManager.uploadFile()'s
     * onSuccess callback - call this right after a successful CSV upload. */
    public void triggerRetrain(Context context, String csvFileId, RetrainCallback callback) {
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
                .addOnSuccessListener(appCheckTokenResult -> {
                    String appCheckToken = appCheckTokenResult.getToken();
                    driveSyncManager.getAccessTokenAsync(context, new DriveSyncManager.TokenCallback() {
                        @Override
                        public void onSuccess(String driveAccessToken) {
                            executor.execute(() -> doRequest(appCheckToken, driveAccessToken, csvFileId, callback));
                        }

                        @Override
                        public void onFailure(Exception e) {
                            callback.onFailure(e);
                        }
                    });
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void doRequest(String appCheckToken, String driveAccessToken, String csvFileId, RetrainCallback callback) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("access_token", driveAccessToken);
            body.put("csv_file_id", csvFileId);

            URL url = new URL(RETRAIN_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-Firebase-AppCheck", appCheckToken);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(310_000); // training genuinely takes a while - don't time out early
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            InputStream responseStream = (status >= 200 && status < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String responseBody = readStream(responseStream);
            Log.d(TAG, "Retrain response (" + status + "): " + responseBody);

            JSONObject json = new JSONObject(responseBody);
            if (status == 200 && "ok".equals(json.optString("status"))) {
                Map<String, String> uploadedFiles = new HashMap<>();
                JSONObject filesJson = json.optJSONObject("uploaded_files");
                if (filesJson != null) {
                    java.util.Iterator<String> keys = filesJson.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        uploadedFiles.put(key, filesJson.optString(key));
                    }
                }
                callback.onSuccess(
                        json.getDouble("accuracy"),
                        json.getDouble("quadratic_weighted_kappa"),
                        json.getDouble("top_2_accuracy"),
                        uploadedFiles
                );
            } else {
                callback.onFailure(new IOException("Retrain failed (" + status + "): " + json.optString("message", responseBody)));
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Retrain request failed", e);
            callback.onFailure(e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}

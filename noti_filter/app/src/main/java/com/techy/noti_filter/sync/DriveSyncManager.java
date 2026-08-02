package com.techy.noti_filter.sync;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phase 4 - Drive sync.
 *
 * Uses ONLY the drive.file scope: this app can only see/create/update files
 * it created itself through this API, never anything else in the user's
 * Drive. That keeps this in Google's "sensitive" OAuth tier rather than
 * "restricted" - restricted scopes (broad Drive access) require an annual
 * third-party security assessment (CASA) if the data ever touches a server,
 * which the Phase 5 training backend will. drive.file avoids that entirely.
 *
 * REQUIRED MANUAL SETUP (I can't do this part from here - it needs your own
 * Google Cloud Console account):
 *   1. Create a Google Cloud project (console.cloud.google.com).
 *   2. Enable the "Google Drive API" for that project.
 *   3. Configure the OAuth consent screen (app name, your support email,
 *      the drive.file scope, privacy policy URL once you have one).
 *   4. Create an OAuth 2.0 Client ID of type "Android": you'll need the
 *      app's package name (com.techy.noti_filter) and its signing
 *      certificate SHA-1 fingerprint (get it via
 *      `./gradlew signingReport` in the project root, or from Android
 *      Studio's Gradle panel under Tasks > android > signingReport).
 *   5. No client secret is needed for Android OAuth clients - they're
 *      "public clients," the SHA-1 + package name pairing is what
 *      authorizes the app.
 * Until that's done, sign-in will fail with a developer-facing error, not
 * a crash - this class doesn't need any code changes once it's done.
 */
public class DriveSyncManager {

    private static final String TAG = "DriveSyncManager";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface UploadCallback {
        void onSuccess(String driveFileId);
        void onFailure(Exception e);
    }

    public interface TokenCallback {
        void onSuccess(String accessToken);
        void onFailure(Exception e);
    }

    /** Returns a plain OAuth access token string - needed to call the Phase 5
     * Cloud Function's "access_token" field. Runs on a background thread
     * (credential.getToken() is a blocking network call). Tokens are
     * short-lived (about an hour), so fetch a fresh one right before use
     * rather than caching it long-term. */
    public void getAccessTokenAsync(Context context, TokenCallback callback) {
        executor.execute(() -> {
            try {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
                if (account == null || account.getEmail() == null) {
                    callback.onFailure(new IllegalStateException("Not signed in to Google Drive"));
                    return;
                }
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, Collections.singleton(DriveScopes.DRIVE_FILE));
                credential.setSelectedAccount(new Account(account.getEmail(), "com.google"));
                String token = credential.getToken();
                callback.onSuccess(token);
            } catch (Exception e) {
                callback.onFailure(e);
            }
        });
    }

    public GoogleSignInClient getSignInClient(Context context) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        return GoogleSignIn.getClient(context, options);
    }

    public Intent getSignInIntent(Context context) {
        return getSignInClient(context).getSignInIntent();
    }

    public boolean isSignedIn(Context context) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account != null && GoogleSignIn.hasPermissions(account, new Scope(DriveScopes.DRIVE_FILE));
    }

    /** Clears the cached sign-in. Needed once after changing the requested
     * scopes/options (like adding requestEmail()) - a device that already
     * signed in under the OLD options will keep reporting isSignedIn()=true
     * using the stale cached account, which won't have the new field
     * populated, until this is called and the user signs in again. */
    public void signOut(Context context, Runnable onComplete) {
        getSignInClient(context).signOut().addOnCompleteListener(task -> onComplete.run());
    }

    private static final String DEFAULT_FOLDER_NAME = "Noti Filter";

    /**
     * Uploads (or updates, if a file with this name already exists in the
     * app's own Drive space) the given local file, inside a dedicated
     * folder (created once, reused after that) rather than dumping it at
     * the Drive root. Runs entirely on a background thread - Drive calls
     * will throw NetworkOnMainThreadException otherwise. Callback is
     * invoked on that same background thread; hop back to the main thread
     * yourself if you're touching views in it.
     */
    public void uploadFile(Context context, java.io.File localFile, String driveFileName, UploadCallback callback) {
        uploadFile(context, localFile, driveFileName, DEFAULT_FOLDER_NAME, callback);
    }

    public void uploadFile(Context context, java.io.File localFile, String driveFileName, String folderName, UploadCallback callback) {
        executor.execute(() -> {
            try {
                Drive driveService = buildDriveService(context);

                String folderId = findOrCreateFolder(driveService, folderName);
                String existingFileId = findExistingFileId(driveService, driveFileName, folderId);
                FileContent mediaContent = new FileContent("text/csv", localFile);

                if (existingFileId != null) {
                    File updated = driveService.files().update(existingFileId, null, mediaContent).execute();
                    Log.d(TAG, "Updated existing Drive file: " + updated.getId());
                    callback.onSuccess(updated.getId());
                } else {
                    File metadata = new File();
                    metadata.setName(driveFileName);
                    metadata.setParents(Collections.singletonList(folderId));
                    File created = driveService.files().create(metadata, mediaContent)
                            .setFields("id")
                            .execute();
                    Log.d(TAG, "Created new Drive file: " + created.getId() + " in folder " + folderId);
                    callback.onSuccess(created.getId());
                }
            } catch (Exception e) {
                Log.e(TAG, "Drive upload failed", e);
                callback.onFailure(e);
            }
        });
    }

    /** Shared credential/service setup used by both upload and download -
     * throws rather than reporting failure via callback, since callers are
     * already inside a try/catch that routes to their own callback. */
    private Drive buildDriveService(Context context) throws Exception {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account == null || account.getEmail() == null) {
            throw new IllegalStateException("Not signed in to Google Drive");
        }

        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(new Account(account.getEmail(), "com.google"));

        return new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Noti Filter")
                .build();
    }

    public interface DownloadCallback {
        void onSuccess(java.io.File downloadedFile);
        void onFailure(Exception e);
    }

    public interface MultiDownloadCallback {
        void onAllSuccess(java.io.File destinationDir);
        void onFailure(Exception e);
    }

    /** Downloads a single Drive file (by its file id, not by name - use the
     * ids returned in the Cloud Function's "uploaded_files" response) into
     * destination. destination's parent directory must already exist and
     * be writable - use context.getFilesDir() or a subdirectory of it,
     * NEVER the assets/ folder, which is read-only at runtime. */
    public void downloadFile(Context context, String driveFileId, java.io.File destination, DownloadCallback callback) {
        executor.execute(() -> {
            try {
                Drive driveService = buildDriveService(context);
                try (OutputStream out = new FileOutputStream(destination)) {
                    driveService.files().get(driveFileId).executeMediaAndDownloadTo(out);
                }
                callback.onSuccess(destination);
            } catch (Exception e) {
                Log.e(TAG, "Drive download failed for file " + driveFileId, e);
                callback.onFailure(e);
            }
        });
    }

    /** Downloads every file in fileNameToId (as returned by the Cloud
     * Function's "uploaded_files" map) into destinationDir, one at a time,
     * each keeping its original filename. Stops and reports failure on the
     * first error - a half-downloaded model set is worse than none, since
     * whatever reload logic consumes this directory shouldn't have to
     * guess whether it's complete. */
    public void downloadFiles(Context context, Map<String, String> fileNameToId, java.io.File destinationDir, MultiDownloadCallback callback) {
        executor.execute(() -> {
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                callback.onFailure(new IOException("Could not create " + destinationDir));
                return;
            }
            try {
                Drive driveService = buildDriveService(context);
                for (Map.Entry<String, String> entry : fileNameToId.entrySet()) {
                    java.io.File dest = new java.io.File(destinationDir, entry.getKey());
                    try (OutputStream out = new FileOutputStream(dest)) {
                        driveService.files().get(entry.getValue()).executeMediaAndDownloadTo(out);
                    }
                    Log.d(TAG, "Downloaded " + entry.getKey() + " -> " + dest);
                }
                callback.onAllSuccess(destinationDir);
            } catch (Exception e) {
                Log.e(TAG, "Batch model download failed", e);
                callback.onFailure(e);
            }
        });
    }

    /** Finds the app's dedicated folder by name, creating it once if it
     * doesn't exist yet. Subsequent calls (and subsequent app runs) reuse
     * the same folder instead of creating a new one each time. */
    private String findOrCreateFolder(Drive driveService, String folderName) throws IOException {
        FileList result = driveService.files().list()
                .setQ("mimeType='application/vnd.google-apps.folder' and name='"
                        + folderName.replace("'", "\\'") + "' and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();
        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        File folder = driveService.files().create(folderMetadata).setFields("id").execute();
        Log.d(TAG, "Created Drive folder: " + folder.getId());
        return folder.getId();
    }

    /** Looks for a file with this exact name inside the given folder, that
     * THIS app previously created (drive.file scope means the query is
     * already implicitly limited to files the app can see). */
    private String findExistingFileId(Drive driveService, String fileName, String folderId) throws IOException {
        FileList result = driveService.files().list()
                .setQ("name='" + fileName.replace("'", "\\'") + "' and '"
                        + folderId + "' in parents and trashed=false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();
        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }
        return null;
    }
}

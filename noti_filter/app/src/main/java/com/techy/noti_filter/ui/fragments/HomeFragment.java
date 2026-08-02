package com.techy.noti_filter.ui.fragments;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Task;
import com.techy.noti_filter.R;
import com.techy.noti_filter.databinding.FragmentHomeBinding;
import com.techy.noti_filter.service.KeepAliveService;
import com.techy.noti_filter.sync.DriveSyncManager;
import com.techy.noti_filter.ui.activities.MainActivity;
import com.techy.noti_filter.ui.activities.NotificationList;

import java.io.File;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private final DriveSyncManager driveSyncManager = new DriveSyncManager();

    private static final int CONTACT_PERMISSION = 100;
    private static final int NOTIFICATION_PERMISSION = 101;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    task.getResult(com.google.android.gms.common.api.ApiException.class);
                    updateDriveStatusText();
                    Toast.makeText(requireContext(), "Signed in to Drive", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("HomeFragment", "Drive sign-in failed", e);
                    Toast.makeText(requireContext(), "Drive sign-in failed", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION);
        }

        binding.export.setOnClickListener(v-> exportCSV());

        binding.buttonPermission.setOnClickListener(v->{
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_CONTACTS},
                    CONTACT_PERMISSION);
        });

        binding.buttonStart.setOnClickListener(v->{
//            Intent serviceIntent= new Intent(this, KeepAliveService.class);
//            ContextCompat.startForegroundService(this,serviceIntent);

            try {
                Intent intent = new Intent(requireContext(), KeepAliveService.class);
                ContextCompat.startForegroundService(requireContext(), intent);
                Log.d("MAIN", "Foreground service start requested");
            } catch (Exception e) {
                Log.e("MAIN", "Failed to start service", e);
            }
        });

        binding.AIExport.setOnClickListener(v->{
            exportAI_CSV();
        });

        binding.syncDrive.setOnClickListener(v -> onSyncDriveClicked());
        updateDriveStatusText();

        binding.bottomNavigation.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_dashboard) {
                if (!getClass().equals(MainActivity.class)) {
                    startActivity(new Intent(requireContext(), MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                }

            } else if (id == R.id.nav_all_notifications) {
                if (!getClass().equals(NotificationList.class)) {
                    startActivity(new Intent(requireContext(), NotificationList.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                }

            } else {
                return false;
            }

            return true;
        });

        return binding.getRoot();
    }

    private void onSyncDriveClicked() {
        if (driveSyncManager.isSignedIn(requireContext())) {
            Toast.makeText(requireContext(), "Drive is already connected", Toast.LENGTH_SHORT).show();
            return;
        }
        signInLauncher.launch(driveSyncManager.getSignInIntent(requireContext()));
    }

    private void updateDriveStatusText() {
        if (binding == null) return;
        boolean signedIn = driveSyncManager.isSignedIn(requireContext());
        binding.syncDriveSubtitle.setText(signedIn
                ? R.string.action_sync_drive_subtitle_signed_in
                : R.string.action_sync_drive_subtitle_signed_out);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void exportAI_CSV() {

        // TO DOWNLOAD DIRECTLY
//        File dest= new File(Environment.getExternalStoragePublicDirectory(
//                Environment.DIRECTORY_DOWNLOADS),"notifications_dataset.csv"
//        );
        // TO SHARE
        File file = new File(requireContext().getExternalFilesDir(null),"predictions_28Jul.csv");
        if (!file.exists()){
            Toast.makeText(requireContext(), "No data found", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri= FileProvider.getUriForFile(
                requireContext(),
                "com.techy.noti_filter.provider",
                file
        );
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM,uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent,"Download / Share CSV"));
    }

    private void exportCSV() {

        // TO DOWNLOAD DIRECTLY
//        File dest= new File(Environment.getExternalStoragePublicDirectory(
//                Environment.DIRECTORY_DOWNLOADS),"notifications_dataset.csv"
//        );
        // TO SHARE
        File file = new File(requireContext().getExternalFilesDir(null),"notifications_dataset_28Jul.csv");
        if (!file.exists()){
            Toast.makeText(requireContext(), "No data found", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri= FileProvider.getUriForFile(
                requireContext(),
                "com.techy.noti_filter.provider",
                file
        );
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM,uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent,"Download / Share CSV"));
    }


}
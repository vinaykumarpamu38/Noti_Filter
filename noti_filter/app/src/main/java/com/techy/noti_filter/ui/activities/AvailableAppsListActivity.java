package com.techy.noti_filter.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.techy.noti_filter.Adapters.AppListAdapter;
import com.techy.noti_filter.databinding.ActivityAvailableAppsListBinding;
import com.techy.noti_filter.dao.AppInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AvailableAppsListActivity extends AppCompatActivity {

    private ActivityAvailableAppsListBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAvailableAppsListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.SelectApps, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());


        // Load the apps data and populate the RecyclerView
        PackageManager pm = getPackageManager();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);

        List<AppInfo> installedApps = new ArrayList<>();

        for (ResolveInfo app : apps) {

            String packageName = app.activityInfo.packageName;
            String appName = app.loadLabel(pm).toString();
            Drawable icon = app.loadIcon(pm);

            installedApps.add(new AppInfo(appName, packageName, icon));
        }

        SharedPreferences prefs =
                getSharedPreferences("MyPrefs", MODE_PRIVATE);

        Set<String> selectedApps =
                prefs.getStringSet("selected_apps", new HashSet<>());

        for (AppInfo app : installedApps) {
            app.setSelected(selectedApps.contains(app.getPackageName()));
        }


        Collections.sort(installedApps,
                (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));

        Log.d("Apps", "Total apps = " + installedApps.size());

        binding.recyclerViewApps.setLayoutManager(
                new LinearLayoutManager(this)
        );
        AppListAdapter adapter = new AppListAdapter(installedApps);
        binding.recyclerViewApps.setAdapter(adapter);


        binding.saveSelectedApps.setOnClickListener(v->{
            Set<String> selectedAppsList = new HashSet<>();

            for (AppInfo app : adapter.getInstalledApps()) {

                if (app.isSelected()) {
                    selectedAppsList.add(app.getPackageName());
                }
            }

            SharedPreferences prefs_save =
                    getSharedPreferences("MyPrefs", MODE_PRIVATE);

            prefs_save.edit()
                    .putStringSet("selected_apps", selectedAppsList)
                    .apply();

            Toast.makeText(this, "Saved Successfully", Toast.LENGTH_SHORT).show();
        });



    }
}
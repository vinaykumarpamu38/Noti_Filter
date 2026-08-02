package com.techy.noti_filter.ui.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.techy.noti_filter.R;
import com.techy.noti_filter.databinding.ActivityMainBinding;
import com.techy.noti_filter.service.KeepAliveService;
import com.techy.noti_filter.sync.TrainingScheduler;
import com.techy.noti_filter.ui.fragments.HomeFragment;
import com.techy.noti_filter.ui.fragments.ProfileFragment;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private DrawerLayout drawerLayout;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TrainingScheduler.scheduleWeekly(getApplicationContext());
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EdgeToEdge.enable(this);


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);

        View mainContent = findViewById(R.id.main_content);
        ViewCompat.setOnApplyWindowInsetsListener(mainContent, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Toggle for the navigation drawer
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setCheckedItem(R.id.nav_home);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.frame_layout, new HomeFragment())
                .commit();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_home);
        }
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
//                    fab.setVisibility(View.VISIBLE);
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.frame_layout, new HomeFragment())
                            .commit();
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.title_home);
                    }
                } else if (id == R.id.nav_settings) {
//                    fab.setVisibility(View.GONE);
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.frame_layout, new ProfileFragment())
                            .commit();
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(R.string.title_settings);
                    }
                } else if (id == R.id.nav_logout) {
//                    startActivity(new Intent(MainActivity.this, AuthenticationActivity.class));
                } else if (id == R.id.nav_select_apps) {
                    startActivity(new Intent(MainActivity.this, AvailableAppsListActivity.class));
                }

                drawerLayout.closeDrawer(GravityCompat.START);
                return true;

            }
        });

    }
}
package com.techy.noti_filter.ui.activities;

import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.techy.noti_filter.Adapters.NotificationAdapter;
import com.techy.noti_filter.dao.NotificationDao;
import com.techy.noti_filter.dao.NotificationDataMapper;
import com.techy.noti_filter.databinding.ActivityNotificationListBinding;
import com.techy.noti_filter.db.AppDatabase;
import com.techy.noti_filter.db.NotificationEntity;
import com.techy.noti_filter.model.NotificationData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationList extends AppCompatActivity {

    private ActivityNotificationListBinding binding;
    private AppDatabase db;
    private NotificationDao dao;
    private ExecutorService executor;


    Integer selectedPriority = null;
    Long startTime = null;
    Long endTime = null;
    String selectedLabel = null;
    List<NotificationData> fullList = new ArrayList<>();

    NotificationAdapter adapter;

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityNotificationListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EdgeToEdge.enable(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        db = AppDatabase.getInstance(getApplicationContext());
        dao = db.notificationDao();

        executor = Executors.newSingleThreadExecutor();
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));

        executor.execute(() -> {
            List<NotificationEntity> entities = dao.getAll();
            fullList = entities.stream()
                    .map(NotificationDataMapper::toData)
                    .toList();


            runOnUiThread(() -> {
                adapter = new NotificationAdapter(fullList);
                binding.recyclerView.setAdapter(adapter);
                updateEmptyState(fullList);
            });
        });

        // TODAY
        binding.chipToday.setOnCheckedChangeListener((chip, isChecked) -> {
            if (isChecked) {
                Calendar cal = Calendar.getInstance();

                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                startTime = cal.getTimeInMillis();

                cal.add(Calendar.DAY_OF_MONTH, 1);
                endTime = cal.getTimeInMillis();
            } else {
                startTime = null;
                endTime = null;
            }

            applyFilters();
        });

// YESTERDAY
        binding.chipYesterday.setOnCheckedChangeListener((chip, isChecked) -> {
            if (isChecked) {

                Calendar cal = Calendar.getInstance();

                // Go to yesterday
                cal.add(Calendar.DAY_OF_MONTH, -1);

                // START of yesterday
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0); // ✅ IMPORTANT

                startTime = cal.getTimeInMillis();

                // END of yesterday (start of today)
                cal.add(Calendar.DAY_OF_MONTH, 1);
                endTime = cal.getTimeInMillis();

            } else {
                startTime = null;
                endTime = null;
            }

            applyFilters();
        });

// HIGH PRIORITY
        binding.chipHigh.setOnCheckedChangeListener((chip, isChecked) -> {
            selectedPriority = isChecked ? 2 : null;
            applyFilters();
        });

// IGNORED
        binding.chipIgnored.setOnCheckedChangeListener((chip, isChecked) -> {
            selectedLabel = isChecked ? "0" : null;
            applyFilters();
        });

// IMPORTANT
        binding.chipImportant.setOnCheckedChangeListener((chip, isChecked) -> {
            selectedLabel = isChecked ? "3" : null;
            applyFilters();
        });

        binding.chipClear.setOnClickListener(v -> clearAllFilters());


    }

    private void clearAllFilters() {

        binding.chipGroup.clearCheck();

        selectedPriority = null;
        selectedLabel = null;
        startTime = null;
        endTime = null;

        adapter.updateList(fullList);
        updateEmptyState(fullList);
    }
    private void applyFilters() {

        if (adapter == null) return;
        List<NotificationData> filtered = new ArrayList<>();

        for (NotificationData item : fullList) {

            boolean match = true;

            if (selectedPriority != null) {
                match &= (item.priority == selectedPriority);
            }

            if (selectedLabel != null) {
                match &= selectedLabel.equalsIgnoreCase(item.label+"");
            }

            if (startTime != null && endTime != null) {
                match &= (item.postTime >= startTime && item.postTime < endTime);
            }

            if (match) {
                filtered.add(item);
            }
        }

        adapter.updateList(filtered);
        updateEmptyState(filtered);
    }

    private void updateEmptyState(List<NotificationData> visibleList) {
        boolean empty = visibleList == null || visibleList.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

}
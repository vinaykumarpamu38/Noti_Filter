package com.techy.noti_filter.ui.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.techy.noti_filter.AI_Model.ModelFileSource;
import com.techy.noti_filter.R;
import com.techy.noti_filter.sync.AccuracyHistoryStore;
import com.techy.noti_filter.sync.WeeklyTrainingWorker;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AccuracyHistoryActivity extends AppCompatActivity {

    private LinearLayout historyContainer;
    private View emptyState;
    private TextView modelStatusText;
    private TextView trainingStatusText;
    private MaterialButton runTrainingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_accuracy_history);

        View main = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(main, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbar))
                .setNavigationOnClickListener(v -> finish());

        historyContainer = findViewById(R.id.history_container);
        emptyState = findViewById(R.id.empty_state);
        modelStatusText = findViewById(R.id.model_status_text);
        trainingStatusText = findViewById(R.id.training_status_text);
        runTrainingButton = findViewById(R.id.run_training_button);

        updateModelStatusText();
        populateHistory();

        runTrainingButton.setOnClickListener(v -> runTrainingNow());

        // If a manual run is already in flight (e.g. screen was rotated or
        // reopened while it was running), reattach to it instead of losing
        // the live status.
        observeWork();
    }

    /** Deliberately separate from TrainingScheduler.WORK_NAME - that name
     * always has the Saturday job sitting in it (re-enqueued with KEEP on
     * every app launch), so sharing it here meant this button's request
     * always lost to the already-enqueued weekly job and just sat
     * watching it wait for Saturday. This name is only ever used for
     * manual test runs, and still protects against a second tap
     * overlapping a manual run already in progress. */
    private static final String MANUAL_TEST_WORK_NAME = "manual_test_training";

    private void runTrainingNow() {
        if (AccuracyHistoryStore.hasPendingEntry(getApplicationContext())) {
            trainingStatusText.setVisibility(View.VISIBLE);
            trainingStatusText.setText("A previous run is still awaiting your Yes/No - respond to that notification first");
            return;
        }

        runTrainingButton.setEnabled(false);
        trainingStatusText.setVisibility(View.VISIBLE);
        trainingStatusText.setText("Queued - waiting for the system to start it...");

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WeeklyTrainingWorker.class).build();
        // MANUAL_TEST_WORK_NAME is its own separate name (not shared with the
        // Saturday schedule) - KEEP still stops a second manual tap from
        // overlapping a manual run already in progress.
        WorkManager.getInstance(getApplicationContext())
                .enqueueUniqueWork(MANUAL_TEST_WORK_NAME, ExistingWorkPolicy.KEEP, request);
        observeWork();
    }

    /** Reattaches to whatever is running under the shared unique work name -
     * whether that's a manual test run or the Saturday schedule having
     * fired - so this always reflects the true, single, in-flight run. */
    private void observeWork() {
        WorkManager.getInstance(getApplicationContext())
                .getWorkInfosForUniqueWorkLiveData(MANUAL_TEST_WORK_NAME)
                .observe(this, infos -> {
                    if (infos == null || infos.isEmpty()) return;
                    WorkInfo latest = infos.get(infos.size() - 1);
                    updateStatusFor(latest);
                });
    }

    private void updateStatusFor(WorkInfo info) {
        if (info == null) return;
        switch (info.getState()) {
            case ENQUEUED:
                runTrainingButton.setEnabled(false);
                trainingStatusText.setVisibility(View.VISIBLE);
                trainingStatusText.setText("Queued - waiting for the system to start it...");
                break;
            case RUNNING:
                runTrainingButton.setEnabled(false);
                trainingStatusText.setVisibility(View.VISIBLE);
                trainingStatusText.setText("Running - check Logcat for \"NF_TRAIN\" for step-by-step progress");
                break;
            case SUCCEEDED:
                trainingStatusText.setText("Done - check your notification shade for the Yes/No prompt");
                runTrainingButton.setEnabled(true);
                updateModelStatusText();
                populateHistory();
                break;
            case FAILED:
                trainingStatusText.setText("Failed - check Logcat for \"NF_TRAIN\" to see which step failed");
                runTrainingButton.setEnabled(true);
                break;
            case CANCELLED:
                trainingStatusText.setText("Cancelled");
                runTrainingButton.setEnabled(true);
                break;
            default:
                break;
        }
    }

    private void updateModelStatusText() {
        ModelFileSource fileSource = new ModelFileSource(getApplicationContext());
        boolean usingDownloaded = fileSource.isUsingDownloadedModel();

        if (!usingDownloaded) {
            modelStatusText.setText("Bundled default model (no accepted retrain yet)");
            return;
        }

        SharedPreferences status = getSharedPreferences("model_status", MODE_PRIVATE);
        long lastReload = status.getLong("last_reload_millis", -1);
        int dim = status.getInt("active_input_dim", -1);

        String when = lastReload > 0
                ? new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(lastReload)
                : "unknown time (files present but no reload recorded - restart the app once)";

        modelStatusText.setText("Downloaded model, active since " + when
                + (dim > 0 ? " (input dim " + dim + ")" : ""));
    }

    private void populateHistory() {
        historyContainer.removeAllViews();
        List<AccuracyHistoryStore.Entry> entries = AccuracyHistoryStore.getAll(getApplicationContext());

        if (entries.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }
        emptyState.setVisibility(View.GONE);

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault());
        LayoutInflater inflater = LayoutInflater.from(this);

        for (AccuracyHistoryStore.Entry entry : entries) {
            View row = inflater.inflate(R.layout.item_accuracy_history, historyContainer, false);

            TextView dateView = row.findViewById(R.id.entry_date);
            TextView detailView = row.findViewById(R.id.entry_detail);
            TextView accuracyView = row.findViewById(R.id.entry_accuracy);
            TextView statusView = row.findViewById(R.id.entry_status);

            dateView.setText(dateFormat.format(entry.timestampMillis));
            detailView.setText(String.format(Locale.getDefault(),
                    "Kappa %.2f · Top-2 %.1f%%", entry.kappa, entry.top2Accuracy * 100));
            accuracyView.setText(String.format(Locale.getDefault(), "%.1f%%", entry.accuracy * 100));

            switch (entry.status) {
                case AccuracyHistoryStore.STATUS_ACCEPTED:
                    statusView.setText("Accepted");
                    statusView.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
                    break;
                case AccuracyHistoryStore.STATUS_REJECTED:
                    statusView.setText("Rejected");
                    statusView.setTextColor(ContextCompat.getColor(this, R.color.colorError));
                    break;
                default:
                    statusView.setText("Pending");
                    statusView.setTextColor(ContextCompat.getColor(this, R.color.colorWarning));
                    break;
            }

            historyContainer.addView(row);
        }
    }
}

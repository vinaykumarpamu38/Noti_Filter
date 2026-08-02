package com.techy.noti_filter.ui.activities;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.techy.noti_filter.R;
import com.techy.noti_filter.databinding.ActivityNotificationDetailBinding;
import com.techy.noti_filter.model.NotificationData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class NotificationDetail extends AppCompatActivity {

    private ActivityNotificationDetailBinding binding;

    private String notificationKey,packageName;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityNotificationDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        notificationKey = getIntent().getStringExtra("key");

        NotificationData data = (NotificationData) getIntent().getSerializableExtra("data");

        if (data != null) {
            packageName=data.packageName;
            if (data.appIcon != null && data.appIcon.length > 0) {
                Bitmap bitmap = byteArrayToBitmap(data.appIcon);
                if (bitmap != null) {
                    binding.appIconDetail.setImageBitmap(bitmap);
                } else {
                    binding.appIconDetail.setImageResource(R.drawable.ic_app_placeholder);
                }
            } else {
                binding.appIconDetail.setImageResource(R.drawable.ic_app_placeholder);
            }
            binding.title.setText(data.title);
            Log.d("DATA_DEBUG", "Title: " + data.title);
            binding.body.setText(data.body);
            Log.d("DATA_DEBUG", "body: " + data.title);
            binding.app.setText(orDash(data.app));
            binding.label.setText(String.valueOf(data.label));
            binding.postTime.setText(convertTime(data.postTime));
            binding.sender.setText(orDash(data.sender));
            binding.category.setText(orDash(data.category));
            binding.priority.setText(String.valueOf(data.priority));
            binding.actionTaken.setText(orDash(data.actionTaken));
            binding.hour.setText(String.valueOf(data.hour));
            binding.day.setText(String.valueOf(data.day));
            binding.type.setText(orDash(data.type));
            binding.timeToInteract.setText(convertTime(data.timeToInteract+data.postTime));

            // bind more fields if needed
        }


        binding.openNotification.setOnClickListener(view -> {
            openNotification();
        });




    }

    /** Presentational helper only — shows a placeholder dash instead of the
     * literal word "null" when a field wasn't collected; doesn't change what
     * data is stored or how it's computed. */
    private String orDash(String value) {
        return (value == null || value.trim().isEmpty()) ? getString(R.string.value_placeholder) : value;
    }
    public Bitmap byteArrayToBitmap(byte[] byteArray) {
        try {
            if (byteArray == null || byteArray.length == 0) {
                return null;
            }

            return BitmapFactory.decodeByteArray(
                    byteArray,
                    0,
                    byteArray.length
            );
        } catch (Exception e) {
            Log.e("BitmapConversion", "Failed to convert byte array to bitmap", e);
            return null;
        }
    }


    private void openNotification() {

        boolean opened = false;

        // ✅ Try PendingIntent first
        try {
            PendingIntent pi = com.techy.noti_filter.utils.NotificationIntentCache
                    .get(notificationKey);

            if (pi != null) {
                pi.send();
                opened = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // ✅ Fallback → open app
        if (!opened) {
            try {
                Intent intent = getPackageManager()
                        .getLaunchIntentForPackage(packageName);

                if (intent != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to open", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public String convertTime(long timestamp){
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm:ss a");
        sdf.setTimeZone(TimeZone.getDefault()); // IST

        return sdf.format(date);
    }
}
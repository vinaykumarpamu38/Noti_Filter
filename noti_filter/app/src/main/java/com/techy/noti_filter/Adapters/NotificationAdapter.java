package com.techy.noti_filter.Adapters;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.techy.noti_filter.R;
import com.techy.noti_filter.databinding.ItemNotificationBinding;
import com.techy.noti_filter.model.NotificationData;
import com.techy.noti_filter.ui.activities.NotificationDetail;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationData> list;

    public NotificationAdapter(List<NotificationData> list) {
        this.list = list;
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        ItemNotificationBinding binding;

        public ViewHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        NotificationData item = list.get(position);

        Log.d("DATA_DEBUG", "AdapterPKG: " + item.packageName);

        // App Icon
        Bitmap bitmap = byteArrayToBitmap(item.appIcon);

        if (bitmap != null) {
            holder.binding.appIcon.setImageBitmap(bitmap);
        } else {
            holder.binding.appIcon.setImageResource(R.drawable.ic_app_placeholder);

            Log.d(
                    "ICON_DEBUG",
                    "Using default icon for: " + item.packageName
            );
        }

        // Text Fields
        holder.binding.title.setText(
                item.title != null ? item.title : "No Title"
        );

        holder.binding.message.setText(
                item.body != null ? item.body : "No Message"
        );

        holder.binding.appName.setText(
                item.app != null ? item.app : "Unknown App"
        );


        holder.binding.meta.setText(
                item.label + ", " + convertTime(item.postTime)
        );

        // Priority indicator — purely visual, derived from the existing label
        // field. Doesn't change what's stored or how labels are generated.
        holder.binding.priorityDot.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(priorityColor(holder, item.label))
        );

        // Item Click
        holder.binding.getRoot().setOnClickListener(view -> {

            Intent intent = new Intent(
                    view.getContext(),
                    NotificationDetail.class
            );

            intent.putExtra("data", item);

            view.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    public void updateList(List<NotificationData> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    /** Maps the existing 0-4 label scale to a color for the at-a-glance
     * priority dot. Presentation only — the label value itself is untouched. */
    private int priorityColor(ViewHolder holder, int label) {
        switch (label) {
            case 0:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.colorNeutralPriority);
            case 1:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.colorInfo);
            case 2:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.colorInfo);
            case 3:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.colorWarning);
            default:
                return ContextCompat.getColor(holder.itemView.getContext(), R.color.colorError);
        }
    }

    private Bitmap byteArrayToBitmap(byte[] byteArray) {

        try {

            if (byteArray == null) {
                Log.d("ICON_DEBUG", "Icon byte array is NULL");
                return null;
            }

            if (byteArray.length == 0) {
                Log.d("ICON_DEBUG", "Icon byte array is EMPTY");
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    byteArray,
                    0,
                    byteArray.length
            );

            if (bitmap == null) {
                Log.d("ICON_DEBUG", "Bitmap decode returned NULL");
            }

            return bitmap;

        } catch (Exception e) {

            Log.e(
                    "ICON_DEBUG",
                    "Failed to decode bitmap",
                    e
            );

            return null;
        }
    }

    private String convertTime(long timestamp) {

        try {

            Date date = new Date(timestamp);

            SimpleDateFormat sdf =
                    new SimpleDateFormat(
                            "dd MMM yyyy, hh:mm:ss a",
                            Locale.getDefault()
                    );

            sdf.setTimeZone(TimeZone.getDefault());

            return sdf.format(date);

        } catch (Exception e) {

            Log.e(
                    "TIME_DEBUG",
                    "Time conversion failed",
                    e
            );

            return "";
        }
    }
}

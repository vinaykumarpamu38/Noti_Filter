package com.techy.noti_filter.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.techy.noti_filter.R;
import com.techy.noti_filter.dao.AppInfo;

import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private final List<AppInfo> installedApps;

    public AppListAdapter(List<AppInfo> installedApps) {
        this.installedApps = installedApps;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        AppInfo app = installedApps.get(position);

        holder.ivIcon.setImageDrawable(app.getIcon());
        holder.tvAppName.setText(app.getAppName());

        // Prevent RecyclerView from triggering the listener
        holder.cbExclude.setOnCheckedChangeListener(null);

        // Show current state
        holder.cbExclude.setChecked(app.isSelected());

        holder.cbExclude.setOnCheckedChangeListener((buttonView, isChecked) -> {

            app.setSelected(isChecked);

//            SharedPreferences prefs = buttonView.getContext()
//                    .getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//
//            Set<String> selectedApps =
//                    new HashSet<>(prefs.getStringSet("selected_apps", new HashSet<>()));
//
//            if (isChecked) {
//                selectedApps.add(app.getPackageName());
//            } else {
//                selectedApps.remove(app.getPackageName());
//            }
//
//            prefs.edit().putStringSet("selected_apps", selectedApps).apply();
        });
    }

    @Override
    public int getItemCount() {
        return installedApps.size();
    }

    public List<AppInfo> getInstalledApps() {
        return installedApps;
    }
    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView ivIcon;
        TextView tvAppName;
        CheckBox cbExclude;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            ivIcon = itemView.findViewById(R.id.appIcon_select);
            tvAppName = itemView.findViewById(R.id.appName_Select);
            cbExclude = itemView.findViewById(R.id.item_app_checkbox);
        }
    }
}
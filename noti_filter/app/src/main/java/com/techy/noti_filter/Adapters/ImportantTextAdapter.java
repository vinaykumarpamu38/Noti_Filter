package com.techy.noti_filter.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.techy.noti_filter.R;

import java.util.List;

public class ImportantTextAdapter extends RecyclerView.Adapter<ImportantTextAdapter.ViewHolder> {

    private final List<String> importantList;

    public ImportantTextAdapter(List<String> importantList) {
        this.importantList = importantList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_imp_text, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.txtImportant.setText(importantList.get(position));
    }

    @Override
    public int getItemCount() {
        return importantList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtImportant;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtImportant = itemView.findViewById(R.id.txtImportant);
        }
    }
}
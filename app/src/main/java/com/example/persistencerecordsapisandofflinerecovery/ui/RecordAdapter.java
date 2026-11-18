package com.example.persistencerecordsapisandofflinerecovery.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.persistencerecordsapisandofflinerecovery.R;
import com.example.persistencerecordsapisandofflinerecovery.model.Record;
import com.example.persistencerecordsapisandofflinerecovery.model.SyncStatus;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adapter for displaying records in RecyclerView.
 * Uses ListAdapter with DiffUtil for efficient updates.
 */
public class RecordAdapter extends ListAdapter<Record, RecordAdapter.RecordViewHolder> {

    private final OnRecordActionListener listener;

    public interface OnRecordActionListener {
        void onSyncClick(Record record);
        void onRetryClick(Record record);
        void onEditClick(Record record);
        void onDeleteClick(Record record);
    }

    public RecordAdapter(OnRecordActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class RecordViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvStatus;
        private final TextView tvDescription;
        private final TextView tvQuantity;
        private final TextView tvClientId;
        private final TextView tvServerId;
        private final TextView tvTimestamps;
        private final TextView tvError;
        private final MaterialButton btnSync;
        private final MaterialButton btnRetry;
        private final MaterialButton btnEdit;
        private final MaterialButton btnDelete;

        RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvClientId = itemView.findViewById(R.id.tvClientId);
            tvServerId = itemView.findViewById(R.id.tvServerId);
            tvTimestamps = itemView.findViewById(R.id.tvTimestamps);
            tvError = itemView.findViewById(R.id.tvError);
            btnSync = itemView.findViewById(R.id.btnSync);
            btnRetry = itemView.findViewById(R.id.btnRetry);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(Record record) {
            tvTitle.setText(record.title);
            tvDescription.setText(record.description != null && !record.description.isEmpty() ? record.description : "No description");
            tvQuantity.setText(String.format(Locale.getDefault(), "%.2f %s", record.quantity, record.unit));
            tvClientId.setText("ClientID: " + record.clientId.substring(0, Math.min(8, record.clientId.length())) + "...");

            // Server ID
            if (record.serverId != null) {
                tvServerId.setText("ServerID: " + record.serverId.substring(0, Math.min(12, record.serverId.length())) + "...");
                tvServerId.setVisibility(View.VISIBLE);
            } else {
                tvServerId.setVisibility(View.GONE);
            }

            // Timestamps
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            String created = sdf.format(new Date(record.createdAt));
            String updated = sdf.format(new Date(record.updatedAt));
            tvTimestamps.setText("Created: " + created + " | Updated: " + updated);

            // Status with color
            bindStatus(record.status, record.lastError);

            // Button visibility based on status
            switch (record.status) {
                case SAVED:
                    btnSync.setVisibility(View.VISIBLE);
                    btnRetry.setVisibility(View.GONE);
                    btnEdit.setVisibility(View.VISIBLE);
                    btnDelete.setVisibility(View.VISIBLE);
                    btnSync.setEnabled(true);
                    btnSync.setText("Sync");
                    break;
                case PENDING:
                    btnSync.setVisibility(View.VISIBLE);
                    btnSync.setText("Syncing...");
                    btnSync.setEnabled(false);
                    btnRetry.setVisibility(View.GONE);
                    btnEdit.setVisibility(View.GONE);
                    btnDelete.setVisibility(View.GONE);
                    break;
                case ACKNOWLEDGED:
                    btnSync.setVisibility(View.GONE);
                    btnRetry.setVisibility(View.GONE);
                    btnEdit.setVisibility(View.GONE);
                    btnDelete.setVisibility(View.VISIBLE);
                    break;
                case FAILED:
                    btnSync.setVisibility(View.GONE);
                    btnRetry.setVisibility(View.VISIBLE);
                    btnEdit.setVisibility(View.VISIBLE);
                    btnDelete.setVisibility(View.VISIBLE);
                    break;
                case CONFLICT:
                    btnSync.setVisibility(View.GONE);
                    btnRetry.setVisibility(View.GONE);
                    btnEdit.setVisibility(View.VISIBLE);
                    btnDelete.setVisibility(View.VISIBLE);
                    break;
            }

            // Click listeners - use RecordAdapter.this.listener to access outer class field
            final OnRecordActionListener l = RecordAdapter.this.listener;
            btnSync.setOnClickListener(v -> l.onSyncClick(record));
            btnRetry.setOnClickListener(v -> l.onRetryClick(record));
            btnEdit.setOnClickListener(v -> l.onEditClick(record));
            btnDelete.setOnClickListener(v -> l.onDeleteClick(record));
        }

        private void bindStatus(SyncStatus status, String error) {
            tvStatus.setText(status.name());

            // Set background color based on status
            int color;
            switch (status) {
                case SAVED: color = 0xFF4CAF50; break; // Green
                case PENDING: color = 0xFFFF9800; break; // Orange
                case ACKNOWLEDGED: color = 0xFF2196F3; break; // Blue
                case FAILED: color = 0xFFF44336; break; // Red
                case CONFLICT: color = 0xFF9C27B0; break; // Purple
                default: color = 0xFF9E9E9E; break; // Grey
            }
            tvStatus.setBackgroundColor(color);

            // Show error for failed/conflict
            if (error != null && (status == SyncStatus.FAILED || status == SyncStatus.CONFLICT)) {
                tvError.setText(error);
                tvError.setVisibility(View.VISIBLE);
            } else {
                tvError.setVisibility(View.GONE);
            }
        }
    }

    private static final DiffUtil.ItemCallback<Record> DIFF_CALLBACK = new DiffUtil.ItemCallback<Record>() {
        @Override
        public boolean areItemsTheSame(@NonNull Record oldItem, @NonNull Record newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Record oldItem, @NonNull Record newItem) {
            return oldItem.equals(newItem);
        }
    };
}
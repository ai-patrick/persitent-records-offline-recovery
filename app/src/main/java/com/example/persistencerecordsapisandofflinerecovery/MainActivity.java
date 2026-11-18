package com.example.persistencerecordsapisandofflinerecovery;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.persistencerecordsapisandofflinerecovery.api.MockApiService;
import com.example.persistencerecordsapisandofflinerecovery.databinding.ActivityMainBinding;
import com.example.persistencerecordsapisandofflinerecovery.model.Record;
import com.example.persistencerecordsapisandofflinerecovery.model.SyncStatus;
import com.example.persistencerecordsapisandofflinerecovery.repository.SyncState;
import com.example.persistencerecordsapisandofflinerecovery.ui.RecordAdapter;
import com.example.persistencerecordsapisandofflinerecovery.viewmodel.RecordViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Main Activity for Lab 08: Persistent Records, APIs and Offline Recovery.
 * 
 * Demonstrates:
 * 1. Local structured storage with Room (survives process restart)
 * 2. Form with validation, save, list, reopen, correct
 * 3. Transaction boundary - errors don't produce false success
 * 4. Mock API with controlled sync scenarios
 * 5. Explicit sync states: SAVED, PENDING, ACKNOWLEDGED, FAILED, CONFLICT
 * 6. Idempotent retry using clientId (prevents duplicates)
 * 7. Lost response handling (timeout but server succeeded)
 * 8. Conflict vs transient failure distinction
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private RecordViewModel viewModel;
    private RecordAdapter adapter;
    private final List<String> units = Arrays.asList("kg", "g", "m", "cm", "l", "ml", "pcs", "units");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupViewModel();
        setupForm();
        setupMockModeButtons();
        setupRecyclerView();
        setupFab();
        observeViewModel();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(RecordViewModel.class);
    }

    private void setupForm() {
        // Setup unit dropdown
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, units);
        binding.actvUnit.setAdapter(unitAdapter);
        binding.actvUnit.setText(units.get(0), false);

        binding.btnSave.setOnClickListener(v -> {
            String title = binding.etTitle.getText().toString();
            String description = binding.etDescription.getText().toString();
            String quantity = binding.etQuantity.getText().toString();
            String unit = binding.actvUnit.getText().toString();

            viewModel.createRecord(title, description, quantity, unit);

            // Clear form
            binding.etTitle.setText("");
            binding.etDescription.setText("");
            binding.etQuantity.setText("1");
            binding.actvUnit.setText(units.get(0), false);
        });

        // Observe field errors for real-time validation feedback
        viewModel.fieldErrors.observe(this, errors -> {
            if (errors != null) {
                binding.tilTitle.setError(errors.get("title"));
                binding.tilDescription.setError(errors.get("description"));
                binding.tilQuantity.setError(errors.get("quantity"));
                binding.tilUnit.setError(errors.get("unit"));
            } else {
                binding.tilTitle.setError(null);
                binding.tilDescription.setError(null);
                binding.tilQuantity.setError(null);
                binding.tilUnit.setError(null);
            }
        });

        viewModel.formError.observe(this, error -> {
            binding.tvFormError.setText(error != null ? error : "");
            binding.tvFormError.setVisibility(error != null ? View.VISIBLE : View.GONE);
        });
    }

    private void setupMockModeButtons() {
        binding.btnModeNormal.setOnClickListener(v -> viewModel.setMockMode(MockApiService.MockMode.NORMAL));
        binding.btnModeUnavailable.setOnClickListener(v -> viewModel.setMockMode(MockApiService.MockMode.UNAVAILABLE));
        binding.btnModeLostResponse.setOnClickListener(v -> viewModel.setMockMode(MockApiService.MockMode.LOST_RESPONSE));
        binding.btnModeConflict.setOnClickListener(v -> viewModel.setMockMode(MockApiService.MockMode.CONFLICT));
        binding.btnResetMock.setOnClickListener(v -> viewModel.resetMockServer());

        // Highlight active mode
        viewModel.mockMode.observe(this, this::updateMockModeButtons);
    }

    private void updateMockModeButtons(MockApiService.MockMode activeMode) {
        if (activeMode == null) return;

        setButtonActive(binding.btnModeNormal, activeMode == MockApiService.MockMode.NORMAL);
        setButtonActive(binding.btnModeUnavailable, activeMode == MockApiService.MockMode.UNAVAILABLE);
        setButtonActive(binding.btnModeLostResponse, activeMode == MockApiService.MockMode.LOST_RESPONSE);
        setButtonActive(binding.btnModeConflict, activeMode == MockApiService.MockMode.CONFLICT);
    }

    private void setButtonActive(MaterialButton button, boolean active) {
        button.setChecked(active);
        if (active) {
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFF174d43));
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFe2eeea));
        } else {
            button.setStrokeColor(null);
            button.setBackgroundTintList(null);
        }
    }

    private void setupRecyclerView() {
        adapter = new RecordAdapter(new RecordAdapter.OnRecordActionListener() {
            @Override
            public void onSyncClick(Record record) { viewModel.syncRecord(record); }
            @Override
            public void onRetryClick(Record record) { viewModel.retrySync(record); }
            @Override
            public void onEditClick(Record record) { showEditDialog(record); }
            @Override
            public void onDeleteClick(Record record) { confirmDelete(record); }
        });
        binding.rvRecords.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRecords.setAdapter(adapter);
    }

    private void setupFab() {
        binding.fabClear.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear All Data")
                    .setMessage("This will delete all records from the local database. This action cannot be undone.")
                    .setPositiveButton("Clear", (dialog, which) -> viewModel.clearAllData())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void observeViewModel() {
        viewModel.records.observe(this, records -> {
            adapter.submitList(records);
            binding.tvEmptyState.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.syncState.observe(this, state -> {
            if (state instanceof SyncState.InProgress) {
                // Sync in progress - UI updated via adapter
            } else if (state instanceof SyncState.Success) {
                SyncState.Success success = (SyncState.Success) state;
                showSnackbar("✓ " + success.record.title + " synced successfully");
            } else if (state instanceof SyncState.Failure) {
                SyncState.Failure failure = (SyncState.Failure) state;
                String type = failure.isTransient ? "Transient (retry safe)" : "Permanent (needs resolution)";
                showSnackbar("✗ Sync failed [" + type + "]: " + failure.error);
            }
        });

        viewModel.lastActionMessage.observe(this, message -> {
            if (message != null) showSnackbar(message);
        });

        viewModel.isSyncing.observe(this, isSyncing -> {
            binding.btnSyncAll.setEnabled(!isSyncing);
        });

        binding.btnSyncAll.setOnClickListener(v -> viewModel.syncAllPending());

        // Load status counts periodically
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            runOnUiThread(() -> updateStatusCounts(viewModel.getStatusCounts()));
        }, 0, 2, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void updateStatusCounts(List<com.example.persistencerecordsapisandofflinerecovery.data.RecordDao.StatusCount> counts) {
        binding.llStatusCounts.removeAllViews();

        List<SyncStatus> statusOrder = Arrays.asList(
                SyncStatus.SAVED,
                SyncStatus.PENDING,
                SyncStatus.ACKNOWLEDGED,
                SyncStatus.FAILED,
                SyncStatus.CONFLICT
        );

        java.util.Map<SyncStatus, Integer> countMap = new java.util.HashMap<>();
        for (com.example.persistencerecordsapisandofflinerecovery.data.RecordDao.StatusCount sc : counts) {
            countMap.put(sc.status, sc.count);
        }

        for (SyncStatus status : statusOrder) {
            Integer count = countMap.get(status);
            if (count != null && count > 0) {
                Chip chip = new Chip(this);
                chip.setText(status.getDisplayName() + ": " + count);
                chip.setClickable(false);
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(getStatusColor(status)));
                chip.setTextColor(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                binding.llStatusCounts.addView(chip);
            }
        }
    }

    private int getStatusColor(SyncStatus status) {
        switch (status) {
            case SAVED: return 0xFF4CAF50;
            case PENDING: return 0xFFFF9800;
            case ACKNOWLEDGED: return 0xFF2196F3;
            case FAILED: return 0xFFF44336;
            case CONFLICT: return 0xFF9C27B0;
            default: return 0xFF9E9E9E;
        }
    }

    private void showEditDialog(Record record) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_record, null);

        AutoCompleteTextView etTitle = dialogView.findViewById(R.id.etEditTitle);
        AutoCompleteTextView etDescription = dialogView.findViewById(R.id.etEditDescription);
        AutoCompleteTextView etQuantity = dialogView.findViewById(R.id.etEditQuantity);
        AutoCompleteTextView actvUnit = dialogView.findViewById(R.id.actvEditUnit);
        TextView tvError = dialogView.findViewById(R.id.tvEditError);

        // Setup unit dropdown
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, units);
        actvUnit.setAdapter(unitAdapter);

        // Populate with current values
        etTitle.setText(record.title);
        etDescription.setText(record.description);
        etQuantity.setText(String.valueOf(record.quantity));
        actvUnit.setText(record.unit, false);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String title = etTitle.getText().toString();
                    String description = etDescription.getText().toString();
                    String quantityStr = etQuantity.getText().toString();
                    String unit = actvUnit.getText().toString();

                    double quantity = 0.0;
                    try { quantity = Double.parseDouble(quantityStr); } catch (NumberFormatException ignored) {}

                    Record updated = record.copyWith(
                            null, null, title, description, quantity, unit,
                            SyncStatus.SAVED, null, System.currentTimeMillis(), null, null, 0, null
                    );
                    viewModel.updateRecord(updated);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Record record) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Record")
                .setMessage("Delete \"" + record.title + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean force = record.status == SyncStatus.ACKNOWLEDGED;
                    viewModel.deleteRecord(record, force);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
package com.example.persistencerecordsapisandofflinerecovery.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.persistencerecordsapisandofflinerecovery.api.MockApiService;
import com.example.persistencerecordsapisandofflinerecovery.model.Record;
import com.example.persistencerecordsapisandofflinerecovery.model.SyncStatus;
import com.example.persistencerecordsapisandofflinerecovery.repository.RecordRepository;
import com.example.persistencerecordsapisandofflinerecovery.repository.SyncResult;
import com.example.persistencerecordsapisandofflinerecovery.repository.SyncState;
import com.example.persistencerecordsapisandofflinerecovery.repository.ValidationException;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for record management UI.
 * 
 * Handles:
 * - Form validation and record creation
 * - Sync operations with retry logic
 * - UI state management (loading, errors, success)
 * - Mock mode selection for testing
 * 
 * Survives configuration changes (rotation) - retains data.
 * Does NOT survive process death - that's what Room is for.
 */
public class RecordViewModel extends AndroidViewModel {

    private final RecordRepository repository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService mainExecutor = Executors.newSingleThreadExecutor();

    // UI State
    private final LiveData<List<Record>> _records;
    public final LiveData<List<Record>> records;

    private final LiveData<SyncState> _syncState;
    public final LiveData<SyncState> syncState;

    private final MutableLiveData<String> _formError = new MutableLiveData<>();
    public final LiveData<String> formError = _formError;

    private final MutableLiveData<java.util.Map<String, String>> _fieldErrors = new MutableLiveData<>();
    public final LiveData<java.util.Map<String, String>> fieldErrors = _fieldErrors;

    private final MutableLiveData<Boolean> _isSyncing = new MutableLiveData<>();
    public final LiveData<Boolean> isSyncing = _isSyncing;

    private final MutableLiveData<String> _lastActionMessage = new MutableLiveData<>();
    public final LiveData<String> lastActionMessage = _lastActionMessage;

    private final MutableLiveData<MockApiService.MockMode> _mockMode = new MutableLiveData<>();
    public final LiveData<MockApiService.MockMode> mockMode = _mockMode;

    public RecordViewModel(Application application) {
        super(application);
        this.repository = new RecordRepository(application);
        this._records = repository.getAllRecords();
        this.records = _records;
        this._syncState = repository.syncState;
        this.syncState = _syncState;

        _mockMode.setValue(MockApiService.getMockMode());
        loadInitialData();
    }

    private void loadInitialData() {
        ioExecutor.execute(() -> repository.seedSyntheticData());
    }

    /**
     * Create a new record from form input.
     * Validates input, saves locally, returns result.
     */
    public void createRecord(final String title, final String description, final String quantityStr, final String unit) {
        postValue(_formError, null);
        postValue(_fieldErrors, null);

        double qty = 0.0;
        try {
            qty = Double.parseDouble(quantityStr);
        } catch (NumberFormatException e) {
            qty = 0.0;
        }
        final double finalQty = qty;

        ioExecutor.execute(() -> {
            try {
                final Record record = repository.createRecord(title, description, finalQty, unit);
                postValue(_lastActionMessage, "Record saved locally (ID: " + record.id + ", ClientID: " + record.clientId.substring(0, 8) + "...)");
                postValue(_formError, null);
            } catch (ValidationException e) {
                postValue(_fieldErrors, e.errors);
                postValue(_formError, "Please fix the highlighted fields");
            } catch (Exception e) {
                postValue(_formError, "Save failed: " + e.getMessage());
            }
        });
    }

    /**
     * Update an existing record.
     */
    public void updateRecord(Record record) {
        ioExecutor.execute(() -> {
            try {
                Record updated = repository.updateRecord(record);
                postValue(_lastActionMessage, "Record updated locally (will re-sync)");
            } catch (Exception e) {
                postValue(_formError, "Update failed: " + e.getMessage());
            }
        });
    }

    /**
     * Delete a record.
     */
    public void deleteRecord(Record record, boolean force) {
        ioExecutor.execute(() -> {
            try {
                repository.deleteRecord(record.id, force);
                postValue(_lastActionMessage, "Record deleted");
            } catch (Exception e) {
                postValue(_formError, "Delete failed: " + e.getMessage());
            }
        });
    }

    /**
     * Sync a single record.
     */
    public void syncRecord(Record record) {
        postValue(_isSyncing, true);
        postValue(_formError, null);

        ioExecutor.execute(() -> {
            SyncResult result = repository.syncRecord(record);
            handleSyncResult(result);
            postValue(_isSyncing, false);
        });
    }

    /**
     * Retry a failed sync.
     * Uses same clientId - demonstrates idempotency.
     */
    public void retrySync(Record record) {
        postValue(_isSyncing, true);
        postValue(_formError, null);

        ioExecutor.execute(() -> {
            SyncResult result = repository.retrySync(record);
            handleSyncResult(result);
            postValue(_isSyncing, false);
        });
    }

    /**
     * Sync all pending records.
     */
    public void syncAllPending() {
        postValue(_isSyncing, true);
        postValue(_formError, null);

        ioExecutor.execute(() -> {
            List<SyncResult> results = repository.syncAllPending(3);
            int successCount = 0;
            int failCount = 0;
            for (SyncResult r : results) {
                if (r instanceof SyncResult.Success) successCount++;
                else failCount++;
            }
            postValue(_lastActionMessage, "Sync complete: " + successCount + " succeeded, " + failCount + " failed");
            postValue(_isSyncing, false);
        });
    }

    private void handleSyncResult(SyncResult result) {
        if (result instanceof SyncResult.Success) {
            SyncResult.Success s = (SyncResult.Success) result;
            postValue(_lastActionMessage, "✓ Synced: " + s.record.title + " (Server: " + s.record.serverId + ")");
        } else if (result instanceof SyncResult.TransientFailure) {
            SyncResult.TransientFailure f = (SyncResult.TransientFailure) result;
            postValue(_formError, "⚠ Transient failure (retry " + f.retryCount + "/3): " + f.error + ". Safe to retry with same ClientID.");
        } else if (result instanceof SyncResult.PermanentFailure) {
            SyncResult.PermanentFailure f = (SyncResult.PermanentFailure) result;
            postValue(_formError, "✗ Permanent failure: " + f.error + ". NOT safe to retry blindly - needs resolution.");
        }
    }

    /**
     * Set mock mode for testing different scenarios.
     */
    public void setMockMode(MockApiService.MockMode mode) {
        MockApiService.setMockMode(mode);
        postValue(_mockMode, mode);
        postValue(_lastActionMessage, "Mock mode: " + mode.getDisplayName());
    }

    /**
     * Reset mock server state.
     */
    public void resetMockServer() {
        MockApiService.reset();
        postValue(_lastActionMessage, "Mock server reset");
    }

    /**
     * Get status counts for summary display.
     */
    public List<com.example.persistencerecordsapisandofflinerecovery.data.RecordDao.StatusCount> getStatusCounts() {
        return repository.getStatusCounts();
    }

    /**
     * Clear all data (for testing).
     */
    public void clearAllData() {
        ioExecutor.execute(() -> {
            repository.clearAllData();
            postValue(_lastActionMessage, "All data cleared");
        });
    }

    private <T> void postValue(MutableLiveData<T> liveData, T value) {
        mainExecutor.execute(() -> liveData.postValue(value));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdown();
        mainExecutor.shutdown();
        repository.shutdown();
    }
}
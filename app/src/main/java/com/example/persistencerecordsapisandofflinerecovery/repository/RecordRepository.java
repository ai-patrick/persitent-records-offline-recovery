package com.example.persistencerecordsapisandofflinerecovery.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.persistencerecordsapisandofflinerecovery.api.MockApiService;
import com.example.persistencerecordsapisandofflinerecovery.data.AppDatabase;
import com.example.persistencerecordsapisandofflinerecovery.data.RecordDao;
import com.example.persistencerecordsapisandofflinerecovery.model.Record;
import com.example.persistencerecordsapisandofflinerecovery.model.SyncStatus;
import com.example.persistencerecordsapisandofflinerecovery.model.ValidationResult;
import com.example.persistencerecordsapisandofflinerecovery.model.RecordValidator;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for record operations.
 * 
 * Encapsulates data sources (local database + mock API) and provides
 * a clean API for the ViewModel.
 * 
 * Key responsibilities:
 * - Local persistence via Room (survives process restart)
 * - Sync orchestration with retry logic
 * - State transitions: SAVED -> PENDING -> ACKNOWLEDGED/FAILED/CONFLICT
 * - Idempotent retry using clientId
 * - Error handling without false success
 */
public class RecordRepository {

    private final AppDatabase database;
    private final RecordDao dao;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    // Sync state observation
    private final MutableLiveData<SyncState> _syncState = new MutableLiveData<>();
    public final LiveData<SyncState> syncState = _syncState;

    public RecordRepository(Application application) {
        this.database = AppDatabase.getInstance(application);
        this.dao = database.recordDao();
    }

    /**
     * Get all records as LiveData for UI.
     */
    public LiveData<List<Record>> getAllRecords() {
        return dao.getAllLiveData();
    }

    /**
     * Create and save a new record locally.
     * 
     * Transaction boundary: The insert is atomic.
     * If validation fails, throws exception - no false success.
     * If database write fails, throws exception - no false success.
     * 
     * @return The saved record with generated ID
     * @throws ValidationException if input invalid
     * @throws RuntimeException if database write fails
     */
    public Record createRecord(String title, String description, double quantity, String unit) throws ValidationException {
        ValidationResult validation = RecordValidator.validate(title, description, quantity, unit);

        if (validation instanceof ValidationResult.Valid) {
            Record record = ((ValidationResult.Valid) validation).record;
            long id = dao.insert(record);
            record.id = id;
            return record;
        } else {
            ValidationResult.Invalid invalid = (ValidationResult.Invalid) validation;
            throw new ValidationException(invalid.errors);
        }
    }

    /**
     * Update an existing record locally.
     * Only allows updates for records not yet acknowledged by server.
     */
    public Record updateRecord(Record record) {
        if (record.status == SyncStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Cannot update acknowledged record. Create new version instead.");
        }

        Record updated = record.copyWith(
                null, null, record.title, record.description,
                record.quantity, record.unit, SyncStatus.SAVED,
                null, System.currentTimeMillis(), null,
                null, 0, null
        );

        int rows = dao.update(updated);
        if (rows == 0) {
            throw new RuntimeException("Record not found for update: " + record.id);
        }
        return updated;
    }

    /**
     * Delete a record locally.
     * Only allows deletion if not acknowledged (or with force flag).
     */
    public void deleteRecord(long id, boolean force) {
        Record record = dao.getById(id);
        if (record == null) {
            throw new IllegalArgumentException("Record not found: " + id);
        }

        if (record.status == SyncStatus.ACKNOWLEDGED && !force) {
            throw new IllegalStateException("Cannot delete acknowledged record without force. Use force=true to override.");
        }

        dao.deleteById(id);
    }

    /**
     * Get record by ID.
     */
    public Record getRecord(long id) {
        return dao.getById(id);
    }

    /**
     * Get record by clientId (for idempotency checks).
     */
    public Record getRecordByClientId(String clientId) {
        return dao.getByClientId(clientId);
    }

    /**
     * Sync a single record to the mock API.
     * 
     * State machine:
     * SAVED -> PENDING -> ACKNOWLEDGED (success)
     * SAVED -> PENDING -> FAILED (transient error, can retry)
     * SAVED -> PENDING -> CONFLICT (permanent error, needs resolution)
     * 
     * Uses clientId for idempotency - same operation = same clientId.
     * This prevents duplicates on retry.
     */
    public SyncResult syncRecord(Record record) {
        // Transition to PENDING
        Record pendingRecord = record.copyWith(
                null, null, null, null, null, null, SyncStatus.PENDING,
                null, System.currentTimeMillis(), null, null, null, null
        );
        dao.update(pendingRecord);
        _syncState.postValue(new SyncState.InProgress(record.id));

        try {
            MockApiService.SyncResult apiResult = MockApiService.syncRecord(pendingRecord);

            // Success - mark acknowledged
            Record acknowledgedRecord = pendingRecord.copyWith(
                    null, null, null, null, null, null, SyncStatus.ACKNOWLEDGED,
                    null, System.currentTimeMillis(), System.currentTimeMillis(),
                    apiResult.serverId, 0, null
            );
            dao.update(acknowledgedRecord);
            _syncState.postValue(new SyncState.Success(acknowledgedRecord));

            return new SyncResult.Success(acknowledgedRecord, apiResult.message);
        } catch (Exception e) {
            boolean isTransient = MockApiService.isTransient(e);
            int newRetryCount = pendingRecord.retryCount + 1;

            Record failedRecord = pendingRecord.copyWith(
                    null, null, null, null, null, null,
                    isTransient ? SyncStatus.FAILED : SyncStatus.CONFLICT,
                    null, System.currentTimeMillis(), null, null, newRetryCount, e.getMessage()
            );
            dao.update(failedRecord);

            _syncState.postValue(new SyncState.Failure(failedRecord, e.getMessage() != null ? e.getMessage() : "Unknown error", isTransient));

            if (isTransient) {
                return new SyncResult.TransientFailure(failedRecord, e.getMessage() != null ? e.getMessage() : "Transient error", newRetryCount);
            } else {
                return new SyncResult.PermanentFailure(failedRecord, e.getMessage() != null ? e.getMessage() : "Permanent error");
            }
        }
    }

    /**
     * Retry a failed record sync.
     * Uses the SAME clientId for idempotency - critical for preventing duplicates.
     */
    public SyncResult retrySync(Record record) {
        if (record.status != SyncStatus.FAILED && record.status != SyncStatus.SAVED) {
            throw new IllegalStateException("Can only retry FAILED or SAVED records, not " + record.status);
        }
        return syncRecord(record);
    }

    /**
     * Sync all pending records (SAVED or FAILED with retries left).
     */
    public List<SyncResult> syncAllPending(int maxRetries) {
        // This is a simplified synchronous version for the demo
        // In a real app, this would be a suspend function
        List<Record> savedRecords = dao.getByStatus(SyncStatus.SAVED).getValue();
        List<Record> failedRecords = dao.getByStatus(SyncStatus.FAILED).getValue();
        
        if (savedRecords == null) savedRecords = new java.util.ArrayList<>();
        if (failedRecords == null) failedRecords = new java.util.ArrayList<>();
        
        // Filter failed records with retries left
        List<Record> pendingRecords = new java.util.ArrayList<>();
        pendingRecords.addAll(savedRecords);
        for (Record r : failedRecords) {
            if (r.retryCount < maxRetries) {
                pendingRecords.add(r);
            }
        }

        List<SyncResult> results = new java.util.ArrayList<>();
        for (Record record : pendingRecords) {
            results.add(syncRecord(record));
        }
        return results;
    }

    /**
     * Get sync status counts for UI display.
     */
    public List<RecordDao.StatusCount> getStatusCounts() {
        return dao.getStatusCounts();
    }

    /**
     * Seed with synthetic data for testing.
     */
    public void seedSyntheticData() {
        ioExecutor.execute(() -> {
            List<Record> existing = dao.getAll().getValue();
            if (existing != null && !existing.isEmpty()) return;

            List<Record> syntheticRecords = List.of(
                    Record.create("Apples", "Fresh organic apples", 5.0, "kg"),
                    Record.create("Notebooks", "A4 ruled notebooks", 10, "pcs"),
                    Record.create("Milk", "Whole milk 1L cartons", 6, "l")
            );
            dao.insertAll(syntheticRecords);
        });
    }

    /**
     * Clear all data (for testing).
     */
    public void clearAllData() {
        ioExecutor.execute(() -> {
            List<Record> all = dao.getAll().getValue();
            if (all != null) {
                for (Record record : all) {
                    dao.deleteById(record.id);
                }
            }
        });
    }

    public void shutdown() {
        ioExecutor.shutdown();
    }
}
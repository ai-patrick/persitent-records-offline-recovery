package com.example.persistencerecordsapisandofflinerecovery.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.persistencerecordsapisandofflinerecovery.model.Record;
import com.example.persistencerecordsapisandofflinerecovery.model.SyncStatus;

import java.util.List;

/**
 * Data Access Object for Record entity.
 * Provides database operations for persistent records with sync state management.
 * 
 * Transaction boundary notes:
 * - Insert/update operations are atomic per record
 * - Bulk operations use @Transaction for consistency
 * - Error during write throws exception - no false success
 */
@Dao
public interface RecordDao {

    /**
     * Insert a new record. Returns the new row ID.
     * Throws exception on failure - no false success.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Record record);

    /**
     * Insert multiple records in a single transaction.
     * All succeed or all fail - atomic batch.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    @Transaction
    List<Long> insertAll(List<Record> records);

    /**
     * Update an existing record. Returns number of rows affected.
     * Throws exception on failure.
     */
    @Update(onConflict = OnConflictStrategy.ABORT)
    int update(Record record);

    /**
     * Delete a record by ID.
     */
    @Query("DELETE FROM records WHERE id = :id")
    int deleteById(long id);

    /**
     * Get a record by local database ID.
     */
    @Query("SELECT * FROM records WHERE id = :id")
    Record getById(long id);

    /**
     * Get a record by client ID (for idempotent retry lookup).
     * This is critical for preventing duplicates on retry.
     */
    @Query("SELECT * FROM records WHERE clientId = :clientId")
    Record getByClientId(String clientId);

    /**
     * Get all records ordered by creation time (newest first).
     */
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    LiveData<List<Record>> getAll();

    /**
     * Get all records as LiveData for UI observation.
     */
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    LiveData<List<Record>> getAllLiveData();

    /**
     * Get records by sync status.
     */
    @Query("SELECT * FROM records WHERE status = :status ORDER BY createdAt DESC")
    LiveData<List<Record>> getByStatus(SyncStatus status);

    /**
     * Get records that need sync (SAVED or FAILED with retry count < max).
     */
    @Query("SELECT * FROM records WHERE status IN (:statuses) ORDER BY createdAt ASC")
    LiveData<List<Record>> getPendingSync(List<SyncStatus> statuses);

    /**
     * Get count of records by status.
     */
    @Query("SELECT status, COUNT(*) as count FROM records GROUP BY status")
    List<StatusCount> getStatusCounts();

    /**
     * Update sync status for a record (used during sync lifecycle).
     * This is a targeted update to avoid overwriting other fields.
     */
    @Query("UPDATE records SET status = :status, updatedAt = :updatedAt, retryCount = :retryCount, lastError = :lastError WHERE id = :id")
    int updateSyncStatus(long id, SyncStatus status, long updatedAt, int retryCount, String lastError);

    /**
     * Mark record as acknowledged by server.
     */
    @Query("UPDATE records SET status = :status, syncedAt = :syncedAt, serverId = :serverId, updatedAt = :updatedAt WHERE id = :id")
    int markAcknowledged(long id, SyncStatus status, long syncedAt, String serverId, long updatedAt);

    /**
     * Check if a clientId already exists (for duplicate prevention).
     */
    @Query("SELECT COUNT(*) FROM records WHERE clientId = :clientId")
    int existsByClientId(String clientId);

    /**
     * Data class for status count query result.
     */
    class StatusCount {
        public SyncStatus status;
        public int count;

        public StatusCount(SyncStatus status, int count) {
            this.status = status;
            this.count = count;
        }
    }
}
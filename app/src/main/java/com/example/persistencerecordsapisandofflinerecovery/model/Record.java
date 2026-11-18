package com.example.persistencerecordsapisandofflinerecovery.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * Record entity representing a persistent record with local storage and sync capabilities.
 * 
 * Fields:
 * - id: Auto-generated primary key (local database ID)
 * - clientId: UUID generated on client for idempotent retries (same operation = same clientId)
 * - title: Record title (required, max 100 chars)
 * - description: Optional description (max 500 chars)
 * - quantity: Numeric value with unit (e.g., "5 kg", "10 meters")
 * - unit: Unit of measurement (kg, m, l, pcs, etc.)
 * - status: Sync state - SAVED, PENDING, ACKNOWLEDGED, FAILED, CONFLICT
 * - createdAt: Timestamp when record was created locally
 * - updatedAt: Timestamp when record was last modified locally
 * - syncedAt: Timestamp when record was acknowledged by server
 * - serverId: ID assigned by server after successful sync (null if not synced)
 * - retryCount: Number of sync attempts
 * - lastError: Last error message from sync attempt
 */
@Entity(tableName = "records")
public class Record {
    @PrimaryKey(autoGenerate = true)
    public long id = 0;

    public String clientId;

    public String title;
    public String description = "";
    public double quantity = 0.0;
    public String unit = "pcs";

    public SyncStatus status = SyncStatus.SAVED;

    public long createdAt = System.currentTimeMillis();
    public long updatedAt = System.currentTimeMillis();
    public Long syncedAt = null;
    public String serverId = null;
    public int retryCount = 0;
    public String lastError = null;

    public Record() {
        this.clientId = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public static Record create(String title, String description, double quantity, String unit) {
        Record record = new Record();
        record.title = title;
        record.description = description;
        record.quantity = quantity;
        record.unit = unit;
        record.status = SyncStatus.SAVED;
        return record;
    }

    public Record copyWith(
            Long id, String clientId, String title, String description,
            Double quantity, String unit, SyncStatus status,
            Long createdAt, Long updatedAt, Long syncedAt,
            String serverId, Integer retryCount, String lastError) {
        Record copy = new Record();
        copy.id = id != null ? id : this.id;
        copy.clientId = clientId != null ? clientId : this.clientId;
        copy.title = title != null ? title : this.title;
        copy.description = description != null ? description : this.description;
        copy.quantity = quantity != null ? quantity : this.quantity;
        copy.unit = unit != null ? unit : this.unit;
        copy.status = status != null ? status : this.status;
        copy.createdAt = createdAt != null ? createdAt : this.createdAt;
        copy.updatedAt = updatedAt != null ? updatedAt : this.updatedAt;
        copy.syncedAt = syncedAt != null ? syncedAt : this.syncedAt;
        copy.serverId = serverId != null ? serverId : this.serverId;
        copy.retryCount = retryCount != null ? retryCount : this.retryCount;
        copy.lastError = lastError != null ? lastError : this.lastError;
        return copy;
    }

    @Override
    public String toString() {
        return "Record{id=" + id + ", title='" + title + "', status=" + status + "}";
    }
}
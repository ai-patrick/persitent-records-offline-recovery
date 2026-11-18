package com.example.persistencerecordsapisandofflinerecovery.model;

/**
 * Sync status enumeration representing the state of a record in the sync lifecycle.
 * 
 * SAVED: Record exists only locally, not yet attempted to sync
 * PENDING: Sync attempt in progress
 * ACKNOWLEDGED: Server confirmed receipt and processing
 * FAILED: Sync failed definitively (server rejected, validation error, etc.)
 * CONFLICT: Server has different version - requires manual resolution
 */
public enum SyncStatus {
    SAVED("Saved locally"),
    PENDING("Syncing..."),
    ACKNOWLEDGED("Synced"),
    FAILED("Failed"),
    CONFLICT("Conflict");

    private final String displayName;

    SyncStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
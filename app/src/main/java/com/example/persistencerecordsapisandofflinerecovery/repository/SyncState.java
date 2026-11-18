package com.example.persistencerecordsapisandofflinerecovery.repository;

import com.example.persistencerecordsapisandofflinerecovery.model.Record;

/**
 * Sync state for UI observation.
 */
public sealed class SyncState permits SyncState.InProgress, SyncState.Success, SyncState.Failure, SyncState.Idle {
    public static final class InProgress extends SyncState {
        public final long recordId;
        public InProgress(long recordId) { this.recordId = recordId; }
    }

    public static final class Success extends SyncState {
        public final Record record;
        public Success(Record record) { this.record = record; }
    }

    public static final class Failure extends SyncState {
        public final Record record;
        public final String error;
        public final boolean isTransient;
        public Failure(Record record, String error, boolean isTransient) {
            this.record = record;
            this.error = error;
            this.isTransient = isTransient;
        }
    }

    public static final class Idle extends SyncState {
        public static final Idle INSTANCE = new Idle();
        private Idle() {}
    }
}
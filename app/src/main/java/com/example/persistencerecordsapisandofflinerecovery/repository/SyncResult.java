package com.example.persistencerecordsapisandofflinerecovery.repository;

import com.example.persistencerecordsapisandofflinerecovery.model.Record;

/**
 * Result of a sync operation with typed outcomes.
 */
public sealed class SyncResult permits SyncResult.Success, SyncResult.TransientFailure, SyncResult.PermanentFailure {
    public static final class Success extends SyncResult {
        public final Record record;
        public final String message;
        public Success(Record record, String message) {
            this.record = record;
            this.message = message;
        }
    }

    public static final class TransientFailure extends SyncResult {
        public final Record record;
        public final String error;
        public final int retryCount;
        public TransientFailure(Record record, String error, int retryCount) {
            this.record = record;
            this.error = error;
            this.retryCount = retryCount;
        }
    }

    public static final class PermanentFailure extends SyncResult {
        public final Record record;
        public final String error;
        public PermanentFailure(Record record, String error) {
            this.record = record;
            this.error = error;
        }
    }
}
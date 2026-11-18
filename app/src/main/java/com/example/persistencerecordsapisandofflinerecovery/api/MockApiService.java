package com.example.persistencerecordsapisandofflinerecovery.api;

import com.example.persistencerecordsapisandofflinerecovery.model.Record;
import com.example.persistencerecordsapisandofflinerecovery.model.SyncStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mock API service for simulating network sync behavior.
 * 
 * This is a DETERMINISTIC MOCK - clearly labelled as such.
 * It simulates:
 * - Normal acknowledgement (success)
 * - Unavailable service (network error)
 * - Lost response (timeout - request succeeded but response lost)
 * - Conflict (server has different version)
 * 
 * Key design: Uses clientId for idempotency - same clientId = same operation.
 * This prevents duplicates on retry.
 */
public class MockApiService {

    // Configuration for deterministic behavior
    private static final AtomicInteger callCounter = new AtomicInteger(0);
    private static final Map<String, ServerRecord> serverRecords = new ConcurrentHashMap<>();
    private static final AtomicReference<MockMode> modeRef = new AtomicReference<>(MockMode.NORMAL);

    /**
     * Mock modes for testing different scenarios.
     * NORMAL: Success on first try
     * UNAVAILABLE: Always throws network exception
     * LOST_RESPONSE: First call succeeds but throws timeout; retry succeeds
     * CONFLICT: Returns conflict error (server has different data)
     */
    public enum MockMode {
        NORMAL("Normal - Success"),
        UNAVAILABLE("Service Unavailable"),
        LOST_RESPONSE("Lost Response (timeout)"),
        CONFLICT("Conflict - Server version differs");

        private final String displayName;

        MockMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Set the mock mode for testing.
     * @param mode The mock behavior to simulate
     */
    public static void setMockMode(MockMode mode) {
        modeRef.set(mode);
        callCounter.set(0);
    }

    public static MockMode getMockMode() {
        return modeRef.get();
    }

    /**
     * Server-side record representation.
     */
    private static class ServerRecord {
        final String serverId;
        final String clientId;
        final String title;
        final String description;
        final double quantity;
        final String unit;
        final int version;
        final long lastModified;

        ServerRecord(String serverId, String clientId, String title, String description,
                     double quantity, String unit, int version, long lastModified) {
            this.serverId = serverId;
            this.clientId = clientId;
            this.title = title;
            this.description = description;
            this.quantity = quantity;
            this.unit = unit;
            this.version = version;
            this.lastModified = lastModified;
        }
    }

    /**
     * Sync a record to the "server".
     * 
     * IDEMPOTENCY: Uses clientId to detect retries of the same operation.
     * If clientId exists on server, returns existing serverId (no duplicate).
     * 
     * @param record The record to sync
     * @return SyncResult with serverId and status
     * @throws ApiException for network errors, conflicts, etc.
     */
    public static SyncResult syncRecord(Record record) throws ApiException {
        MockMode mode = modeRef.get();
        int callNum = callCounter.incrementAndGet();

        // Simulate network latency
        try {
            Thread.sleep(500 + (long)(Math.random() * 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        switch (mode) {
            case NORMAL:
                return handleNormalSync(record);
            case UNAVAILABLE:
                throw new ApiException.NetworkUnavailable("Cannot reach sync service");
            case LOST_RESPONSE:
                return handleLostResponse(record, callNum);
            case CONFLICT:
                return handleConflict(record);
            default:
                throw new ApiException.ServerError("Unknown mock mode", 500);
        }
    }

    /**
     * Normal sync: First call creates record, subsequent calls with same clientId return existing.
     */
    private static SyncResult handleNormalSync(Record record) {
        ServerRecord existing = serverRecords.get(record.clientId);
        if (existing != null) {
            // Idempotent retry - return existing server record
            return new SyncResult(
                    existing.serverId,
                    SyncStatus.ACKNOWLEDGED,
                    "Already synced (idempotent retry)"
            );
        }

        // New record - create on server
        String serverId = "SRV-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
        ServerRecord serverRecord = new ServerRecord(
                serverId, record.clientId, record.title, record.description,
                record.quantity, record.unit, 1, System.currentTimeMillis()
        );
        serverRecords.put(record.clientId, serverRecord);

        return new SyncResult(serverId, SyncStatus.ACKNOWLEDGED, "Synced successfully");
    }

    /**
     * Lost response simulation:
     * - First call: Actually succeeds on server but throws timeout to client
     * - Retry (same clientId): Finds existing record, returns success
     * This demonstrates why clientId-based idempotency is critical.
     */
    private static SyncResult handleLostResponse(Record record, int callNum) throws ApiException.Timeout {
        ServerRecord existing = serverRecords.get(record.clientId);

        if (existing != null) {
            // Retry after lost response - server already has it
            return new SyncResult(
                    existing.serverId,
                    SyncStatus.ACKNOWLEDGED,
                    "Recovered after lost response (idempotent)"
            );
        }

        if (callNum == 1) {
            // First call: server processes but response is "lost"
            // We still create the server record to simulate reality
            String serverId = "SRV-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
            ServerRecord serverRecord = new ServerRecord(
                    serverId, record.clientId, record.title, record.description,
                    record.quantity, record.unit, 1, System.currentTimeMillis()
            );
            serverRecords.put(record.clientId, serverRecord);

            throw new ApiException.Timeout("Request timed out - response lost");
        }

        // Should not reach here with proper retry logic
        throw new ApiException.Timeout("Unexpected state in lost response simulation");
    }

    /**
     * Conflict simulation: Server has different version of the record.
     * This is NOT a transient failure - requires manual resolution.
     */
    private static SyncResult handleConflict(Record record) throws ApiException.Conflict {
        ServerRecord existing = serverRecords.get(record.clientId);

        if (existing != null) {
            // Simulate server having different data
            throw new ApiException.Conflict(
                    "Conflict: Server version differs. Server title: '" + existing.title + "', Local title: '" + record.title + "'",
                    existing.serverId
            );
        }

        // No existing record but mode is CONFLICT - simulate concurrent creation
        throw new ApiException.Conflict(
                "Conflict: Record created concurrently on server",
                "SRV-concurrent-" + System.currentTimeMillis()
        );
    }

    /**
     * Reset the mock server state (for testing).
     */
    public static void reset() {
        serverRecords.clear();
        callCounter.set(0);
        modeRef.set(MockMode.NORMAL);
    }

    /**
     * Result of a sync operation.
     */
    public static class SyncResult {
        public final String serverId;
        public final SyncStatus status;
        public final String message;

        public SyncResult(String serverId, SyncStatus status, String message) {
            this.serverId = serverId;
            this.status = status;
            this.message = message;
        }
    }

    /**
     * API exceptions with specific types for different failure modes.
     * Critical distinction: Transient (retryable) vs Permanent (non-retryable).
     */
    public static class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }

        public static class NetworkUnavailable extends ApiException {
            public NetworkUnavailable(String msg) { super(msg); }
        }

        public static class Timeout extends ApiException {
            public Timeout(String msg) { super(msg); }
        }

        public static class Conflict extends ApiException {
            public final String serverId;
            public Conflict(String msg, String serverId) {
                super(msg);
                this.serverId = serverId;
            }
        }

        public static class ServerError extends ApiException {
            public final int statusCode;
            public ServerError(String msg, int statusCode) {
                super(msg);
                this.statusCode = statusCode;
            }
        }

        public static class ValidationError extends ApiException {
            public final Map<String, String> errors;
            public ValidationError(String msg, Map<String, String> errors) {
                super(msg);
                this.errors = errors;
            }
        }
    }

    /**
     * Determines if an exception represents a transient (retryable) failure.
     * 
     * Transient: Network unavailable, timeout - safe to retry with same clientId
     * Permanent: Conflict, validation error, server error - NOT safe to retry blindly
     */
    public static boolean isTransient(Exception exception) {
        if (exception instanceof ApiException.NetworkUnavailable) return true;
        if (exception instanceof ApiException.Timeout) return true;
        if (exception instanceof ApiException.ServerError) {
            return ((ApiException.ServerError) exception).statusCode >= 500;
        }
        return false;
    }
}
# Persistent Records, APIs and Offline Recovery

**Lab 08 - JKUAT ICS 2300 | Consolidated Homeworks**

An Android application demonstrating persistent record storage with SQLite/Room, offline-first architecture, and robust sync/retry mechanisms with a mock API.

## 🎯 Lab Requirements Covered

This app implements all requirements from Lab 08:

1. **Local Structured Storage** - SQLite/Room for records with justification against Preferences/Files
2. **Save, List, Reopen, Correct** - Full CRUD with validation and transaction boundaries
3. **Mock API Sync** - Controlled mock service for sync experiments with explicit states
4. **Retry & Idempotency** - ClientID-based idempotent retries preventing duplicates
5. **Failure Classification** - Transient vs Permanent failures with proper handling

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      MainActivity (UI)                       │
│  - Form (create/edit)                                        │
│  - Record list with sync actions                             │
│  - Mock mode selector                                        │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      RecordViewModel                         │
│  - Form validation & error handling                          │
│  - Sync orchestration & retry logic                          │
│  - UI state management (LiveData)                            │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      RecordRepository                        │
│  - Sync state machine (SAVED→PENDING→ACK/FAILED/CONFLICT)   │
│  - Idempotent retry with ClientID                            │
│  - Transient vs Permanent failure classification            │
└─────────────────────────┬───────────────────────────────────┘
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
┌───────────────────────┐  ┌───────────────────────┐
│      Room Database    │  │     MockApiService    │
│  - RecordDao (CRUD)   │  │  - Normal sync        │
│  - LiveData queries   │  │  - Service unavailable│
│  - Type converters    │  │  - Lost response      │
└───────────────────────┘  │  - Conflict           │
                           └───────────────────────┘
```

## 📱 Features

### Record Management
- **Create** records with title, description, quantity, and unit
- **Validation** - Title required (≤100 chars), quantity ≥0, valid units
- **List** all records with color-coded sync status
- **Edit** existing records (resets sync status to SAVED)
- **Delete** with force option for acknowledged records

### Sync States (Explicit)
| Status | Color | Description |
|--------|-------|-------------|
| `SAVED` | 🟢 Green | Stored locally, not yet synced |
| `PENDING` | 🟠 Orange | Sync in progress |
| `ACKNOWLEDGED` | 🔵 Blue | Server confirmed |
| `FAILED` | 🔴 Red | Transient failure - safe to retry |
| `CONFLICT` | 🟣 Purple | Permanent failure - needs resolution |

### Mock API Modes (for Testing)
1. **Normal** - Success on first try
2. **Service Unavailable** - Network error (transient)
3. **Lost Response** - Timeout but server succeeded (tests idempotency)
4. **Conflict** - Server has different data (permanent)

### Key Technical Concepts Demonstrated

#### Idempotent Retry (Prevents Duplicates)
```java
// Each record gets a unique clientId on creation
clientId = UUID.randomUUID().toString();

// Same clientId on retry = same operation = no duplicate
Record record = dao.getByClientId(clientId); // Check before insert
```

#### Transaction Boundaries (No False Success)
```java
@Insert(onConflict = OnConflictStrategy.ABORT)
long insert(Record record); // Throws exception on failure - no false success

@Transaction
List<Long> insertAll(List<Record> records); // All succeed or all fail
```

#### Failure Classification
```java
// Transient (retry safe): Network unavailable, timeout
// Permanent (NOT retry safe): Conflict, validation error, 4xx/5xx server errors
public static boolean isTransient(Exception e) {
    return e instanceof NetworkUnavailable 
        || e instanceof Timeout
        || (e instanceof ServerError && statusCode >= 500);
}
```

## 🛠️ Tech Stack

- **Language**: Java 17
- **Database**: Room 2.6.1 (SQLite)
- **Architecture**: MVVM with LiveData
- **UI**: Material 3 Components, RecyclerView, ViewBinding
- **Async**: ExecutorService (Java threads)
- **Build**: Gradle 9.5, AGP 9.3.3

## 📦 Project Structure

```
app/src/main/java/com/example/persistencerecordsapisandofflinerecovery/
├── MainActivity.java              # UI controller
├── viewmodel/
│   └── RecordViewModel.java       # Business logic & UI state
├── repository/
│   ├── RecordRepository.java      # Data orchestration & sync
│   ├── SyncState.java             # Sync state events
│   ├── SyncResult.java            # Sync outcome types
│   └── ValidationException.java   # Form validation errors
├── data/
│   ├── AppDatabase.java           # Room database
│   └── RecordDao.java             # Database operations
├── model/
│   ├── Record.java                # Entity with copyWith()
│   ├── SyncStatus.java            # Enum: SAVED/PENDING/ACK/FAILED/CONFLICT
│   ├── ValidationResult.java      # Sealed validation result
│   └── RecordValidator.java       # Business rules
├── api/
│   └── MockApiService.java        # Deterministic mock API
└── ui/
    └── RecordAdapter.java         # RecyclerView adapter
```

## 🚀 Getting Started

### Prerequisites
- Android Studio Koala (2024.1.1) or later
- JDK 17
- Android SDK 34+

### Build & Run
```bash
# Clone the repository
git clone https://github.com/ai-patrick/persitent-records-offline-recovery.git
cd persitent-records-offline-recovery

# Build debug APK
./gradlew assembleDebug

# Install on device/emulator
./gradlew installDebug
```

Or open in Android Studio and click **Run**.

## 🧪 Testing Sync Scenarios

1. **Normal Sync**: 
   - Create records → Click "Sync All Pending" → All become ACKNOWLEDGED

2. **Service Unavailable**:
   - Select "Service Unavailable" mode
   - Try sync → Records become FAILED (transient)
   - Switch to "Normal" mode → Retry → Success

3. **Lost Response**:
   - Select "Lost Response" mode
   - Sync a record → Shows timeout error
   - Click "Retry" → Recovers using same ClientID → Success

4. **Conflict**:
   - Select "Conflict" mode
   - Sync → Shows CONFLICT (permanent)
   - Edit record to resolve → Sync again

## 📝 Design Decisions

### Why Room over SharedPreferences/Files?
| Aspect | Room/SQLite | SharedPreferences | Files |
|--------|-------------|-------------------|-------|
| Structured data | ✅ Tables, types | ❌ Key-value only | ❌ Manual parsing |
| Queries | ✅ SQL, LiveData | ❌ None | ❌ Manual |
| Transactions | ✅ ACID | ❌ No | ❌ Manual |
| Migrations | ✅ Versioned | ❌ Manual | ❌ Manual |
| Type safety | ✅ Compile-time | ❌ Runtime | ❌ Runtime |
| Concurrency | ✅ Built-in | ⚠️ Limited | ❌ Manual |

### Why ClientID for Idempotency?
- **Server-generated IDs** are unreliable for retry (lost response = no server ID)
- **Client-generated UUID** travels with request, enables deduplication
- **Same ClientID = Same Operation** - fundamental idempotency principle

### Why Explicit Sync States?
- `SAVED` ≠ `PENDING` ≠ `ACKNOWLEDGED` - each has different user actions
- `FAILED` vs `CONFLICT` require fundamentally different user responses
- UI can show precise status and appropriate actions

## 📚 Lab Evidence Mapping

| Lab Check | Implementation |
|-----------|----------------|
| Save 3 records offline; close and reopen | Room persists across process death |
| Rejected input preserves draft | ValidationException + form error UI |
| Retry without duplicate | ClientID-based idempotency in MockApiService |
| Malformed ack not shown as success | ApiException.Conflict separate from transient |

## 👨‍💻 Author

Developed for **JKUAT ICS 2300** - Lab 08: Persistent Records, APIs and Offline Recovery

## 📄 License

Educational use - JKUAT coursework
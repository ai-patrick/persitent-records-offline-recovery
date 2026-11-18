package com.example.persistencerecordsapisandofflinerecovery.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.persistencerecordsapisandofflinerecovery.model.Record;
import com.example.persistencerecordsapisandofflinerecovery.model.SyncStatus;

/**
 * Room database for persistent records.
 * 
 * Design justification for Room/SQLite over SharedPreferences or Files:
 * 
 * 1. STRUCTURED DATA: Records have multiple typed fields (title, quantity, unit, status)
 *    - Preferences only supports primitive key-value pairs
 *    - Files require manual serialization/parsing
 * 
 * 2. QUERY CAPABILITIES: Need to query by status, clientId, date ranges
 *    - Room provides compile-time verified SQL queries
 *    - LiveData/Flow for reactive UI updates
 * 
 * 3. TRANSACTION SUPPORT: Atomic operations for sync state transitions
 *    - Critical for "no false success" guarantee
 *    - Preferences has no transaction support
 * 
 * 4. MIGRATION PATH: Schema evolution as app grows
 *    - Room handles migrations safely
 *    - File/Preferences migrations are manual and error-prone
 * 
 * 5. TYPE SAFETY: Compile-time verification of queries
 *    - Reduces runtime crashes from schema mismatches
 * 
 * 6. CONCURRENCY: Built-in thread safety for multi-threaded access
 *    - Sync operations run on background threads
 *    - UI observes on main thread
 */
@Database(entities = {Record.class}, version = 1, exportSchema = false)
@TypeConverters(AppDatabase.Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract RecordDao recordDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "records_database"
                    )
                            .fallbackToDestructiveMigration() // For development; use migrations in production
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Type converters for Room to handle custom types.
     */
    public static class Converters {
        @androidx.room.TypeConverter
        public static String fromSyncStatus(SyncStatus value) {
            return value != null ? value.name() : null;
        }

        @androidx.room.TypeConverter
        public static SyncStatus toSyncStatus(String value) {
            return value != null ? SyncStatus.valueOf(value) : null;
        }
    }
}
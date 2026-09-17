package com.acrylic.m71demo.data;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteDatabase;

/** Android 9+ pool configuration: FULL applies to every newly opened SQLite connection. */
public final class FullSyncOpenHelper implements SupportSQLiteOpenHelper {
    private final SQLiteOpenHelper helper;
    private SQLiteDatabase connection;
    private FrameworkSQLiteDatabase adapter;

    public FullSyncOpenHelper(Configuration configuration) {
        SQLiteDatabase.OpenParams parameters = new SQLiteDatabase.OpenParams.Builder()
                .addOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
                .setJournalMode(SQLiteDatabase.JOURNAL_MODE_WAL).setSynchronousMode(SQLiteDatabase.SYNC_MODE_FULL)
                .setErrorHandler(db -> {
                    throw new android.database.sqlite.SQLiteDatabaseCorruptException(
                            "数据库损坏：保留原文件，停止作业并提取证据；不自动删除或重建");
                }).build();
        helper = new SQLiteOpenHelper(configuration.context, configuration.name,
                configuration.callback.version, parameters) {
            @Override public void onConfigure(SQLiteDatabase db) { configuration.callback.onConfigure(wrap(db)); }
            @Override public void onCreate(SQLiteDatabase db) { configuration.callback.onCreate(wrap(db)); }
            @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                configuration.callback.onUpgrade(wrap(db), oldVersion, newVersion);
            }
            @Override public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                configuration.callback.onDowngrade(wrap(db), oldVersion, newVersion);
            }
            @Override public void onOpen(SQLiteDatabase db) { configuration.callback.onOpen(wrap(db)); }
        };
    }

    // AndroidX's adapter is Kotlin-internal but its JVM constructor is public. This Java bridge
    // intentionally pins sqlite-framework 2.5.0; library upgrades must rerun migration/pool tests.
    private synchronized FrameworkSQLiteDatabase wrap(SQLiteDatabase db) {
        if (connection != db) { connection = db; adapter = new FrameworkSQLiteDatabase(db); }
        return adapter;
    }

    @Override public String getDatabaseName() { return helper.getDatabaseName(); }
    @Override public void setWriteAheadLoggingEnabled(boolean enabled) { helper.setWriteAheadLoggingEnabled(enabled); }
    @Override public SupportSQLiteDatabase getWritableDatabase() { return wrap(helper.getWritableDatabase()); }
    @Override public SupportSQLiteDatabase getReadableDatabase() { return wrap(helper.getReadableDatabase()); }
    @Override public void close() { helper.close(); }
}

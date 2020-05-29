package com.plutonem.datasets;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.plutonem.Plutonem;

import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

/**
 * database for all nemur information
 */
public class NemurDatabase extends SQLiteOpenHelper {
    protected static final String DB_NAME = "pnnemur.db";
    private static final int DB_VERSION = 4;
    private static final int DB_LAST_VERSION_WITHOUT_MIGRATION_SCRIPT = 3; // do not change this value

    /*
     * version history
     * 1 - add NemurTagTable, NemurOrderTable, NemurBuyerTable, NemurSearchTable
     * 2 - rename featured_video to item_descriptive_video_main in NemurOrderTable
     * 3 - add item_descriptive_video_affiliated to NemurOrderTable
     * 4 - still decide what to do now
     */

    /*
     * database singleton
     */
    private static NemurDatabase mNemurDb;
    private static final Object DB_LOCK = new Object();

    public static NemurDatabase getDatabase() {
        if (mNemurDb == null) {
            synchronized (DB_LOCK) {
                if (mNemurDb == null) {
                    mNemurDb = new NemurDatabase(Plutonem.getContext());
                    // this ensures that onOpen() is called with a writable database
                    // (open will fail if app calls getReadableDb() first)
                    mNemurDb.getWritableDatabase();
                }
            }
        }
        return mNemurDb;
    }

    public static SQLiteDatabase getReadableDb() {
        return getDatabase().getReadableDatabase();
    }

    public static SQLiteDatabase getWritableDb() {
        return getDatabase().getWritableDatabase();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // copyDatabase(db);
        // getDatabase().reset(db);
    }

    public NemurDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAllTables(db);
    }

    @SuppressWarnings({"FallThrough"})
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        AppLog.i(T.NEMUR,
                "Upgrading database from version " + oldVersion + " to version " + newVersion + " IN PROGRESS");
        int currentVersion = oldVersion;
        if (currentVersion <= DB_LAST_VERSION_WITHOUT_MIGRATION_SCRIPT) {
            // versions 0 - 3 didn't support migration scripts, so we can safely drop and recreate all tables
            reset(db);
            currentVersion = DB_LAST_VERSION_WITHOUT_MIGRATION_SCRIPT;
        }

        switch (currentVersion) {
            case 3:
                // no-op
                currentVersion++;
        }
        if (currentVersion != newVersion) {
            throw new RuntimeException(
                    "Migration from version " + oldVersion + " to version " + newVersion + " FAILED. ");
        }
        AppLog.i(T.NEMUR,
                "Upgrading database from version " + oldVersion + " to version " + newVersion + " SUCCEEDED");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // IMPORTANT: do NOT call super() here - doing so throws a SQLiteException
        AppLog.w(T.NEMUR, "Downgrading database from version " + oldVersion + " to version " + newVersion);
        reset(db);
    }

    private void createAllTables(SQLiteDatabase db) {
        NemurOrderTable.createTables(db);
        NemurTagTable.createTables(db);
        NemurBuyerTable.createTables(db);
        NemurSearchTable.createTables(db);
    }

    private void dropAllTables(SQLiteDatabase db) {
        NemurOrderTable.dropTables(db);
        NemurTagTable.dropTables(db);
        NemurBuyerTable.dropTables(db);
        NemurSearchTable.dropTables(db);
    }

    /*
     * drop & recreate all tables (essentially clears the db of all data)
     */
    private void reset(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            dropAllTables(db);
            createAllTables(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /*
     * purge older/unattached data - use purgeAsync() to do this in the background
     */
    private static void purge() {
        SQLiteDatabase db = getWritableDb();
        db.beginTransaction();
        try {
            NemurOrderTable.purge(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static void purgeAsync() {
        new Thread() {
            @Override
            public void run() {
                purge();
            }
        }.start();
    }
}

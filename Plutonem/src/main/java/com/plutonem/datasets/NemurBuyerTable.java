package com.plutonem.datasets;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import com.plutonem.models.NemurBuyer;
import com.plutonem.ui.nemur.NemurConstants;

import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.SqlUtils;

import java.util.Date;

/**
 * tbl_buyer_info contains information about buyers viewed in the nemur.
 * Note that this table is populated from one endpoint:
 * <p>
 * 1. buyers/{$buyerId}
 * <p>
 * The endpoint is called when the user views buyer preview
 */
public class NemurBuyerTable {
    protected static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tbl_buyer_info ("
                + " buyer_id INTEGER DEFAULT 0,"
                + " name TEXT,"
                + " description TEXT,"
                + " num_acusers INTEGER DEFAULT 0,"
                + " is_notifications_enabled INTEGER DEFAULT 0,"
                + " date_updated TEXT,"
                + " PRIMARY KEY (buyer_id)"
                + ")");
    }

    protected static void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS tbl_buyer_info");
    }

    public static NemurBuyer getBuyerInfo(long buyerId) {
        if (buyerId == 0) {
            return null;
        }
        String[] args = {Long.toString(buyerId)};
        Cursor cursor = NemurDatabase.getReadableDb().rawQuery("SELECT * FROM tbl_buyer_info WHERE buyer_id=?", args);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return getBuyerInfoFromCursor(cursor);
        } finally {
            SqlUtils.closeCursor(cursor);
        }
    }

    private static NemurBuyer getBuyerInfoFromCursor(Cursor c) {
        if (c == null) {
            return null;
        }

        NemurBuyer buyerInfo = new NemurBuyer();
        buyerInfo.buyerId = c.getLong(c.getColumnIndex("buyer_id"));
        buyerInfo.setName(c.getString(c.getColumnIndex("name")));
        buyerInfo.setDescription(c.getString(c.getColumnIndex("description")));
        buyerInfo.isNotificationsEnabled = SqlUtils.sqlToBool(c.getInt(c.getColumnIndex("is_notifications_enabled")));
        buyerInfo.numActiveUsers = c.getInt(c.getColumnIndex("num_acusers"));

        return buyerInfo;
    }

    public static void addOrUpdateBuyer(NemurBuyer buyerInfo) {
        if (buyerInfo == null) {
            return;
        }
        String sql = "INSERT OR REPLACE INTO tbl_buyer_info"
                + " (buyer_id, name, description, "
                + "  is_notifications_enabled, num_acusers, date_updated)"
                + " VALUES (?1, ?2, ?3, ?4, ?5, ?6)";
        SQLiteStatement stmt = NemurDatabase.getWritableDb().compileStatement(sql);
        try {
            stmt.bindLong(1, buyerInfo.buyerId);
            stmt.bindString(2, buyerInfo.getName());
            stmt.bindString(3, buyerInfo.getDescription());
            stmt.bindLong(4, SqlUtils.boolToSql(buyerInfo.isNotificationsEnabled));
            stmt.bindLong(5, buyerInfo.numActiveUsers);
            stmt.bindString(6, DateTimeUtils.iso8601FromDate(new Date()));
            stmt.execute();
        } finally {
            SqlUtils.closeStatement(stmt);
        }
    }

    /*
     * determine whether the passed buyer info should be updated based on when it was last updated
     */
    public static boolean isTimeToUpdateBuyerInfo(NemurBuyer buyerInfo) {
        int minutes = minutesSinceLastUpdate(buyerInfo);
        if (minutes == NEVER_UPDATED) {
            return true;
        }
        return (minutes >= NemurConstants.NEMUR_AUTO_UPDATE_DELAY_MINUTES);
    }

    private static String getBuyerInfoLastUpdated(NemurBuyer buyerInfo) {
        if (buyerInfo == null || buyerInfo.buyerId == 0) {
            return "";
        }
        String[] args = {Long.toString(buyerInfo.buyerId)};
        return SqlUtils.stringForQuery(NemurDatabase.getReadableDb(),
                "SELECT date_updated FROM tbl_buyer_info WHERE buyer_id=?",
                args);
    }

    private static final int NEVER_UPDATED = -1;

    private static int minutesSinceLastUpdate(NemurBuyer buyerInfo) {
        if (buyerInfo == null) {
            return 0;
        }

        String updated = getBuyerInfoLastUpdated(buyerInfo);
        if (TextUtils.isEmpty(updated)) {
            return NEVER_UPDATED;
        }

        Date dtUpdated = DateTimeUtils.dateFromIso8601(updated);
        if (dtUpdated == null) {
            return 0;
        }

        Date dtNow = new Date();
        return DateTimeUtils.minutesBetween(dtUpdated, dtNow);
    }
}

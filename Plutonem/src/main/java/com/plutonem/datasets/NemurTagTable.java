package com.plutonem.datasets;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import com.plutonem.models.NemurTag;
import com.plutonem.models.NemurTagList;
import com.plutonem.models.NemurTagType;
import com.plutonem.ui.nemur.NemurConstants;

import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.SqlUtils;

import java.util.Date;

/**
 * tbl_tags stores the list of tags the user subscribed to or has by default
 */
public class NemurTagTable {
    protected static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tbl_tags ("
                + " tag_slug TEXT COLLATE NOCASE,"
                + " tag_display_name TEXT COLLATE NOCASE,"
                + " tag_title TEXT COLLATE NOCASE,"
                + " tag_type INTEGER DEFAULT 0,"
                + " endpoint TEXT,"
                + " date_updated TEXT,"
                + " PRIMARY KEY (tag_slug, tag_type)"
                + ")");
    }

    protected static void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS tbl_tags");
    }

    /*
     * replaces all tags with the passed list
     */
    public static void replaceTags(NemurTagList tags) {
        if (tags == null || tags.size() == 0) {
            return;
        }

        SQLiteDatabase db = NemurDatabase.getWritableDb();
        db.beginTransaction();
        try {
            try {
                // first delete all existing tags, then insert the passed ones
                db.execSQL("DELETE FROM tbl_tags");
                addOrUpdateTags(tags);
                db.setTransactionSuccessful();
            } catch (SQLException e) {
                AppLog.e(T.NEMUR, e);
            }
        } finally {
            db.endTransaction();
        }
    }

    private static void addOrUpdateTags(NemurTagList tagList) {
        if (tagList == null || tagList.size() == 0) {
            return;
        }
        SQLiteStatement stmt = null;
        try {
            stmt = NemurDatabase.getWritableDb().compileStatement(
                    "INSERT OR REPLACE INTO tbl_tags (tag_slug, tag_display_name, tag_title, tag_type, endpoint) "
                            + "VALUES (?1,?2,?3,?4,?5)");

            for (NemurTag tag : tagList) {
                stmt.bindString(1, tag.getTagSlug());
                stmt.bindString(2, tag.getTagDisplayName());
                stmt.bindString(3, tag.getTagTitle());
                stmt.bindLong(4, tag.tagType.toInt());
                stmt.bindString(5, tag.getEndpoint());
                stmt.execute();
            }
        } finally {
            SqlUtils.closeStatement(stmt);
        }
    }

    /*
     * returns true if the passed tag exists, regardless of type
     */
    public static boolean tagExists(NemurTag tag) {
        if (tag == null) {
            return false;
        }
        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        return SqlUtils.boolForQuery(NemurDatabase.getReadableDb(),
                "SELECT 1 FROM tbl_tags WHERE tag_slug=?1 AND tag_type=?2",
                args);
    }

    private static NemurTag getTagFromCursor(Cursor c) {
        if (c == null) {
            throw new IllegalArgumentException("null tag cursor");
        }

        String tagSlug = c.getString(c.getColumnIndex("tag_slug"));
        String tagDisplayName = c.getString(c.getColumnIndex("tag_display_name"));
        String tagTitle = c.getString(c.getColumnIndex("tag_title"));
        String endpoint = c.getString(c.getColumnIndex("endpoint"));
        NemurTagType tagType = NemurTagType.fromInt(c.getInt(c.getColumnIndex("tag_type")));

        return new NemurTag(tagSlug, tagDisplayName, tagTitle, endpoint, tagType);
    }

    public static NemurTag getTag(String tagSlug, NemurTagType tagType) {
        if (TextUtils.isEmpty(tagSlug)) {
            return null;
        }

        String[] args = {tagSlug, Integer.toString(tagType.toInt())};
        Cursor c = NemurDatabase.getReadableDb()
                .rawQuery("SELECT * FROM tbl_tags WHERE tag_slug=? AND tag_type=? LIMIT 1", args);
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            return getTagFromCursor(c);
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public static NemurTag getTagFromEndpoint(String endpoint) {
        if (TextUtils.isEmpty(endpoint)) {
            return null;
        }

        String[] args = {"%" + endpoint};
        String query = "SELECT * FROM tbl_tags WHERE endpoint LIKE ? LIMIT 1";
        Cursor cursor = NemurDatabase.getReadableDb().rawQuery(query, args);

        try {
            return cursor.moveToFirst() ? getTagFromCursor(cursor) : null;
        } finally {
            SqlUtils.closeCursor(cursor);
        }
    }

    public static String getEndpointForTag(NemurTag tag) {
        if (tag == null) {
            return null;
        }
        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        return SqlUtils.stringForQuery(NemurDatabase.getReadableDb(),
                "SELECT endpoint FROM tbl_tags WHERE tag_slug=? AND tag_type=?",
                args);
    }

    public static NemurTagList getDefaultTags() {
        return getTagsOfType(NemurTagType.DEFAULT);
    }

    private static NemurTagList getTagsOfType(NemurTagType tagType) {
        String[] args = {Integer.toString(tagType.toInt())};
        Cursor c = NemurDatabase.getReadableDb()
                .rawQuery("SELECT * FROM tbl_tags WHERE tag_type=? ORDER BY tag_slug", args);
        try {
            NemurTagList tagList = new NemurTagList();
            if (c.moveToFirst()) {
                do {
                    tagList.add(getTagFromCursor(c));
                } while (c.moveToNext());
            }
            return tagList;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    static NemurTagList getAllTags() {
        Cursor c = NemurDatabase.getReadableDb().rawQuery("SELECT * FROM tbl_tags ORDER BY tag_slug", null);
        try {
            NemurTagList tagList = new NemurTagList();
            if (c.moveToFirst()) {
                do {
                    tagList.add(getTagFromCursor(c));
                } while (c.moveToNext());
            }
            return tagList;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public static NemurTag getFirstTag() {
        Cursor c = NemurDatabase.getReadableDb().rawQuery("SELECT * FROM tbl_tags ORDER BY tag_slug LIMIT 1", null);
        try {
            if (c.moveToFirst()) {
                return getTagFromCursor(c);
            }
            return null;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public static void deleteTag(NemurTag tag) {
        if (tag == null) {
            return;
        }
        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        NemurDatabase.getWritableDb().delete("tbl_tags", "tag_slug=? AND tag_type=?", args);
    }

    public static String getTagLastUpdated(NemurTag tag) {
        if (tag == null) {
            return "";
        }
        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        return SqlUtils.stringForQuery(NemurDatabase.getReadableDb(),
                                        "SELECT date_updated FROM tbl_tags WHERE tag_slug=? AND tag_type=?",
                                        args);
    }

    public static void setTagLastUpdated(NemurTag tag) {
        if (tag == null) {
            return;
        }

        String date = DateTimeUtils.iso8601FromDate(new Date());
        String sql = "UPDATE tbl_tags SET date_updated=?1 WHERE tag_slug=?2 AND tag_type=?3";
        SQLiteStatement stmt = NemurDatabase.getWritableDb().compileStatement(sql);
        try {
            stmt.bindString(1, date);
            stmt.bindString(2, tag.getTagSlug());
            stmt.bindLong(3, tag.tagType.toInt());
            stmt.execute();
        } finally {
            SqlUtils.closeStatement(stmt);
        }
    }

    /*
     * determine whether the passed tag should be auto-updated based on when it was last updated
     */
    public static boolean shouldAutoUpdateTag(NemurTag tag) {
        int minutes = minutesSinceLastUpdate(tag);
        if (minutes == NEVER_UPDATED) {
            return true;
        }
        return (minutes >= NemurConstants.NEMUR_AUTO_UPDATE_DELAY_MINUTES);
    }

    private static final int NEVER_UPDATED = -1;

    private static int minutesSinceLastUpdate(NemurTag tag) {
        if (tag == null) {
            return 0;
        }

        String updated = getTagLastUpdated(tag);
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

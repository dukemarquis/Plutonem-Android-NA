package com.plutonem.datasets;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.plutonem.models.NemurCardType;
import com.plutonem.models.NemurOrder;
import com.plutonem.models.NemurOrderList;
import com.plutonem.models.NemurTag;
import com.plutonem.models.NemurTagList;
import com.plutonem.ui.nemur.NemurConstants;
import com.plutonem.ui.nemur.actions.NemurActions;
import com.plutonem.ui.nemur.models.NemurBuyerIdOrderId;
import com.plutonem.ui.nemur.models.NemurBuyerIdOrderIdList;

import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.SqlUtils;

import java.util.Locale;

/**
 * tbl_orders contains all nemur orders - the primary key is pseudo_id + tag_name + tag_type,
 * which allows the same order to appear in multiple streams.
 */
public class NemurOrderTable {
    private static final String COLUMN_NAMES =
            "order_id," // 1
            + "buyer_id," // 2
            + "pseudo_id," // 3
            + "account_name," // 4
            + "account_id," // 5
            + "title," // 6
            + "price," // 7
            + "item_distribution_mode," // 8
            + "buyer_name," // 9
            + "featured_image," // 10
            + "featured_video," // 11
            + "date_published," // 12
            + "tag_name," // 13
            + "tag_type," // 14
            + "has_gap_marker," // 15
            + "card_type"; // 16

    protected static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tbl_orders ("
                + " order_id INTEGER DEFAULT 0,"
                + " buyer_id INTEGER DEFAULT 0,"
                + " pseudo_id TEXT NOT NULL,"
                + " account_name TEXT,"
                + " account_id INTEGER DEFAULT 0,"
                + " title  TEXT,"
                + " price TEXT,"
                + " item_distribution_mode TEXT,"
                + " buyer_name TEXT,"
                + " featured_image TEXT,"
                + " featured_video TEXT,"
                + " date_published TEXT,"
                + " tag_name TEXT NOT NULL COLLATE NOCASE,"
                + " tag_type INTEGER DEFAULT 0,"
                + " has_gap_marker INTEGER DEFAULT 0,"
                + " card_type TEXT,"
                + " PRIMARY KEY (pseudo_id, tag_name, tag_type)"
                + ")");

        db.execSQL("CREATE INDEX idx_orders_order_id_buyer_id ON tbl_orders(order_id, buyer_id)");
        db.execSQL("CREATE INDEX idx_orders_date_published ON tbl_orders(date_published)");
        db.execSQL("CREATE INDEX idx_orders_tag_name ON tbl_orders(tag_name)");
    }

    protected static void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS tbl_orders");
    }

    protected static void reset(SQLiteDatabase db) {
        dropTables(db);
        createTables(db);
    }

    /*
     * purge table of unattached/older orders - no need to wrap this in a transaction since it's
     * only called from NemurDatabase.purge() which already creates a transaction
     */
    protected static int purge(SQLiteDatabase db) {
        // delete orders attached to tags that no longer exist
        int numDeleted = db.delete("tbl_orders", "tag_name NOT IN (SELECT DISTINCT tag_name FROM tbl_tags)", null);

        // delete excess posts on a per-tag basis
        NemurTagList tags = NemurTagTable.getAllTags();
        for (NemurTag tag : tags) {
            numDeleted += purgeOrdersForTag(db, tag);
        }

        return numDeleted;
    }

    /*
     * purge excess orders in the passed tag
     */
    private static final int MAX_ORDERS_PER_TAG = NemurConstants.NEMUR_MAX_ORDERS_TO_DISPLAY;

    private static int purgeOrdersForTag(SQLiteDatabase db, NemurTag tag) {
        int numPosts = getNumOrdersWithTag(tag);
        if (numPosts <= MAX_ORDERS_PER_TAG) {
            return 0;
        }
        String tagName = tag.getTagSlug();
        String tagType = Integer.toString(tag.tagType.toInt());
        String[] args = {tagName, tagType, tagName, tagType, Integer.toString(MAX_ORDERS_PER_TAG)};
        String where = "tag_name=? AND tag_type=? AND pseudo_id NOT IN (SELECT DISTINCT pseudo_id FROM tbl_orders WHERE "
                + "tag_name=? AND tag_type=? ORDER BY " + getSortColumnForTag(tag) + " DESC LIMIT ?)";
        int numDeleted = db.delete("tbl_orders", where, args);
        AppLog.d(AppLog.T.NEMUR,
                String.format(Locale.ENGLISH, "nemur order table > purged %d orders in tag %s", numDeleted,
                        tag.getTagNameForLog()));
        return numDeleted;
    }

    public static int getNumOrdersWithTag(NemurTag tag) {
        if (tag == null) {
            return 0;
        }
        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        return SqlUtils.intForQuery(NemurDatabase.getReadableDb(),
                                    "SELECT count(*) FROM tbl_orders WHERE tag_name=? AND tag_type=?",
                                    args);
    }

    public static void updateOrder(@NonNull NemurOrder order) {
        // we need to update a few important fields across all instances of this order - this is
        // necessary because a order can exist multiple times in the table with different tags
        ContentValues values = new ContentValues();
        values.put("title", order.getTitle());
        values.put("price", order.getPrice());
        values.put("item_distribution_mode", order.getItemDistributionMode());
        values.put("featured_image", order.getFeaturedImage());
        values.put("featured_video", order.getFeaturedVideo());
        NemurDatabase.getWritableDb().update(
                "tbl_orders", values, "pseudo_id=?", new String[]{order.getPseudoId()});
        NemurOrderList orders = new NemurOrderList();
        orders.add(order);
        addOrUpdateOrders(null, orders);
    }

    public static void addOrder(@NonNull NemurOrder order) {
        NemurOrderList orders = new NemurOrderList();
        orders.add(order);
        addOrUpdateOrders(null, orders);
    }

    public static NemurOrder getBuyerOrder(long buyerId, long orderId) {
        return getOrder("buyer_id=? AND order_id=?", new String[]{Long.toString(buyerId), Long.toString(orderId)});
    }

    private static NemurOrder getOrder(String where, String[] args) {
        String sql = "SELECT * FROM tbl_orders WHERE " + where + " LIMIT 1";

        Cursor c = NemurDatabase.getReadableDb().rawQuery(sql, args);
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            return getOrderFromCursor(c);
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public static String getOrderTitle(long buyerId, long orderId) {
        String[] args = {Long.toString(buyerId), Long.toString(orderId)};
        return SqlUtils.stringForQuery(NemurDatabase.getReadableDb(),
                "SELECT title FROM tbl_orders WHERE buyer_id=? AND order_id=?",
                args);
    }

    private static boolean orderExistsForNemurTag(long buyerId, long orderId, NemurTag nemurTag) {
        String[] args = {Long.toString(buyerId), Long.toString(orderId), nemurTag.getTagSlug(),
                Integer.toString(nemurTag.tagType.toInt())};
        return SqlUtils.boolForQuery(NemurDatabase.getReadableDb(),
                "SELECT 1 FROM tbl_orders WHERE buyer_id=? AND order_id=? AND tag_name=? AND tag_type=?",
                args);
    }

    /*
     * returns whether any of the passed orders are new or changed - used after orders are retrieved
     */
    public static NemurActions.UpdateResult compareOrders(NemurOrderList orders) {
        if (orders == null || orders.size() == 0) {
            return NemurActions.UpdateResult.UNCHANGED;
        }

        boolean hasChanges = false;
        for (NemurOrder order : orders) {
            NemurOrder existingOrder = getBuyerOrder(order.buyerId, order.orderId);
            if (existingOrder == null) {
                return NemurActions.UpdateResult.HAS_NEW;
            } else if (!hasChanges && !order.isSameOrder(existingOrder)) {
                hasChanges = true;
            }
        }

        return (hasChanges ? NemurActions.UpdateResult.CHANGED : NemurActions.UpdateResult.UNCHANGED);
    }

    /*
     * returns true if any orders in the passed list exist in this list for the given tag
     */
    public static boolean hasOverlap(NemurOrderList orders, NemurTag tag) {
        for (NemurOrder order : orders) {
            if (orderExistsForNemurTag(order.buyerId, order.orderId, tag)) {
                return true;
            }
        }
        return false;
    }

    public static int deleteOrdersWithTag(final NemurTag tag) {
        if (tag == null) {
            return 0;
        }

        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        return NemurDatabase.getWritableDb().delete(
                "tbl_orders",
                "tag_name=? AND tag_type=?",
                args);
    }

    /*
     * returns the iso8601 date of the oldest order with the passed tag
     */
    public static String getOldestDateWithTag(final NemurTag tag) {
        if (tag == null) {
            return "";
        }

        // date field depends on the tag
        String dateColumn = getSortColumnForTag(tag);
        String sql = "SELECT " + dateColumn + " FROM tbl_orders"
                + " WHERE tag_name=? AND tag_type=?"
                + " ORDER BY " + dateColumn + " LIMIT 1";
        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        return SqlUtils.stringForQuery(NemurDatabase.getReadableDb(), sql, args);
    }

    public static void removeGapMarkerForTag(final NemurTag tag) {
        if (tag == null) {
            return;
        }

        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        String sql = "UPDATE tbl_orders SET has_gap_marker=0 WHERE has_gap_marker!=0 AND tag_name=? AND tag_type=?";
        NemurDatabase.getWritableDb().execSQL(sql, args);
    }

    /*
     * returns the buyerId/orderId of the order with the passed tag that has a gap marker, or null if none exists
     */
    public static NemurBuyerIdOrderId getGapMarkerIdsForTag(final NemurTag tag) {
        if (tag == null) {
            return null;
        }

        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        String sql = "SELECT buyer_id, order_id FROM tbl_orders WHERE has_gap_marker!=0 AND tag_name=? AND tag_type=?";
        Cursor cursor = NemurDatabase.getReadableDb().rawQuery(sql, args);
        try {
            if (cursor.moveToFirst()) {
                long buyerId = cursor.getLong(0);
                long orderId = cursor.getLong(1);
                return new NemurBuyerIdOrderId(buyerId, orderId);
            } else {
                return null;
            }
        } finally {
            SqlUtils.closeCursor(cursor);
        }
    }

    public static void setGapMarkerForTag(long buyerId, long orderId, NemurTag tag) {
        if (tag == null) {
            return;
        }

        String[] args = {
                Long.toString(buyerId),
                Long.toString(orderId),
                tag.getTagSlug(),
                Integer.toString(tag.tagType.toInt())
        };
        String sql =
                "UPDATE tbl_orders SET has_gap_marker=1 WHERE buyer_id=? AND order_id=? AND tag_name=? AND tag_type=?";
        NemurDatabase.getWritableDb().execSQL(sql, args);
    }

    public static String getGapMarkerDateForTag(NemurTag tag) {
        NemurBuyerIdOrderId ids = getGapMarkerIdsForTag(tag);
        if (ids == null) {
            return null;
        }

        String dateColumn = getSortColumnForTag(tag);
        String[] args = {Long.toString(ids.getBuyerId()), Long.toString(ids.getOrderId())};
        String sql = "SELECT " + dateColumn + " FROM tbl_orders WHERE buyer_id=? AND order_id=?";
        return SqlUtils.stringForQuery(NemurDatabase.getReadableDb(), sql, args);
    }

    /*
     * the column orders are sorted by depends on the type of tag stream being displayed:
     */
    private static String getSortColumnForTag(NemurTag tag) {
        return "date_published";
    }

    /*
     * delete orders with the passed tag that come before the one with the gap marker for
     * this tag - note this may leave some stray orders in tbl_orders, but these will
     * be cleaned up by the next purge
     */
    public static void deleteOrdersBeforeGapMarkerForTag(NemurTag tag) {
        String gapMarkerDate = getGapMarkerDateForTag(tag);
        if (TextUtils.isEmpty(gapMarkerDate)) {
            return;
        }

        String dateColumn = getSortColumnForTag(tag);
        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt()), gapMarkerDate};
        String where = "tag_name=? AND tag_type=? AND " + dateColumn + " < ?";
        int numDeleted = NemurDatabase.getWritableDb().delete("tbl_orders", where, args);
        if (numDeleted > 0) {
            AppLog.d(AppLog.T.NEMUR, "removed " + numDeleted + " orders older than gap marker");
        }
    }

    public static void addOrUpdateOrders(final NemurTag tag, NemurOrderList orders) {
        if (orders == null || orders.size() == 0) {
            return;
        }

        SQLiteDatabase db = NemurDatabase.getWritableDb();
        SQLiteStatement stmtOrders = db.compileStatement(
                "INSERT OR REPLACE INTO tbl_orders ("
                        + COLUMN_NAMES
                        + ") VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16)");

        db.beginTransaction();
        try {
            String tagName = (tag != null ? tag.getTagSlug() : "");
            int tagType = (tag != null ? tag.tagType.toInt() : 0);

            NemurBuyerIdOrderId orderWithGapMarker = getGapMarkerIdsForTag(tag);

            for (NemurOrder order : orders) {
                // keep the gapMarker flag
                boolean hasGapMarker = orderWithGapMarker != null && orderWithGapMarker.getOrderId() == order.orderId
                                       && orderWithGapMarker.getBuyerId() == order.buyerId;
                stmtOrders.bindLong(1, order.orderId);
                stmtOrders.bindLong(2, order.buyerId);
                stmtOrders.bindString(3, order.getPseudoId());
                stmtOrders.bindString(4, order.getAccountName());
                stmtOrders.bindLong(5, order.accountId);
                stmtOrders.bindString(6, order.getTitle());
                stmtOrders.bindString(7, order.getPrice());
                stmtOrders.bindString(8, order.getItemDistributionMode());
                stmtOrders.bindString(9, order.getBuyerName());
                stmtOrders.bindString(10, order.getFeaturedImage());
                stmtOrders.bindString(11, order.getFeaturedVideo());
                stmtOrders.bindString(12, order.getDatePublished());
                stmtOrders.bindString(13, tagName);
                stmtOrders.bindLong(14, tagType);
                stmtOrders.bindLong(15, SqlUtils.boolToSql(hasGapMarker));
                stmtOrders.bindString(16, NemurCardType.toString(order.getCardType()));
                stmtOrders.execute();
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            SqlUtils.closeStatement(stmtOrders);
        }
    }

    public static NemurOrderList getOrdersWithTag(NemurTag tag, int maxOrders) {
        if (tag == null) {
            return new NemurOrderList();
        }

        String sql = "SELECT * FROM tbl_orders WHERE tag_name=? AND tag_type=?";

        sql += " ORDER BY " + getSortColumnForTag(tag) + " DESC";

        if (maxOrders > 0) {
            sql += " LIMIT " + Integer.toString(maxOrders);
        }

        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        Cursor cursor = NemurDatabase.getReadableDb().rawQuery(sql, args);
        try {
            return getOrderListFromCursor(cursor);
        } finally {
            SqlUtils.closeCursor(cursor);
        }
    }

    /*
     * same as getOrdersWithTag() but only returns the buyerId/orderId pairs
     */
    public static NemurBuyerIdOrderIdList getBuyerIdOrderIdsWithTag(NemurTag tag, int maxOrders) {
        if (tag == null) {
            return new NemurBuyerIdOrderIdList();
        }

        String sql = "SELECT buyer_id, order_id FROM tbl_orders WHERE tag_name=? AND tag_type=?";

        sql += " ORDER BY " + getSortColumnForTag(tag) + " DESC";

        if (maxOrders > 0) {
            sql += " LIMIT " + Integer.toString(maxOrders);
        }

        String[] args = {tag.getTagSlug(), Integer.toString(tag.tagType.toInt())};
        return getBuyerIdOrderIds(sql, args);
    }

    private static NemurBuyerIdOrderIdList getBuyerIdOrderIds(@NonNull String sql, @NonNull String[] args) {
        NemurBuyerIdOrderIdList idList = new NemurBuyerIdOrderIdList();
        Cursor cursor = NemurDatabase.getReadableDb().rawQuery(sql, args);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    idList.add(new NemurBuyerIdOrderId(cursor.getLong(0), cursor.getLong(1)));
                } while (cursor.moveToNext());
            }
            return idList;
        } finally {
            SqlUtils.closeCursor(cursor);
        }
    }

    private static NemurOrder getOrderFromCursor(Cursor c) {
        if (c == null) {
            throw new IllegalArgumentException("getOrderFromCursor > null cursor");
        }

        NemurOrder order = new NemurOrder();

        order.orderId = c.getLong(c.getColumnIndex("order_id"));
        order.buyerId = c.getLong(c.getColumnIndex("buyer_id"));
        order.accountId = c.getLong(c.getColumnIndex("account_id"));
        order.setPseudoId(c.getString(c.getColumnIndex("pseudo_id")));

        order.setAccountName(c.getString(c.getColumnIndex("account_name")));
        order.setBuyerName(c.getString(c.getColumnIndex("buyer_name")));
        order.setPrice(c.getString(c.getColumnIndex("price")));
        order.setItemDistributionMode(c.getString(c.getColumnIndex("item_distribution_mode")));
        order.setFeaturedImage(c.getString(c.getColumnIndex("featured_image")));
        order.setFeaturedVideo(c.getString(c.getColumnIndex("featured_video")));

        order.setTitle(c.getString(c.getColumnIndex("title")));

        order.setDatePublished(c.getString(c.getColumnIndex("date_published")));

        order.setCardType(NemurCardType.fromString(c.getString(c.getColumnIndex("card_type"))));

        return order;
    }

    private static NemurOrderList getOrderListFromCursor(Cursor cursor) {
        NemurOrderList orders = new NemurOrderList();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    orders.add(getOrderFromCursor(cursor));
                } while (cursor.moveToNext());
            }
        } catch (IllegalStateException e) {
            AppLog.e(AppLog.T.NEMUR, e);
        }
        return orders;
    }
}

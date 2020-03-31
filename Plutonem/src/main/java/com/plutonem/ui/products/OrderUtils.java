package com.plutonem.ui.products;

import androidx.annotation.Nullable;

import com.plutonem.android.fluxc.model.OrderImmutableModel;
import com.plutonem.android.fluxc.model.order.OrderStatus;

import org.apache.commons.lang3.StringUtils;
import org.wordpress.android.util.DateTimeUtils;

import java.util.Date;

public class OrderUtils {
    public static boolean isConfirmable(OrderImmutableModel order) {
        return order != null && !(order.getShopTitle().trim().isEmpty()
                                    || order.getProductDetail().trim().isEmpty()
                                    || order.getItemSalesPrice().trim().isEmpty()
                                    || order.getOrderNumber() == 0
                                    || order.getItemDistributionMode().trim().isEmpty()
                                    || order.getOrderName().trim().isEmpty()
                                    || order.getOrderPhoneNumber().trim().isEmpty()
                                    || order.getOrderAddress().trim().isEmpty());
    }

    /**
     * Checks if two orders have differing data
     */
    public static boolean orderHasEdits(@Nullable OrderImmutableModel oldOrder, OrderImmutableModel newOrder) {
        if (oldOrder == null) {
            return newOrder != null;
        }

        return newOrder == null || !(StringUtils.equals(oldOrder.getShopTitle(), newOrder.getShopTitle())
                                    && StringUtils.equals(oldOrder.getProductDetail(), newOrder.getProductDetail())
                                    && StringUtils.equals(oldOrder.getItemSalesPrice(), newOrder.getItemSalesPrice())
                                    && StringUtils.equals(String.valueOf(oldOrder.getOrderNumber()), String.valueOf(newOrder.getOrderNumber()))
                                    && StringUtils.equals(oldOrder.getItemDistributionMode(), newOrder.getItemDistributionMode())
                                    && StringUtils.equals(oldOrder.getOrderName(), newOrder.getOrderName())
                                    && StringUtils.equals(oldOrder.getOrderPhoneNumber(), newOrder.getOrderPhoneNumber())
                                    && StringUtils.equals(oldOrder.getOrderAddress(), newOrder.getOrderAddress())
                                    && StringUtils.equals(oldOrder.getDateCreated(), newOrder.getDateCreated())
                                    && oldOrder.getChangesConfirmedContentHashcode() == newOrder
                .getChangesConfirmedContentHashcode()
        );
    }

    static boolean shouldConfirmImmediately(OrderStatus orderStatus, String dateCreated) {
        if (!shouldConfirmImmediatelyOptionBeAvailable(orderStatus)) {
            return false;
        }
        Date confDate = DateTimeUtils.dateFromIso8601(dateCreated);
        Date now = new Date();
        // Confirm immediately for orders that don't have any date set yet and paying with confirm dates in the past
        return confDate == null || !confDate.after(now);
    }

    static boolean shouldConfirmImmediatelyOptionBeAvailable(OrderStatus orderStatus) {
        return orderStatus == OrderStatus.PAYING;
    }
}

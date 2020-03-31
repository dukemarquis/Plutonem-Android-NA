package com.plutonem.models;

import com.plutonem.ui.nemur.models.NemurBuyerIdOrderId;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class NemurOrderList extends ArrayList<NemurOrder> {
    public static NemurOrderList fromJson(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("null json order list");
        }

        NemurOrderList orders = new NemurOrderList();
        JSONArray jsonOrders = json.optJSONArray("orders");
        if (jsonOrders != null) {
            for (int i = 0; i < jsonOrders.length(); i++) {
                orders.add(NemurOrder.fromJson(jsonOrders.optJSONObject(i)));
            }
        }

        return orders;
    }

    @Override
    public Object clone() {
        return super.clone();
    }

    public int indexOfOrder(NemurOrder order) {
        if (order == null) {
            return -1;
        }
        for (int i = 0; i < size(); i++) {
            if (this.get(i).orderId == order.orderId) {
                if (order.buyerId == this.get(i).buyerId) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int indexOfIds(NemurBuyerIdOrderId ids) {
        if (ids == null) {
            return -1;
        }
        for (int i = 0; i < size(); i++) {
            if (this.get(i).hasIds(ids)) {
                return i;
            }
        }
        return -1;
    }

    /*
     * Does passed list contain the same orders as this list?
     */
    public boolean isSameListWithX(NemurOrderList orders) {
        if (orders == null || orders.size() != this.size()) {
            return false;
        }

        for (NemurOrder order : orders) {
            int index = indexOfOrder(order);

            if (index == -1) {
                return false;
            }

            NemurOrder orderInsideList = this.get(index);

            if (!order.isSameOrder(orderInsideList)) {
                return false;
            }
        }

        return true;
    }
}

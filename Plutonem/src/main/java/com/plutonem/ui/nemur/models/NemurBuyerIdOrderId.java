package com.plutonem.ui.nemur.models;

import java.io.Serializable;

public class NemurBuyerIdOrderId implements Serializable {
    private static final long serialVersionUID = 0L;

    private final long mBuyerId;
    private final long mOrderId;

    public NemurBuyerIdOrderId(long buyerId, long orderId) {
        mBuyerId = buyerId;
        mOrderId = orderId;
    }

    public long getBuyerId() {
        return mBuyerId;
    }

    public long getOrderId() {
        return mOrderId;
    }
}

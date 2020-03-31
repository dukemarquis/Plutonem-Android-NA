package com.plutonem.ui.submits;

import com.plutonem.android.fluxc.model.OrderImmutableModel;

public class OrderEvents {
    public static class OrderSubmitStarted {
        public final OrderImmutableModel order;

        public OrderSubmitStarted(OrderImmutableModel order) {
            this.order = order;
        }
    }

    public static class OrderOpenedInEditor {
        public final int localBuyerId;
        public final int orderId;

        public OrderOpenedInEditor(int localBuyerId, int orderId) {
            this.localBuyerId = localBuyerId;
            this.orderId = orderId;
        }
    }
}

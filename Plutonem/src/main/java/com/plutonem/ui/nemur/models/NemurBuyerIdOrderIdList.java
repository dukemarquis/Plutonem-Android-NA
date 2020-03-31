package com.plutonem.ui.nemur.models;

import java.io.Serializable;
import java.util.ArrayList;

public class NemurBuyerIdOrderIdList extends ArrayList<NemurBuyerIdOrderId>
        implements Serializable {
    private static final long serialVersionUID = 0L;

    public NemurBuyerIdOrderIdList() {
        super();
    }

    /*
     * when Android serializes any ArrayList descendant, it does so as an ArrayList
     * rather than its actual class - use this to convert the serialized list back
     * into a NemurBuyerIdOrderIdList
     */
    /*
     * when Android serializes any ArrayList descendant, it does so as an ArrayList
     * rather than its actual class - use this to convert the serialized list back
     * into a ReaderBlogIdPostIdList
     */
    @SuppressWarnings("unused")
    public NemurBuyerIdOrderIdList(Serializable serializedList) {
        super();
        if (serializedList != null && serializedList instanceof ArrayList) {
            //noinspection unchecked
            ArrayList<NemurBuyerIdOrderId> list = (ArrayList<NemurBuyerIdOrderId>) serializedList;
            for (NemurBuyerIdOrderId idPair : list) {
                this.add(idPair);
            }
        }
    }

    public int indexOf(long buyerId, long orderId) {
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).getBuyerId() == buyerId && this.get(i).getOrderId() == orderId) {
                return i;
            }
        }
        return -1;
    }
}

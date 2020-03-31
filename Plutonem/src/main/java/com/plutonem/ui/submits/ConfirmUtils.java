package com.plutonem.ui.submits;

import android.app.Activity;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.plutonem.R;
import com.plutonem.android.fluxc.model.BuyerModel;
import com.plutonem.android.fluxc.model.OrderModel;
import com.plutonem.android.fluxc.model.order.OrderStatus;
import com.plutonem.ui.utils.UiString.UiStringRes;
import com.plutonem.ui.utils.UiString.UiStringText;
import com.plutonem.utilities.SnackbarItem;
import com.plutonem.utilities.SnackbarItem.Info;
import com.plutonem.utilities.SnackbarSequencer;

public class ConfirmUtils {
    private static final int K_SNACKBAR_WAIT_TIME_MS = 5000;

    public static void showSnackbarError(View view, String message, SnackbarSequencer sequencer) {
        sequencer.enqueue(
                new SnackbarItem(
                        new Info(
                                view,
                                new UiStringText(message),
                                K_SNACKBAR_WAIT_TIME_MS
                        ),
                        null,
                        null
                )
        );
    }

    public static void showSnackbar(View view, int messageRes, SnackbarSequencer sequencer) {
        sequencer.enqueue(
                new SnackbarItem(
                        new Info(
                                view,
                                new UiStringRes(messageRes),
                                Snackbar.LENGTH_LONG
                        ),
                        null,
                        null
                )
        );
    }

    /*
     * returns true if the user has permission to confirm the order - assumed to be true for
     * dot.com buyers because it is what it is
     */
    public static boolean userCanConfirm(BuyerModel buyer) {
        return true;
    }

    public static void onOrderSubmittedSnackbarHandler(final Activity activity, View snackbarAttachView,
                                                       boolean isError,
                                                       final OrderModel order,
                                                       final String errorMessage,
                                                       final BuyerModel buyer,
                                                       SnackbarSequencer sequencer) {
        boolean userCanConfirm = userCanConfirm(buyer);
        if (isError) {
            if (errorMessage != null) {
                ConfirmUtils.showSnackbarError(snackbarAttachView, errorMessage, sequencer);
            } else {
                ConfirmUtils.showSnackbar(snackbarAttachView, R.string.editor_draft_saved_locally, sequencer);
            }
        } else {
            if (order != null) {
                OrderStatus status = OrderStatus.fromOrder(order);
                int snackbarMessageRes;
                int snackbarButtonRes = 0;

                switch (status) {
                    case DELIVERING:
                        if (userCanConfirm) {
                            snackbarMessageRes = R.string.order_confirmed;
                        } else {
                            snackbarMessageRes = R.string.order_submitted;
                        }
                        break;
                    default:
                        snackbarMessageRes = R.string.order_updated;
                        break;
                }

                if (snackbarButtonRes > 0) {

                } else {
                    ConfirmUtils.showSnackbar(snackbarAttachView, snackbarMessageRes, sequencer);
                }
            }
        }
    }
}

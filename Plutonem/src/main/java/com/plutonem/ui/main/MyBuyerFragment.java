package com.plutonem.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.fluxc.model.BuyerModel;
import com.plutonem.ui.ActivityLauncher;
import com.plutonem.utilities.BuyerUtils;
import com.plutonem.utilities.image.ImageManager;
import com.plutonem.utilities.image.ImageType;
import com.plutonem.widgets.PNTextView;

import javax.inject.Inject;

public class MyBuyerFragment extends Fragment implements
        PMainActivity.OnScrollToTopListener,
        MainToolbarFragment {
    private ImageView mByavatarImageView;
    private ProgressBar mByavatarProgressBar;
    private PNTextView mBuyerTitleTextView;
    private PNTextView mBuyerSubtitleTextView;
    private View mQuickActionAwaitDeliveryButtonContainer;
    private LinearLayout mQuickActionButtonsContainer;
    private ScrollView mScrollView;

    @Nullable
    private Toolbar mToolbar = null;
    private String mToolbarTitle;

    private int mByavatarSz;

    @Inject ImageManager mImageManager;

    public static MyBuyerFragment newInstance() {
        return new MyBuyerFragment();
    }

    public @Nullable BuyerModel getSelectedBuyer() {
        if (getActivity() instanceof PMainActivity) {
            PMainActivity mainActivity = (PMainActivity) getActivity();
            return mainActivity.getSelectedBuyer();
        }
        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((Plutonem) requireActivity().getApplication()).component().inject(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Buyer details may have changed (e.g. returning to this Fragment) so update the UI
        refreshSelectedBuyerDetails(getSelectedBuyer());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.my_buyer_fragment, container, false);

        mByavatarSz = getResources().getDimensionPixelSize(R.dimen.wordpress_blavatar_sz_small);

        mByavatarImageView = rootView.findViewById(R.id.my_buyer_blavatar);
        mByavatarProgressBar = rootView.findViewById(R.id.my_buyer_icon_progress);
        mBuyerTitleTextView = rootView.findViewById(R.id.my_buyer_title_label);
        mBuyerSubtitleTextView = rootView.findViewById(R.id.my_buyer_subtitle_label);
        mScrollView = rootView.findViewById(R.id.scroll_view);
        mQuickActionAwaitDeliveryButtonContainer = rootView.findViewById(R.id.quick_action_await_delivery_container);
        mQuickActionButtonsContainer = rootView.findViewById(R.id.quick_action_buttons_container);

        setupClickListeners(rootView);

        mToolbar = rootView.findViewById(R.id.toolbar_main);
        mToolbar.setTitle(mToolbarTitle);

        return rootView;
    }

    private void setupClickListeners(View rootView) {
        rootView.findViewById(R.id.quick_action_await_delivery_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewProducts();
            }
        });

        rootView.findViewById(R.id.quick_action_await_receive_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewProducts();
            }
        });
    }

    private void viewProducts() {
        ActivityLauncher.viewCurrentBuyerProducts(getActivity(), getSelectedBuyer());
    }

    private void showBuyerIconProgressBar(boolean isVisible) {
        if (mByavatarProgressBar != null && mByavatarImageView != null) {
            if (isVisible) {
                mByavatarProgressBar.setVisibility(View.VISIBLE);
                mByavatarImageView.setVisibility(View.INVISIBLE);
            } else {
                mByavatarProgressBar.setVisibility(View.GONE);
                mByavatarImageView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void refreshSelectedBuyerDetails(BuyerModel buyer) {
        if (!isAdded()) {
            return;
        }

        if (buyer == null) {
            mScrollView.setVisibility(View.GONE);

            return;
        }

        mScrollView.setVisibility(View.VISIBLE);

        mImageManager.load(mByavatarImageView, ImageType.BYAVATAR, BuyerUtils.getBuyerIconUrl(buyer, mByavatarSz));
        String buyerDescription = BuyerUtils.getBuyerDescription(buyer);
        String buyerTitle = BuyerUtils.getBuyerName(buyer);

        mBuyerTitleTextView.setText(buyerTitle);
        mBuyerSubtitleTextView.setText(buyerDescription);

        // Refresh the title
        setTitle(buyer.getName());
    }

    @Override
    public void onScrollToTop() {
        if (isAdded()) {
            mScrollView.smoothScrollTo(0, 0);
        }
    }

    @Override
    public void setTitle(@NonNull final String title) {
        if (isAdded()) {
            mToolbarTitle = (title.isEmpty()) ? getString(R.string.plutonem) : title;

            if (mToolbar != null) {
                mToolbar.setTitle(mToolbarTitle);
            }
        }
    }

    /**
     * We can't just use fluxc OnBuyerChanged event, as the order of events is not guaranteed -> getSelectedBuyer()
     * method might return an out of date BuyerModel, if the OnBuyerChanged event handler in the PMainActivity wasn't
     * called yet.
     */
    public void onBuyerChanged(BuyerModel buyer) {
        refreshSelectedBuyerDetails(buyer);
        showBuyerIconProgressBar(false);
    }
}

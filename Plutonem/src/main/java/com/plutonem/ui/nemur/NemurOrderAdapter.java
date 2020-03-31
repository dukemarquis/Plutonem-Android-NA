package com.plutonem.ui.nemur;

import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.datasets.NemurOrderTable;
import com.plutonem.models.NemurOrder;
import com.plutonem.models.NemurOrderList;
import com.plutonem.models.NemurTag;
import com.plutonem.models.news.NewsItem;
import com.plutonem.ui.nemur.NemurTypes.NemurOrderListType;
import com.plutonem.ui.nemur.actions.NemurActions;
import com.plutonem.ui.nemur.models.NemurBuyerIdOrderId;
//import com.plutonem.ui.nemur.viewholders.NemurOrderViewHolder;
import com.plutonem.ui.nemur.viewholders.NemurOrderViewHolder;
import com.plutonem.ui.nemur.views.NemurBuyerHeaderView;
import com.plutonem.ui.nemur.views.NemurGapMarkerView;
import com.plutonem.ui.news.NewsViewHolder;
import com.plutonem.ui.news.NewsViewHolder.NewsCardListener;
import com.plutonem.utilities.image.ImageManager;
import com.plutonem.utilities.image.ImageType;

import org.wordpress.android.util.AppLog;

import kohii.v1.core.Playback;
import kohii.v1.exoplayer.Kohii;

public class NemurOrderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final ImageManager mImageManager;
    private final Kohii mKohii;
    private NewsCardListener mNewsCardListener;
    private NemurTag mCurrentTag;
    private int mGapMarkerPosition = -1;

    private final int mPhotonWidth;
    private final int mPhotonHeight;
    private final int mMarginLarge;

    private boolean mCanRequestMoreOrders;

    private final NemurTypes.NemurOrderListType mOrderListType;
    private final NemurOrderList mOrders = new NemurOrderList();
    private NewsItem mNewsItem;

    private NemurInterfaces.OnOrderSelectedListener mOrderSelectedListener;
    private NemurInterfaces.DataLoadedListener mDataLoadedListener;
    private NemurActions.DataRequestedListener mDataRequestedListener;
    private NemurBuyerHeaderView.OnBuyerInfoLoadedListener mBuyerInfoLoadedListener;

    private static final int MAX_ROWS = NemurConstants.NEMUR_MAX_ORDERS_TO_DISPLAY;

    private static final int VIEW_TYPE_ORDER = 0;
    private static final int VIEW_TYPE_BUYER_HEADER = 1;
    private static final int VIEW_TYPE_GAP_MARKER = 2;
    private static final int VIEW_TYPE_NEWS_CARD = 3;

    private static final long ITEM_ID_HEADER = -1L;
    private static final long ITEM_ID_GAP_MARKER = -2L;
    private static final long ITEM_ID_NEWS_CARD = -3L;

    private static final int NEWS_CARD_POSITION = 0;

    /*
     * full order
     */
//    private class NemurOrderViewHolder extends RecyclerView.ViewHolder {
//        final CardView mCardView;
//
//        private final TextView mTxtTitle;
//        private final TextView mTxtPrice;
//
//        private final ImageView mImgFeatured;
//
//        private final ViewGroup mFramePhoto;
//
//        NemurOrderViewHolder(View itemView) {
//            super(itemView);
//
//            mCardView = itemView.findViewById(R.id.card_view);
//
//            mTxtTitle = itemView.findViewById(R.id.text_title);
//            mTxtPrice = itemView.findViewById(R.id.text_price);
//
//            mFramePhoto = itemView.findViewById(R.id.frame_photo);
//            mImgFeatured = mFramePhoto.findViewById(R.id.image_featured);
//        }
//    }

    private class BuyerHeaderViewHolder extends RecyclerView.ViewHolder {
        private final NemurBuyerHeaderView mBuyerHeaderView;

        BuyerHeaderViewHolder(View itemView) {
            super(itemView);
            mBuyerHeaderView = (NemurBuyerHeaderView) itemView;
        }
    }

    private class GapMarkerViewHolder extends RecyclerView.ViewHolder {
        private final NemurGapMarkerView mGapMarkerView;

        GapMarkerViewHolder(View itemView) {
            super(itemView);
            mGapMarkerView = (NemurGapMarkerView) itemView;
        }
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        if (lp != null
                && lp instanceof StaggeredGridLayoutManager.LayoutParams
                && holder.getLayoutPosition() == 0
                && (hasBuyerHeader() || hasNewsCard())) {
            StaggeredGridLayoutManager.LayoutParams p = (StaggeredGridLayoutManager.LayoutParams) lp;
            p.setFullSpan(true);
        }
    }

    @Override
    public int getItemViewType(int position) {
        int headerPosition = hasNewsCard() ? 1 : 0;
        if (position == NEWS_CARD_POSITION && hasNewsCard()) {
            return VIEW_TYPE_NEWS_CARD;
        } else if (position == headerPosition && hasBuyerHeader()) {
            // first item is a NemurBuyerHeaderView
            return VIEW_TYPE_BUYER_HEADER;
        } else if (position == mGapMarkerPosition) {
            return VIEW_TYPE_GAP_MARKER;
        } else {
            return VIEW_TYPE_ORDER;
        }
    }

    @Override
    public @NonNull
    RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View orderView;
        switch (viewType) {
            case VIEW_TYPE_NEWS_CARD:
                return new NewsViewHolder(parent, mNewsCardListener);
            case VIEW_TYPE_BUYER_HEADER:
                NemurBuyerHeaderView nemurBuyerHeaderView = new NemurBuyerHeaderView(context);
                return new BuyerHeaderViewHolder(nemurBuyerHeaderView);

            case VIEW_TYPE_GAP_MARKER:
                return new GapMarkerViewHolder(new NemurGapMarkerView(context));

            default:
//                return new GapMarkerViewHolder(new NemurGapMarkerView(context));
                return new NemurOrderViewHolder(parent, mKohii);
//            default:
//                orderView = LayoutInflater.from(context).inflate(R.layout.nemur_cardview_order, parent, false);
//                return new NemurOrderViewHolder(orderView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof NemurOrderViewHolder) {
//            renderOrder(position, (NemurOrderViewHolder) holder);
            final NemurOrder order = getItem(position);
            if (order == null) return;
            ((NemurOrderViewHolder) holder).renderOrder(order, mImageManager, mPhotonWidth, mPhotonHeight, mMarginLarge, mOrderSelectedListener);
            checkLoadMore(position);
        } else if (holder instanceof BuyerHeaderViewHolder) {
            BuyerHeaderViewHolder buyerHolder = (BuyerHeaderViewHolder) holder;
            buyerHolder.mBuyerHeaderView.setOnBuyerInfoLoadedListener(mBuyerInfoLoadedListener);
            if (isNemur()) {
                buyerHolder.mBuyerHeaderView.loadBuyerInfo(NemurConstants.NEMUR_BUYER_ID);
            }
        } else if (holder instanceof GapMarkerViewHolder) {
            GapMarkerViewHolder gapHolder = (GapMarkerViewHolder) holder;
            gapHolder.mGapMarkerView.setCurrentTag(mCurrentTag);
        } else if (holder instanceof NewsViewHolder) {
            ((NewsViewHolder) holder).bind(mNewsItem);
        }
    }

//    private void renderOrder(final int position, final NemurOrderViewHolder holder) {
//        final NemurOrder order = getItem(position);
//        if (order == null) {
//            return;
//        }
//
//        mImageManager.cancelRequestAndClearImageView(holder.mImgFeatured);
//        holder.mTxtTitle.setVisibility(View.VISIBLE);
//        holder.mTxtTitle.setText(order.getTitle());
//
//        if (order.hasPrice()) {
//            holder.mTxtPrice.setVisibility(View.VISIBLE);
//            holder.mTxtPrice.setText(order.getPrice());
//        } else {
//            holder.mTxtPrice.setVisibility(View.GONE);
//        }
//
//        final int titleMargin;
//        if (order.hasFeaturedImage()) {
//            mImageManager.load(holder.mImgFeatured, ImageType.PHOTO,
//                    order.getFeaturedImageForDisplay(mPhotonWidth, mPhotonHeight), ScaleType.CENTER_INSIDE);
//            holder.mFramePhoto.setVisibility(View.VISIBLE);
//            titleMargin = mMarginLarge;
//        } else {
//            holder.mFramePhoto.setVisibility(View.GONE);
//            titleMargin = 0;
//        }
//
//        // set the top margin of the title based on whether there's a featured image
//        LayoutParams params = (LayoutParams) holder.mTxtTitle.getLayoutParams();
//        params.topMargin = titleMargin;
//
//        holder.mCardView.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (mOrderSelectedListener != null) {
//                    mOrderSelectedListener.onOrderSelected(order);
//                }
//            }
//        });
//
//        checkLoadMore(position);
//    }

    /*
     * if we're nearing the end of the orders, fire request to load more
     */
    private void checkLoadMore(int position) {
        if (mCanRequestMoreOrders
                && mDataRequestedListener != null
                && (position >= getItemCount() - 1)) {
            mDataRequestedListener.onRequestData();
        }
    }

    // ********************************************************************************************

    public NemurOrderAdapter(
            Context context,
            NemurOrderListType orderListType,
            ImageManager imageManager,
            Kohii kohii
    ) {
        super();
        ((Plutonem) context.getApplicationContext()).component().inject(this);
        this.mImageManager = imageManager;
        this.mKohii = kohii;
        mOrderListType = orderListType;
        mMarginLarge = context.getResources().getDimensionPixelSize(R.dimen.wordpress_margin_large);

//        int displayWidth = DisplayUtils.getDisplayPixelWidth(context);
//        int cardMargin = context.getResources().getDimensionPixelSize(R.dimen.nemur_card_margin);
//        mPhotonWidth = displayWidth - (cardMargin * 2);
//        mPhotonHeight = context.getResources().getDimensionPixelSize(R.dimen.nemur_featured_image_height_cardview);
        mPhotonWidth = -1;
        mPhotonHeight = -1;

        setHasStableIds(true);
    }

    private boolean hasHeader() {
        return hasBuyerHeader();
    }

    private boolean hasBuyerHeader() {
        return isNemur();
    }

    private boolean isNemur() {
        return mCurrentTag != null && mCurrentTag.isNemur();
    }

    public void setOnOrderSelectedListener(NemurInterfaces.OnOrderSelectedListener listener) {
        mOrderSelectedListener = listener;
    }

    public void setOnDataLoadedListener(NemurInterfaces.DataLoadedListener listener) {
        mDataLoadedListener = listener;
    }

    public void setOnDataRequestedListener(NemurActions.DataRequestedListener listener) {
        mDataRequestedListener = listener;
    }

    public void setOnBuyerInfoLoadedListener(NemurBuyerHeaderView.OnBuyerInfoLoadedListener listener) {
        mBuyerInfoLoadedListener = listener;
    }

    public void setOnNewsCardListener(NewsCardListener newsCardListener) {
        this.mNewsCardListener = newsCardListener;
    }

    private NemurTypes.NemurOrderListType getOrderListType() {
        return (mOrderListType != null ? mOrderListType : NemurTypes.DEFAULT_ORDER_LIST_TYPE);
    }

    public boolean isCurrentTag(NemurTag tag) {
        return NemurTag.isSameTag(tag, mCurrentTag);
    }

    // used when the viewing tagged orders
    public void setCurrentTag(NemurTag tag) {
        if (!NemurTag.isSameTag(tag, mCurrentTag)) {
            mCurrentTag = tag;
            reload();
        }
    }

    public void clear() {
        mGapMarkerPosition = -1;
        if (!mOrders.isEmpty()) {
            mOrders.clear();
            notifyDataSetChanged();
        }
    }

    public void refresh() {
        loadOrders();
    }

    /*
     * same as refresh() above but first clears the existing orders
     */
    public void reload() {
        clear();
        loadOrders();
    }

    private void loadOrders() {
        if (mIsTaskRunning) {
            AppLog.w(AppLog.T.NEMUR, "nemur orders task already running");
            return;
        }
        new LoadOrdersTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private NemurOrder getItem(int position) {
        if (position == NEWS_CARD_POSITION && hasNewsCard()) {
            return null;
        }
        if (position == getHeaderPosition() && hasHeader()) {
            return null;
        }
        if (position == mGapMarkerPosition) {
            return null;
        }

        int arrayPos = position - getItemPositionOffset();

        if (mGapMarkerPosition > -1 && position > mGapMarkerPosition) {
            arrayPos--;
        }

        return mOrders.get(arrayPos);
    }

    private int getItemPositionOffset() {
        int newsCardOffset = hasNewsCard() ? 1 : 0;
        int headersOffset = hasHeader() ? 1 : 0;
        return newsCardOffset + headersOffset;
    }

    private int getHeaderPosition() {
        int headerPosition = hasNewsCard() ? 1 : 0;
        return hasHeader() ? headerPosition : -1;
    }

    @Override
    public int getItemCount() {
        int size = mOrders.size();
        if (mGapMarkerPosition != -1) {
            size++;
        }
        if (hasHeader()) {
            size++;
        }
        if (hasNewsCard()) {
            size++;
        }
        return size;
    }

    public boolean isEmpty() {
        return (mOrders == null || mOrders.size() == 0);
    }

    @Override
    public long getItemId(int position) {
        switch (getItemViewType(position)) {
            case VIEW_TYPE_BUYER_HEADER:
                return ITEM_ID_HEADER;
            case VIEW_TYPE_GAP_MARKER:
                return ITEM_ID_GAP_MARKER;
            case VIEW_TYPE_NEWS_CARD:
                return ITEM_ID_NEWS_CARD;
            default:
                NemurOrder order = getItem(position);
                return order != null ? order.getStableId() : 0;
        }
    }

    public void removeGapMarker() {
        if (mGapMarkerPosition == -1) {
            return;
        }

        int position = mGapMarkerPosition;
        mGapMarkerPosition = -1;
        if (position < getItemCount()) {
            notifyItemRemoved(position);
        }
    }

    public void updateNewsCardItem(NewsItem newsItem) {
        NewsItem prevState = mNewsItem;
        mNewsItem = newsItem;
        if (prevState == null && newsItem != null) {
            notifyItemInserted(NEWS_CARD_POSITION);
        } else if (prevState != null) {
            if (newsItem == null) {
                notifyItemRemoved(NEWS_CARD_POSITION);
            } else {
                notifyItemChanged(NEWS_CARD_POSITION);
            }
        }
    }

    private boolean hasNewsCard() {
        // We don't want to display the card when we are displaying just a loading screen. However, on Nemur a header
        // is shown, even when we are loading data, so the card should be displayed. [moreover displaying the card only
        // after we fetch the data results in weird animation after configuration change, since it plays insertion
        // animation for all the data (including the card) except of the header which hasn't changed].
        return mNewsItem != null && (!isEmpty() || isNemur());
    }

    /*
     * AsyncTask to load orders in the current tag
     */
    private boolean mIsTaskRunning = false;

    private class LoadOrdersTask extends AsyncTask<Void, Void, Boolean> {
        private NemurOrderList mAllOrders;

        private boolean mCanRequestMoreOrdersTemp;
        private int mGapMarkerPositionTemp;

        @Override
        protected void onPreExecute() {
            mIsTaskRunning = true;
        }

        @Override
        protected void onCancelled() {
            mIsTaskRunning = false;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            int numExisting;
            switch (getOrderListType()) {
                case TAG_DEFAULT:
                case SEARCH_RESULTS:
                    mAllOrders = NemurOrderTable.getOrdersWithTag(mCurrentTag, MAX_ROWS);
                    numExisting = NemurOrderTable.getNumOrdersWithTag(mCurrentTag);
                    break;
                default:
                    return false;
            }

            if (mOrders.isSameListWithX(mAllOrders)) {
                return false;
            }

            // if we're not already displaying the max # orders, enable requesting more when
            // the user scrolls to the end of the list
            mCanRequestMoreOrdersTemp = (numExisting < NemurConstants.NEMUR_MAX_ORDERS_TO_DISPLAY);

            // determine whether a gap marker exists - only applies to tagged orders
            mGapMarkerPositionTemp = getGapMarkerPosition();

            return true;
        }

        private int getGapMarkerPosition() {
            if (!getOrderListType().isTagType()) {
                return -1;
            }

            NemurBuyerIdOrderId gapMarkerIds = NemurOrderTable.getGapMarkerIdsForTag(mCurrentTag);
            if (gapMarkerIds == null) {
                return -1;
            }

            int gapMarkerOrderPosition = mAllOrders.indexOfIds(gapMarkerIds);
            int gapMarkerPosition = -1;
            if (gapMarkerOrderPosition > -1) {
                // remove the gap marker if it's on the last order (edge case but
                // it can happen following a purge)
                if (gapMarkerOrderPosition == mAllOrders.size() - 1) {
                    AppLog.w(AppLog.T.NEMUR, "gap marker at/after last order, removed");
                    NemurOrderTable.removeGapMarkerForTag(mCurrentTag);
                } else {
                    // we want the gap marker to appear *below* this order
                    gapMarkerPosition = gapMarkerOrderPosition + 1;
                    // increment it if there are custom items at the top of the list (header or newsCard)
                    gapMarkerPosition += getItemPositionOffset();
                    AppLog.d(AppLog.T.NEMUR, "gap marker at position " + gapMarkerOrderPosition);
                }
            }
            return gapMarkerPosition;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                NemurOrderAdapter.this.mGapMarkerPosition = mGapMarkerPositionTemp;
                NemurOrderAdapter.this.mCanRequestMoreOrders = mCanRequestMoreOrdersTemp;
                mOrders.clear();
                mOrders.addAll(mAllOrders);
                notifyDataSetChanged();
            }

            if (mDataLoadedListener != null) {
                mDataLoadedListener.onDataLoaded(isEmpty());
            }

            mIsTaskRunning = false;
        }
    }
}

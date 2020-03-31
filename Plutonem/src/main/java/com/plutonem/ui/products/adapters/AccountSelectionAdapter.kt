package com.plutonem.ui.products.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.annotation.CallSuper
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.plutonem.Plutonem
import com.plutonem.R
import com.plutonem.ui.products.AccountFilterListItemUIState
import com.plutonem.ui.products.AccountFilterSelection
import com.plutonem.ui.utils.UiHelpers
import com.plutonem.utilities.image.ImageManager
import javax.inject.Inject

class AccountSelectionAdapter(context: Context) : BaseAdapter() {
    @Inject lateinit var imageManager: ImageManager
    @Inject lateinit var uiHelpers: UiHelpers

    private val items = mutableListOf<AccountFilterListItemUIState>()

    init {
        (context.applicationContext as Plutonem).component().inject(this)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view: View? = convertView
        val holder: DropdownViewHolder

        if (view == null) {
            val inflater = LayoutInflater.from(parent.context)
            view = inflater.inflate(R.layout.product_list_account_selection_dropdown, parent, false)
            holder = DropdownViewHolder(view)
            view.tag = holder
        } else {
            holder = view.tag as DropdownViewHolder
        }

        holder.bind(items[position], imageManager, uiHelpers)

        return view!!
    }

    override fun getItemId(position: Int): Long = items[position].id

    fun getIndexOfSelection(selection: AccountFilterSelection): Int? {
        for ((index, item) in items.withIndex()) {
            if (item.id == selection.id) {
                return index
            }
        }

        return null
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view: View? = convertView
        val holder: NormalViewHolder

        if (view == null) {
            val inflater = LayoutInflater.from(parent.context)
            view = inflater.inflate(R.layout.product_list_account_selection, parent, false)
            holder = NormalViewHolder(view)
            view.tag = holder
        } else {
            holder = view.tag as NormalViewHolder
        }

        holder.bind(items[position], imageManager, uiHelpers)

        return view!!
    }

    override fun hasStableIds(): Boolean = true

    override fun getItem(position: Int): Any = items[position]

    override fun getCount(): Int = items.count()

    fun updateItems(newItems: List<AccountFilterListItemUIState>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private open class NormalViewHolder(protected val itemView: View) {
        protected val image: AppCompatImageView = itemView.findViewById(R.id.product_list_account_selection_image)

        @CallSuper
        open fun bind(state: AccountFilterListItemUIState, imageManager: ImageManager, uiHelpers: UiHelpers) {
            /**
             * We can't use error/placeholder drawables as it causes an issue.
             * It seems getView method always returns convertView == null when used with Spinner. When we invoke
             * imageManager.load..(url..) in the view holder and the 'url' is empty or the requests fails
             * an error/placeholder drawable is used. However, this results in another layout/measure phase
             * -> getView(..) is called again. However, since the convertView == null we inflate a new view and
             * imageManager.load..(..) is invoked again - this goes on forever.
             * In order to prevent this issue we don't use placeholder/error drawables in this case.
             * The cost of this solution is that an empty circle is shown if we don't have the avatar in the cache
             * and the request fails.
             */
            when (state) {
                is AccountFilterListItemUIState.Everyone -> {
                    imageManager.load(image, state.imageRes)
                }
            }
        }
    }

    private class DropdownViewHolder(itemView: View) : NormalViewHolder(itemView) {
        private val text: AppCompatTextView = itemView.findViewById(R.id.product_list_account_selection_text)

        override fun bind(state: AccountFilterListItemUIState, imageManager: ImageManager, uiHelpers: UiHelpers) {
            super.bind(state, imageManager, uiHelpers)
            val context = itemView.context
            text.text = uiHelpers.getTextOfUiString(context, state.text)
            itemView.setBackgroundColor(ContextCompat.getColor(context, state.dropDownBackground))
        }
    }
}
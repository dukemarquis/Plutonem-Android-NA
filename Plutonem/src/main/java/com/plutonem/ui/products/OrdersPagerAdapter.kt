package com.plutonem.ui.products

import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.plutonem.Plutonem
import com.plutonem.android.fluxc.model.BuyerModel
import java.lang.ref.WeakReference

class OrdersPagerAdapter(
        private val pages: List<OrderListType>,
        private val buyer: BuyerModel,
        fm: FragmentManager
) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    private val listFragments = mutableMapOf<Int, WeakReference<OrderListFragment>>()

    override fun getCount(): Int = pages.size

    override fun getItem(position: Int): OrderListFragment =
            OrderListFragment.newInstance(buyer, pages[position])

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as OrderListFragment
        listFragments[position] = WeakReference(fragment)
        return fragment
    }

    override fun getPageTitle(position: Int): CharSequence? =
            Plutonem.getContext().getString(pages[position].titleResId)
}
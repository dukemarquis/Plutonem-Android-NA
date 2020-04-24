package com.plutonem.ui.main

import android.content.Context
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomnavigation.BottomNavigationView.OnNavigationItemReselectedListener
import com.google.android.material.bottomnavigation.BottomNavigationView.OnNavigationItemSelectedListener
import com.google.android.material.bottomnavigation.LabelVisibilityMode
import com.plutonem.R
import com.plutonem.ui.main.PMainActivity.OnScrollToTopListener
import com.plutonem.ui.main.PMainNavigationView.PageType.HOME_PAGE
import com.plutonem.ui.main.PMainNavigationView.PageType.CHAT
import com.plutonem.ui.main.PMainNavigationView.PageType.ME
import com.plutonem.ui.nemur.NemurOrderListFragment
import com.plutonem.ui.prefs.AppPrefs
import com.plutonem.utilities.AniUtils
import com.plutonem.utilities.AniUtils.Duration;
import com.plutonem.utilities.getColorStateListFromAttribute
import com.plutonem.xmpp.ui.ConversationsOverviewFragment

/*
 * Bottom navigation view and related adapter used by the main activity for the
 * two primary views - note that we ignore the built-in icons and labels and
 * insert our own custom views so we have more control over their appearance
 */
class PMainNavigationView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr),
        OnNavigationItemSelectedListener, OnNavigationItemReselectedListener {
    private lateinit var navAdapter: NavAdapter
    private lateinit var fragmentManager: FragmentManager
    private lateinit var pageListener: OnPageListener
    private var prevPosition = -1
    private val unselectedButtonAlpha = ResourcesCompat.getFloat(
            resources,
            R.dimen.material_emphasis_disabled
    )

    private var currentPosition: Int
        get() = getPositionForItemId(selectedItemId)
        set(position) = updateCurrentPosition(position)

    val activeFragment: Fragment?
        get() = navAdapter.getFragment(currentPosition)

    var currentSelectedPage: PageType
        get() = getPageForItemId(selectedItemId)
        set(pageType) = updateCurrentPosition(pages().indexOf(pageType))

    interface OnPageListener {
        fun onPageChanged(position: Int)
        fun onMeAndChatButtonClicked(): Boolean
    }

    fun init(fm: FragmentManager, listener: OnPageListener) {
        fragmentManager = fm
        pageListener = listener

        navAdapter = NavAdapter()
        assignNavigationListeners(true)
        disableShiftMode()

        // overlay each item with our custom view
        val menuView = getChildAt(0) as BottomNavigationMenuView
        val inflater = LayoutInflater.from(context)
        for (i in 0 until menu.size()) {
            val itemView = menuView.getChildAt(i) as BottomNavigationItemView
            val customView: View = inflater.inflate(R.layout.navbar_item, menuView, false)

            val txtLabel = customView.findViewById<TextView>(R.id.nav_label)
            val imgIcon = customView.findViewById<ImageView>(R.id.nav_icon)
            txtLabel.text = getTitleForPosition(i)
            customView.contentDescription = getContentDescriptionForPosition(i)
            imgIcon.setImageResource(getDrawableResForPosition(i))
            if (i == getPosition(HOME_PAGE)) {
                customView.id = R.id.bottom_nav_nemur_button // identify view
            }

            itemView.addView(customView)
        }

        currentPosition = AppPrefs.getMainPageIndex(numPages() - 1)
    }

    private fun disableShiftMode() {
        labelVisibilityMode = LabelVisibilityMode.LABEL_VISIBILITY_LABELED
    }

    private fun assignNavigationListeners(assign: Boolean) {
        setOnNavigationItemSelectedListener(if (assign) this else null)
        setOnNavigationItemReselectedListener(if (assign) this else null)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val position = getPositionForItemId(item.itemId)
        return if (position == getPosition(ME) || position == getPosition(CHAT)) {
            if (handleMeAndChatButtonClick()) {
                currentPosition = position
                pageListener.onPageChanged(position)
                true
            } else {
                false
            }
        } else {
            currentPosition = position
            pageListener.onPageChanged(position)
            return true
        }
    }

    private fun handleMeAndChatButtonClick(): Boolean {
        return pageListener.onMeAndChatButtonClicked()
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        // scroll the active fragment's contents to the top when user re-taps the current item
        val position = getPositionForItemId(item.itemId)
        (navAdapter.getFragment(position) as? OnScrollToTopListener)?.onScrollToTop()
    }

    private fun getPositionForItemId(@IdRes itemId: Int): Int {
        return getPosition(getPageForItemId(itemId))
    }

    private fun getPageForItemId(@IdRes itemId: Int): PageType {
        return when (itemId) {
            R.id.nav_home_page -> HOME_PAGE
            R.id.nav_chat -> CHAT
            else -> ME
        }
    }

    @IdRes
    private fun getItemIdForPosition(position: Int): Int {
        return when (getPageTypeOrNull(position)) {
            HOME_PAGE -> R.id.nav_home_page
            CHAT -> R.id.nav_chat
            else -> R.id.nav_me
        }
    }

    private fun updateCurrentPosition(position: Int) {
        // remove the title and selected state from the previously selected item
        if (prevPosition > -1) {
            setTitleViewSelected(prevPosition, false)
            setImageViewSelected(prevPosition, false)
        }

        // set the title and selected state from the newly selected item
        setTitleViewSelected(position, true)

        setImageViewSelected(position, true)

        AppPrefs.setMainPageIndex(position)
        prevPosition = position

        // temporarily disable the nav listeners so they don't fire when we change the selected page
        assignNavigationListeners(false)
        try {
            selectedItemId = getItemIdForPosition(position)
        } finally {
            assignNavigationListeners(true)
        }

        val fragment = navAdapter.getFragment(position)
        if (fragment != null) {
            fragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment, getTagForPosition(position))
                    // This is used because the main activity sometimes crashes because it's trying to switch fragments
                    // after `onSaveInstanceState` was already called. This is the related issue
                    // https://github.com/wordpress-mobile/WordPress-Android/issues/10852
                    .commitAllowingStateLoss()
        }
    }

    private fun setImageViewSelected(position: Int, isSelected: Boolean) {
        getImageViewForPosition(position)?.let {
            it.isSelected = isSelected
            it.alpha = if (isSelected) 1f else unselectedButtonAlpha
        }
    }

    private fun setTitleViewSelected(position: Int, isSelected: Boolean) {
        getTitleViewForPosition(position)?.setTextColor(
                context.getColorStateListFromAttribute(
                        if (isSelected) R.attr.colorPrimary else R.attr.pnColorOnSurfaceMedium
                )
        )
    }

    @DrawableRes
    private fun getDrawableResForPosition(position: Int): Int {
        return when (getPageTypeOrNull(position)) {
            HOME_PAGE -> R.drawable.ic_nemur_white_24dp
            CHAT -> R.drawable.ic_chat_white_24dp
            else -> R.drawable.ic_mine_circle_white_24dp
        }
    }

    private fun getTitleForPosition(position: Int): CharSequence {
        @StringRes val idRes: Int = when (pages().getOrNull(position)) {
            HOME_PAGE -> R.string.home_page_section_screen_title
            CHAT -> R.string.chat_screen_title
            else -> R.string.me_section_screen_title
        }
        return context.getString(idRes)
    }

    fun getTitleForPageType(pageType: PageType): CharSequence {
        return getTitleForPosition(getPosition(pageType))
    }

    private fun getContentDescriptionForPosition(position: Int): CharSequence {
        @StringRes val idRes: Int = when (pages().getOrNull(position)) {
            HOME_PAGE -> R.string.tabbar_accessibility_label_home_page
            CHAT -> R.string.tabbar_accessibility_label_chat
            else -> R.string.tabbar_accessibility_label_me
        }
        return context.getString(idRes)
    }

    fun getContentDescriptionForPageType(pageType: PageType): CharSequence {
        return getContentDescriptionForPosition(getPosition(pageType))
    }

    private fun getTagForPosition(position: Int): String {
        return when (getPageTypeOrNull(position)) {
            HOME_PAGE -> TAG_HOME_PAGE
            CHAT -> TAG_CHAT
            else -> TAG_ME
        }
    }

    private fun getTitleViewForPosition(position: Int): TextView? {
        return getItemView(position)?.findViewById(R.id.nav_label)
    }

    private fun getImageViewForPosition(position: Int): ImageView? {
        val itemView = getItemView(position)
        return itemView?.findViewById(R.id.nav_icon)
    }

    private fun showTitleForPosition(position: Int, show: Boolean) {
        val txtTitle = getTitleViewForPosition(position)
        txtTitle?.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun getFragment(pageType: PageType) = navAdapter.getFragment(getPosition(pageType))

    private fun getItemView(position: Int): BottomNavigationItemView? {
        if (isValidPosition(position)) {
            val menuView = getChildAt(0) as BottomNavigationMenuView
            return menuView.getChildAt(position) as BottomNavigationItemView
        }
        return null
    }

    fun showNemurBadge(showBadge: Boolean) {
        showBadge(getPosition(HOME_PAGE), showBadge)
    }

    /*
     * show or hide the badge on the 'pageId' icon in the bottom bar
     */
    private fun showBadge(pageId: Int, showBadge: Boolean) {
        val badgeView = getItemView(pageId)?.findViewById<View>(R.id.badge)

        val currentVisibility = badgeView?.visibility
        val newVisibility = if (showBadge) View.VISIBLE else View.GONE
        if (currentVisibility == newVisibility) {
            return
        }

        if (showBadge) {
            AniUtils.fadeIn(badgeView, Duration.MEDIUM)
        } else {
            AniUtils.fadeOut(badgeView, Duration.MEDIUM)
        }
    }

    private fun isValidPosition(position: Int): Boolean {
        return position in 0 until numPages()
    }

    private inner class NavAdapter {
        private val mFragments = SparseArray<Fragment>(numPages())

        private fun createFragment(position: Int): Fragment? {
            val fragment: Fragment = when (pages().getOrNull(position)) {
                HOME_PAGE -> NemurOrderListFragment.newInstance(true)
                CHAT -> ConversationsOverviewFragment.newInstance()
                ME -> MyBuyerFragment.newInstance()
                else -> return null
            }

            mFragments.put(position, fragment)
            return fragment
        }

        internal fun getFragment(position: Int): Fragment? {
            if (isValidPosition(position) && mFragments.get(position) != null) {
                return mFragments.get(position)
            }

            val fragment = fragmentManager.findFragmentByTag(getTagForPosition(position))
            return if (fragment != null) {
                mFragments.put(position, fragment)
                fragment
            } else {
                createFragment(position)
            }
        }
    }

    companion object {
        private val pages = listOf(HOME_PAGE, CHAT, ME)

        private const val TAG_HOME_PAGE = "tag-homepage"
        private const val TAG_CHAT = "tag-messages";
        private const val TAG_ME = "tag-me"

        private fun numPages(): Int = pages.size

        private fun pages(): List<PageType> = pages

        private fun getPageTypeOrNull(position: Int): PageType? {
            return pages().getOrNull(position)
        }

        fun getPosition(pageType: PageType): Int {
            return pages().indexOf(pageType)
        }

        @JvmStatic
        fun getPageType(position: Int): PageType {
            return pages()[position]
        }
    }

    enum class PageType {
        HOME_PAGE, CHAT, ME
    }
}
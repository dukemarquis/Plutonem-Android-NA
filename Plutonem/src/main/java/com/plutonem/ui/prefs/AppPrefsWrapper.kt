package com.plutonem.ui.prefs

import com.plutonem.ui.products.AccountFilterSelection
import com.plutonem.ui.products.OrderListViewLayoutType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable wrapper around AppPrefs.
 *
 * AppPrefs interface is consisted of static methods, which make the client code difficult to test/mock. Main purpose of
 * this wrapper is to make testing easier.
 *
 */
@Singleton
class AppPrefsWrapper @Inject constructor() {
    var newsCardDismissedVersion: Int
        get() = AppPrefs.getNewsCardDismissedVersion()
        set(version) = AppPrefs.setNewsCardDismissedVersion(version)

    var newsCardShownVersion: Int
        get() = AppPrefs.getNewsCardShownVersion()
        set(version) = AppPrefs.setNewsCardShownVersion(version)

    var orderListAccountSelection: AccountFilterSelection
        get() = AppPrefs.getAccountFilterSelection()
        set(value) = AppPrefs.setAccountFilterSelection(value)

    var orderListViewLayoutType: OrderListViewLayoutType
        get() = AppPrefs.getOrdersListViewLayoutType()
        set(value) = AppPrefs.setOrdersListViewLayoutType(value)
}
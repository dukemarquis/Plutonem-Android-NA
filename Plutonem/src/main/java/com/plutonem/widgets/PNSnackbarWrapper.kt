package com.plutonem.widgets

import android.view.View
import dagger.Reusable
import javax.inject.Inject

/**
 * Injectable wrapper around PNSnackbar.
 *
 * PNSnackbar interfaces are consisted of static methods, which
 * makes the client code difficult to test/mock. Main purpose of this wrapper is to make testing easier.
 */
@Reusable
class PNSnackbarWrapper @Inject constructor() {
    fun make(view: View, text: CharSequence, duration: Int): PNSnackbar = PNSnackbar.make(view, text, duration)
}
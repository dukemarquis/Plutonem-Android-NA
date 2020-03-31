package com.plutonem.ui.utils

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.plutonem.ui.utils.UiString.UiStringRes
import com.plutonem.ui.utils.UiString.UiStringText
import javax.inject.Inject

class UiHelpers @Inject constructor() {
    fun getTextOfUiString(context: Context, uiString: UiString): String =
            when (uiString) {
                is UiStringRes -> context.getString(uiString.stringRes)
                is UiStringText -> uiString.text
            }

    fun updateVisibility(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setTextOrHide(view: TextView, uiString: UiString?) {
        val text = uiString?.let { getTextOfUiString(view.context, uiString) }
        setTextOrHide(view, text)
    }

    fun setTextOrHide(view: TextView, text: CharSequence?) {
        updateVisibility(view, text != null)
        text?.let {
            view.text = text
        }
    }

    fun setImageOrHide(imageView: ImageView, @DrawableRes resId: Int?) {
        updateVisibility(imageView, resId != null)
        resId?.let {
            imageView.setImageResource(resId)
        }
    }
}
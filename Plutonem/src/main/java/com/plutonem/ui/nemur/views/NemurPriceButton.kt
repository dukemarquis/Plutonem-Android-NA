package com.plutonem.ui.nemur.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.plutonem.R
import kotlinx.android.synthetic.main.nemur_price_button.view.*
import java.util.*

/**
 * Price button used in nemur order detail
 */
class NemurPriceButton : LinearLayout {
    constructor(context: Context) : super(context) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initView(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initView(context)
    }

    private fun initView(context: Context) {
        View.inflate(context, R.layout.nemur_price_button, this)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        label_price_button.isEnabled = enabled
        icon_price_button.isEnabled = enabled
    }

    fun updateState(priceValue: String) {
        label_price_button.text = priceValue.toUpperCase(Locale.getDefault()).substring(1)

        if (priceValue.indexOf("$") == -1) {
            icon_price_button.text = "￥"
        } else {
            icon_price_button.text = "$"
        }

        label_price_button.isSelected = true
        icon_price_button.isSelected = true

        contentDescription = context.getString(R.string.nemur_description_price)
    }
}
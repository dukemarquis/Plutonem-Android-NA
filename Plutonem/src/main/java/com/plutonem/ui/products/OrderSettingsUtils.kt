package com.plutonem.ui.products

import android.text.TextUtils
import com.plutonem.R
import com.plutonem.viewmodels.ResourceProvider
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject

class OrderSettingsUtils
@Inject constructor(
        private val resourceProvider: ResourceProvider
) {
    fun getTotalPriceLabel(
            orderModel: EditOrderRepository
    ): String {
        val labelToUse: String
        val itemSalesPrice = orderModel.itemSalesPrice
        val orderNumber = orderModel.number
        val itemDistributionMode = orderModel.itemDistributionMode
        labelToUse = if (!TextUtils.isEmpty(itemSalesPrice)
                && orderNumber.toInt() != 0
                && !TextUtils.isEmpty(itemDistributionMode)) {
            val formattedPrice = formattedForTotalPriceLabel(itemSalesPrice, orderNumber, itemDistributionMode)
            resourceProvider.getString(R.string.total_on, formattedPrice)
        } else if (OrderUtils.shouldConfirmImmediatelyOptionBeAvailable(orderModel.status)) {
            resourceProvider.getString(R.string.hundred)
        } else {
            // TODO: What should the label be if there is no specific value and this is not a PAYING?
            ""
        }
        return labelToUse
    }

    private fun formattedForTotalPriceLabel(
        itemSalesPrice: String,
        orderNumber: Long,
        itemDistributionMode: String
    ): String {
        val itemSalesPriceTemporal = itemSalesPrice.substring(1)
//        val totalPriceTemporalInt: Int
        val totalPriceTemporalDouble: Double
        val totalPriceTemporalString: String
//        decimalFormat.roundingMode = RoundingMode.CEILING

        val totalPrice: String
        val itemDistributionModeTemporal: String
        if (itemDistributionMode.indexOf("$") > 0) {
            itemDistributionModeTemporal = itemDistributionMode.substring(itemDistributionMode.indexOf("$") + 1)
//            totalPriceTemporal = String.format("%.2f",itemSalesPriceTemporal.toInt() * orderNumber.toInt() + itemDistributionModeTemporal.toDouble().toInt().toDouble()).toDouble()
//            totalPriceTemporal = decimalFormat.format(totalPriceTemporal)
            totalPriceTemporalDouble = itemSalesPriceTemporal.toDouble() * orderNumber.toInt() + itemDistributionModeTemporal.toDouble()
//            totalPriceTemporalDouble = totalPriceTemporalInt.toDouble()
            totalPriceTemporalString = DecimalFormat("#,##0.00").format(totalPriceTemporalDouble)
            totalPrice = "$$totalPriceTemporalString"
        } else {
            itemDistributionModeTemporal = itemDistributionMode.substring(itemDistributionMode.indexOf("￥") + 1)
            totalPriceTemporalDouble = itemSalesPriceTemporal.toDouble() * orderNumber.toInt() + itemDistributionModeTemporal.toDouble()
//            totalPriceTemporalDouble = totalPriceTemporalInt.toDouble()
            totalPriceTemporalString = DecimalFormat("#,##0.00").format(totalPriceTemporalDouble)
            totalPrice = "￥$totalPriceTemporalString"
        }
        return totalPrice
    }
}
package com.plutonem.ui.products

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.plutonem.Plutonem
import com.plutonem.R
import com.plutonem.android.fluxc.model.BuyerModel
import com.plutonem.ui.ActivityId
import com.plutonem.ui.products.adapters.AccountSelectionAdapter
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import javax.inject.Inject

class ProductsListActivity : AppCompatActivity() {
    @Inject internal lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var buyer: BuyerModel
    private lateinit var viewModel: OrderListMainViewModel

    private lateinit var accountSelectionAdapter: AccountSelectionAdapter
    private lateinit var accountSelection: AppCompatSpinner

    private lateinit var tabLayout: TabLayout
    private lateinit var tabLayoutFadingEdge: View

    private lateinit var ordersPagerAdapter: OrdersPagerAdapter

    private lateinit var pager: androidx.viewpager.widget.ViewPager

    private var onPageChangeListener: ViewPager.OnPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

        override fun onPageSelected(position: Int) {
            viewModel.onTabChanged()
        }

        override fun onPageScrollStateChanged(state: Int) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!intent.hasExtra(Plutonem.BUYER)) {
            AppLog.e(T.PRODUCTS, "ProductListActivity started without a buyer.")
            finish()
            return
        }
        restartWhenBuyerHasChanged(intent)
    }

    private fun restartWhenBuyerHasChanged(intent: Intent) {
        val buyer = intent.getSerializableExtra(Plutonem.BUYER) as BuyerModel
        if (buyer.id != this.buyer.id) {
            finish()
            startActivity(intent)
            return
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as Plutonem).component().inject(this)
        setContentView(R.layout.product_list_activity)

        buyer = if (savedInstanceState == null) {
            intent.getSerializableExtra(Plutonem.BUYER) as BuyerModel
        } else {
            savedInstanceState.getSerializable(Plutonem.BUYER) as BuyerModel
        }

        setupActionBar()
        setupContent()
        initViewModel()
    }

    private fun setupActionBar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        title = getString(R.string.my_buyer_btn_buyer_orders)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupContent() {
        accountSelection = findViewById(R.id.product_list_account_selection)
        tabLayoutFadingEdge = findViewById(R.id.product_list_tab_layout_fading_edge)

        accountSelectionAdapter = AccountSelectionAdapter(this)
        accountSelection.adapter = accountSelectionAdapter

        accountSelection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>) {}

            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {}
        }

        pager = findViewById(R.id.orderPager)

        // Just a safety measure - there shouldn't by any existing listeners since this method is called just once.
        pager.clearOnPageChangeListeners()

        tabLayout = findViewById(R.id.tabLayout)
        tabLayout.setupWithViewPager(pager)
        pager.addOnPageChangeListener(onPageChangeListener)

        ordersPagerAdapter = OrdersPagerAdapter(ORDER_LIST_PAGES, buyer, supportFragmentManager)
        pager.adapter = ordersPagerAdapter
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this, viewModelFactory).get(OrderListMainViewModel::class.java)
        viewModel.start(buyer)

        viewModel.viewState.observe(this, Observer { state ->
            state?.let {
                val accountSelectionVisibility = if (state.isAccountFilterVisible) View.VISIBLE else View.GONE
                accountSelection.visibility = accountSelectionVisibility
                tabLayoutFadingEdge.visibility = accountSelectionVisibility

                val tabLayoutPaddingStart =
                        if (state.isAccountFilterVisible)
                            resources.getDimensionPixelSize(R.dimen.products_list_tab_layout_fading_edge_width)
                        else 0
                tabLayout.setPaddingRelative(tabLayoutPaddingStart, 0, 0, 0)

                accountSelectionAdapter.updateItems(state.accountFilterItems)

                accountSelectionAdapter.getIndexOfSelection(state.accountFilterSelection)?.let { selectionIndex ->
                    accountSelection.setSelection(selectionIndex)
                }
            }
        })
    }

    public override fun onResume() {
        super.onResume()
        ActivityId.trackLastActivity(ActivityId.ORDERS)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
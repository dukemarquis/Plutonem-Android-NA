package com.plutonem.modules;

import android.app.Application;

import com.plutonem.Plutonem;
import com.plutonem.android.fluxc.module.ReleaseBaseModule;
import com.plutonem.android.fluxc.module.ReleaseNetworkModule;
import com.plutonem.android.fluxc.module.ReleaseOkHttpClientModule;
import com.plutonem.android.login.di.LoginFragmentModule;
import com.plutonem.android.login.di.LoginServiceModule;
import com.plutonem.ui.BuyIntentReceiverActivity;
import com.plutonem.ui.accounts.LoginActivity;
import com.plutonem.ui.main.MyBuyerFragment;
import com.plutonem.ui.main.PMainActivity;
import com.plutonem.ui.nemur.NemurOrderAdapter;
import com.plutonem.ui.nemur.NemurOrderDetailFragment;
import com.plutonem.ui.nemur.NemurOrderListFragment;
import com.plutonem.ui.nemur.NemurOrderPagerActivity;
import com.plutonem.ui.nemur.services.update.NemurUpdateLogic;
import com.plutonem.ui.nemur.views.NemurBuyerHeaderView;
import com.plutonem.ui.products.EditOrderActivity;
import com.plutonem.ui.products.EditOrderSettingsFragment;
import com.plutonem.ui.products.OrderListFragment;
import com.plutonem.ui.products.ProductsListActivity;
import com.plutonem.ui.products.adapters.AccountSelectionAdapter;
import com.plutonem.ui.submits.OrderSubmitHandler;
import com.plutonem.ui.submits.SubmitService;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;
import dagger.android.AndroidInjector;
import dagger.android.support.AndroidSupportInjectionModule;

@Singleton
@Component(modules = {
        ApplicationModule.class,
        AppConfigModule.class,
        ReleaseBaseModule.class,
        ReleaseOkHttpClientModule.class,
        ReleaseNetworkModule.class,
        LegacyModule.class,
        AndroidSupportInjectionModule.class,
        ViewModelModule.class,
        ThreadModule.class,
        // Login flow library
        LoginFragmentModule.class,
        LoginServiceModule.class
})
public interface AppComponent extends AndroidInjector<Plutonem> {
    @Override
    void inject(Plutonem instance);

    void inject(PMainActivity object);

    void inject(SubmitService object);

    void inject(OrderSubmitHandler object);

    void inject(LoginActivity object);

    void inject(BuyIntentReceiverActivity object);

    void inject(MyBuyerFragment object);

    void inject(EditOrderActivity object);

    void inject(EditOrderSettingsFragment object);

    void inject(ProductsListActivity object);

    void inject(AccountSelectionAdapter object);

    void inject(OrderListFragment object);

    void inject(NemurUpdateLogic object);

    void inject(NemurOrderDetailFragment object);

    void inject(NemurOrderListFragment object);

    void inject(NemurOrderAdapter object);

    void inject(NemurBuyerHeaderView object);

    void inject(NemurOrderPagerActivity object);

    void inject(PlutonemGlideModule object);

    // Allows us to inject the application without having to instantiate any modules, and provides the Application
    // in the app graph

    @Component.Builder
    interface Builder {
        @BindsInstance
        AppComponent.Builder application(Application application);

        AppComponent build();
    }
}

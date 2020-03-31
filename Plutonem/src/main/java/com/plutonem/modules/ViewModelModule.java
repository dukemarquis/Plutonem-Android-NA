package com.plutonem.modules;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.plutonem.ui.nemur.viewmodels.NemurOrderListViewModel;
import com.plutonem.ui.products.OrderListMainViewModel;
import com.plutonem.viewmodels.ViewModelFactory;
import com.plutonem.viewmodels.ViewModelKey;
import com.plutonem.viewmodels.orders.OrderListViewModel;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class ViewModelModule {
    @Binds
    @IntoMap
    @ViewModelKey(NemurOrderListViewModel.class)
    abstract ViewModel nemurOrderListViewModel(NemurOrderListViewModel viewModel);

    @Binds
    @IntoMap
    @ViewModelKey(OrderListViewModel.class)
    abstract ViewModel orderListViewModel(OrderListViewModel viewModel);

    @Binds
    @IntoMap
    @ViewModelKey(OrderListMainViewModel.class)
    abstract ViewModel orderListMainViewModel(OrderListMainViewModel viewModel);

    @Binds
    abstract ViewModelProvider.Factory provideViewModelFactory(ViewModelFactory viewModelFactory);
}

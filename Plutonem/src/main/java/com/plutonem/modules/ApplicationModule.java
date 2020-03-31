package com.plutonem.modules;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LiveData;

import com.plutonem.ui.news.LocalNewsService;
import com.plutonem.ui.news.NewsService;
import com.plutonem.viewmodels.ContextProvider;
import com.plutonem.viewmodels.helpers.ConnectionStatus;
import com.plutonem.viewmodels.helpers.ConnectionStatusLiveData;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class ApplicationModule {
    // Expose Application as an injectable context
    @Binds
    abstract Context bindContext(Application application);

    @Provides
    public static NewsService provideLocalNewsService(ContextProvider contextProvider) {
        return new LocalNewsService(contextProvider);
    }

    @Provides
    static LiveData<ConnectionStatus> provideConnectionStatusLiveData(Context context) {
        return new ConnectionStatusLiveData.Factory(context).create();
    }
}
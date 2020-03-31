package com.plutonem.ui.news

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.plutonem.models.news.LocalNewsItem
import com.plutonem.models.news.NewsItem
import com.plutonem.viewmodels.ContextProvider
import javax.inject.Inject

/**
 * Service for fetching data for a News Card (Card with a new feature/update announcement) from a local resource.
 *
 * This is just a temporary solution until News Cards are supported on the server.
 */
class LocalNewsService @Inject constructor(private val contextProvider: ContextProvider) : NewsService {
    val data: MutableLiveData<NewsItem> = MutableLiveData()

    override fun newsItemSource(): LiveData<NewsItem> {
        return data
    }

    override fun pull(skipCache: Boolean) {
        data.value = loadCardFromResources()
    }

    override fun stop() {
        // clean not required
    }

    private fun loadCardFromResources(): NewsItem {
        return NewsItem(
                contextProvider.getContext().getString(LocalNewsItem.titleResId),
                contextProvider.getContext().getString(LocalNewsItem.contentResId),
                contextProvider.getContext().getString(LocalNewsItem.actionResId),
                contextProvider.getContext().getString(LocalNewsItem.urlResId),
                LocalNewsItem.version
        )
    }
}
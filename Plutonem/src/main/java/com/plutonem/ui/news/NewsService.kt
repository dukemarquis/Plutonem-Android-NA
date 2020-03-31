package com.plutonem.ui.news

import androidx.lifecycle.LiveData
import com.plutonem.models.news.NewsItem

interface NewsService {
    fun newsItemSource(): LiveData<NewsItem>

    fun pull(skipCache: Boolean)

    /**
     * Release resources and unregister from dispatchers.
     */
    fun stop()
}
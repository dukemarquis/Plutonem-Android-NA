package com.plutonem.ui.nemur.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.plutonem.models.NemurTag
import com.plutonem.models.news.NewsItem
import com.plutonem.modules.BG_THREAD
import com.plutonem.ui.news.NewsManager
import com.plutonem.viewmodels.ScopedViewModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Named

class NemurOrderListViewModel @Inject constructor(
        private val newsManager: NewsManager,
        @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher
) : ScopedViewModel(bgDispatcher) {
    private val newsItemSource = newsManager.newsItemSource()
    private val _newsItemSourceMediator = MediatorLiveData<NewsItem>()

    private val onTagChanged: Observer<NewsItem?> = Observer { _newsItemSourceMediator.value = it }

    private val _shouldCollapseToolbar = MutableLiveData<Boolean>()
    val shouldCollapseToolbar: LiveData<Boolean> = _shouldCollapseToolbar

    /**
     * First tag for which the card was shown.
     */
    private var initialTag: NemurTag? = null
    private var isStarted = false

    private var lastKnownUserId: Long? = null
    private var lastTokenAvailableStatus: Boolean? = null

    fun start(tag: NemurTag?, shouldShowSubfilter: Boolean, collapseToolbar: Boolean) {
        if (isStarted) {
            return
        }

        tag?.let {
            onTagChanged(tag)
            newsManager.pull()
        }

        _shouldCollapseToolbar.value = collapseToolbar

        isStarted = true
    }

    fun getNewsDataSource(): LiveData<NewsItem> {
        return _newsItemSourceMediator
    }

    fun onTagChanged(tag: NemurTag?) {
        tag?.let { newTag ->
            // show the card only when the initial tag is selected in the filter
            if (initialTag == null || newTag == initialTag) {
                _newsItemSourceMediator.addSource(newsItemSource, onTagChanged)
            } else {
                _newsItemSourceMediator.removeSource(newsItemSource)
                _newsItemSourceMediator.value = null
            }
        }
    }

    fun onNewsCardDismissed(item: NewsItem) {
        newsManager.dismiss(item)
    }

    fun onNewsCardShown(
            item: NewsItem,
            currentTag: NemurTag
    ) {
        initialTag = currentTag
        newsManager.cardShown(item)
    }

    fun onUserComesToNemur() {
        // skip Tag Update part.
    }

    override fun onCleared() {
        super.onCleared()
        newsManager.stop()
    }
}
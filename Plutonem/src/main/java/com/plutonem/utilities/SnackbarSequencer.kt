package com.plutonem.utilities

import android.app.Activity
import android.content.Context
import com.plutonem.modules.UI_THREAD
import com.plutonem.ui.utils.UiHelpers
import com.plutonem.widgets.PNSnackbar
import com.plutonem.widgets.PNSnackbarWrapper
import kotlinx.coroutines.*
import org.wordpress.android.util.AppLog
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

const val QUEUE_SIZE_LIMIT: Int = 5

@Singleton
class SnackbarSequencer @Inject constructor(
        private val uiHelper: UiHelpers,
        private val pnSnackbarWrapper: PNSnackbarWrapper,
        @Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher
) : CoroutineScope {
    private var job: Job = Job()

    private val snackBarQueue: Queue<SnackbarItem> = LinkedList()

    override val coroutineContext: CoroutineContext
        get() = mainDispatcher + job

    fun enqueue(item: SnackbarItem) {
        // This needs to be run on a single thread or synchronized - we are accessing a critical zone (`snackBarQueue`)
        launch {
            AppLog.d(AppLog.T.UTILS, "SnackbarSequencer > New item added")
            if (snackBarQueue.size == QUEUE_SIZE_LIMIT) {
                snackBarQueue.remove()
            }
            snackBarQueue.add(item)
            if (snackBarQueue.size == 1) {
                AppLog.d(AppLog.T.UTILS, "SnackbarSequencer > invoking start()")
                start()
            }
        }
    }

    private suspend fun start() {
        while (true) {
            val item = snackBarQueue.peek()
            val context: Activity? = item.info.view.get()?.context as? Activity
            if (context != null && isContextAlive(context)) {
                prepareSnackBar(context, item)?.show()
                AppLog.d(AppLog.T.UTILS, "SnackbarSequencer > before delay")
                delay(item.getSnackbarDurationMs())
                AppLog.d(AppLog.T.UTILS, "SnackbarSequencer > after delay")
            } else {
                AppLog.d(AppLog.T.UTILS,
                        "SnackbarSequencer > start context was ${if (context == null) "null" else "not alive"}")
            }
            if (snackBarQueue.peek() == item) {
                AppLog.d(AppLog.T.UTILS, "SnackbarSequencer > item removed from the queue")
                snackBarQueue.remove()
            }
            if (snackBarQueue.isEmpty()) {
                AppLog.d(AppLog.T.UTILS, "SnackbarSequencer > finishing start()")
                return
            }
        }
    }

    private fun isContextAlive(activity: Activity): Boolean {
        return !activity.isFinishing
    }

    private fun prepareSnackBar(context: Context, item: SnackbarItem): PNSnackbar? {
        return item.info.view.get()?.let { view ->
            val message = uiHelper.getTextOfUiString(context, item.info.textRes)

            val snackbar = pnSnackbarWrapper.make(view, message, item.info.duration)

            item.action?.let { actionInfo ->
                snackbar.setAction(
                        uiHelper.getTextOfUiString(context, actionInfo.textRes),
                        actionInfo.clickListener.get()
                )
            }

            item.dismissCallback.get()?.let {
                snackbar.addCallback(item.snackbarCallback)
            }

            AppLog.d(AppLog.T.UTILS, "SnackbarSequencer > prepareSnackBar message [$message]")

            return snackbar
        } ?: null.also {
            AppLog.e(AppLog.T.UTILS, "SnackbarSequencer > prepareSnackBar Unexpected null view")
        }
    }
}
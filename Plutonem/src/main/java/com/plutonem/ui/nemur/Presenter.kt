package com.plutonem.ui.nemur

import kohii.v1.exoplayer.Kohii

/**
 *
 * To bridge between [NemurOrderDetailFragment] and Data Binding event handling.
 */
interface Presenter {

    fun requireProvider(): Kohii
}

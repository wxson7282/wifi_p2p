package com.wxson.audio_player.ui.main.state

import com.wxson.audio_player.ui.main.MainViewModel

abstract class AbstractState {
    protected lateinit var context: PlayerContext
    fun setPlayerContext(context: PlayerContext) {
        this.context = context
    }

    abstract fun playHandle(viewModel: MainViewModel)
    abstract fun stopHandle(viewModel: MainViewModel)
    abstract fun pauseHandle(viewModel: MainViewModel)
    abstract fun muteHandle(viewModel: MainViewModel)
    abstract fun resumeHandle(viewModel: MainViewModel)
    abstract fun unMuteHandle(viewModel: MainViewModel)
}
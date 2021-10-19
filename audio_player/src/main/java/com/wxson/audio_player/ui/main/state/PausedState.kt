package com.wxson.audio_player.ui.main.state

import com.wxson.audio_player.ui.main.MainViewModel

class PausedState : AbstractState()  {
    override fun playHandle(viewModel: MainViewModel) {}

    override fun stopHandle(viewModel: MainViewModel) {}

    override fun pauseHandle(viewModel: MainViewModel) {
        viewModel.pause()
    }

    override fun muteHandle(viewModel: MainViewModel) {}

    override fun resumeHandle(viewModel: MainViewModel) {
        super.context.apply {
            setCurrentState(PlayerContext.resumeState)
            getCurrentState().resumeHandle(viewModel)
        }
    }

    override fun unMuteHandle(viewModel: MainViewModel) {}
}
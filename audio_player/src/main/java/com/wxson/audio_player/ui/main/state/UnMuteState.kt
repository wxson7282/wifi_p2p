package com.wxson.audio_player.ui.main.state

import com.wxson.audio_player.ui.main.MainViewModel

class UnMuteState : AbstractState() {
    override fun playHandle(viewModel: MainViewModel) {}

    override fun stopHandle(viewModel: MainViewModel) {
        super.context.apply {
            setCurrentState(PlayerContext.stoppedState)
            getCurrentState().stopHandle(viewModel)
        }
    }

    override fun pauseHandle(viewModel: MainViewModel) {
        super.context.apply {
            setCurrentState(PlayerContext.pausedState)
            getCurrentState().pauseHandle(viewModel)
        }
    }

    override fun muteHandle(viewModel: MainViewModel) {
        super.context.apply {
            setCurrentState(PlayerContext.muteState)
            getCurrentState().muteHandle(viewModel)
        }
    }

    override fun resumeHandle(viewModel: MainViewModel) {}

    override fun unMuteHandle(viewModel: MainViewModel) {
        viewModel.unMute()
    }
}
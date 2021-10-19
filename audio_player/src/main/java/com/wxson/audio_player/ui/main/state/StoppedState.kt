package com.wxson.audio_player.ui.main.state

import com.wxson.audio_player.ui.main.MainViewModel

class StoppedState : AbstractState() {
    override fun playHandle(viewModel: MainViewModel) {
        super.context.apply {
            setCurrentState(PlayerContext.playingState)
            getCurrentState().playHandle(viewModel)
        }
    }

    override fun stopHandle(viewModel: MainViewModel) {
        viewModel.stop()
    }
    override fun pauseHandle(viewModel: MainViewModel) {}
    override fun muteHandle(viewModel: MainViewModel) {}
    override fun resumeHandle(viewModel: MainViewModel) {}
    override fun unMuteHandle(viewModel: MainViewModel) {}
}

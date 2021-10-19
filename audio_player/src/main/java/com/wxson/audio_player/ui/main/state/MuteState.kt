package com.wxson.audio_player.ui.main.state

import com.wxson.audio_player.ui.main.MainViewModel

class MuteState : AbstractState() {
    override fun playHandle(viewModel: MainViewModel) {}
    override fun stopHandle(viewModel: MainViewModel) {}
    override fun pauseHandle(viewModel: MainViewModel) {}
    override fun muteHandle(viewModel: MainViewModel) {
        viewModel.mute()
    }

    override fun resumeHandle(viewModel: MainViewModel) {}
    override fun unMuteHandle(viewModel: MainViewModel) {
        super.context.apply {
            setCurrentState(PlayerContext.unMuteState)
            getCurrentState().unMuteHandle(viewModel)
        }
    }
}
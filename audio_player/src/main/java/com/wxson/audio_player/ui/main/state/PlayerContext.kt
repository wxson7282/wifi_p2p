package com.wxson.audio_player.ui.main.state

import com.wxson.audio_player.ui.main.MainViewModel

class PlayerContext(private val viewModel: MainViewModel) {

    companion object {
        val stoppedState = StoppedState()
        val playingState = PlayingState()
        val pausedState = PausedState()
        val muteState = MuteState()
        val resumeState = ResumeState()
        val unMuteState = UnMuteState()
    }

    private lateinit var currentPlayerState: AbstractState

    fun getCurrentState() : AbstractState {
        return currentPlayerState
    }

    fun setCurrentState(currentState: AbstractState) {
        currentPlayerState = currentState
        // 切换状态
        currentPlayerState.setPlayerContext(this)
    }

    // 行为委托给当前状态实例
    fun playHandle() {
        currentPlayerState.playHandle(viewModel)
    }

    fun stopHandle() {
        currentPlayerState.stopHandle(viewModel)
    }

    fun pauseHandle() {
        currentPlayerState.pauseHandle(viewModel)
    }

    fun muteHandle() {
        currentPlayerState.muteHandle(viewModel)
    }

    fun resumeHandle() {
        currentPlayerState.resumeHandle(viewModel)
    }

    fun unMuteHandle() {
        currentPlayerState.unMuteHandle(viewModel)
    }
}
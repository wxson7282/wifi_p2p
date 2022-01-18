package com.wxson.audio_receiver.ui.main

import android.media.AudioTrack
import com.wxson.p2p_comm.AudioUtil

class PcmPlayer(sampleRateInHz: Int, leftGain: Float, rightGain: Float) {
    private val audioTrack: AudioTrack = AudioUtil.initAudioTrack(sampleRateInHz)

    init {
        // 启动audioTrack
        audioTrack.setStereoVolume(leftGain, rightGain)
        audioTrack.play()
    }

    fun writePcmData(pcmData: ByteArray) {
        audioTrack.write(pcmData, 0, pcmData.size)
    }
}
package com.wxson.p2p_comm

import android.media.MediaCodec

interface IInputDataReadyListener {
    fun onInputDataReady(inputByteArray: ByteArray, codecBufferInfo: MediaCodec.BufferInfo)
}
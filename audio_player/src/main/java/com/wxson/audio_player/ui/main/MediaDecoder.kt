package com.wxson.audio_player.ui.main

import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat

object MediaDecoder {

    fun init(mime: String, format: MediaFormat, extractor: MediaExtractor, audioTrack: AudioTrack) : MediaCodec {
        val mediaCodec = MediaCodec.createDecoderByType(mime)
        val decoderCallback = DecoderCallback(extractor, audioTrack)
        mediaCodec.setCallback(decoderCallback.callback)
        mediaCodec.configure(format, null, null, 0)
        return mediaCodec
    }
}
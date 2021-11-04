package com.wxson.audio_player.ui.main

import android.media.*
import android.os.Handler
import android.os.Message
import android.util.Log
import com.wxson.p2p_comm.AudioUtil
import java.io.FileDescriptor

class PlayThreadHandler : Handler() {
    private val thisTag = this.javaClass.simpleName
    private lateinit var dummyAudioTrack: AudioTrack
    private lateinit var decoder: MediaCodec
    private lateinit var mediaFormat: MediaFormat
    private var mediaExtractor: MediaExtractor? = null
    private var mime: String? = null
    lateinit var decoderCallback: DecoderCallback

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MsgType.DUMMY_PLAYER_PLAY.ordinal -> play(msg.obj)
            MsgType.DUMMY_PLAYER_PAUSE.ordinal -> pause()
            MsgType.DUMMY_PLAYER_STOP.ordinal -> stop()
            MsgType.DUMMY_PLAYER_MUTE.ordinal -> mute()
            MsgType.DUMMY_PLAYER_RESUME.ordinal -> resume()
            MsgType.DUMMY_PLAYER_UNMUTE.ordinal -> unMute()
        }
    }

    private fun play(dataSource: Any) {
        Log.i(thisTag, "play")
        if (!setDataSource(dataSource)) {
            Log.e(thisTag, "play(dataSource: Any) error : unknown dataSource")
            return
        }
        // 从mediaExtractor取得音频轨，初始化dummyAudioTrack，初始化解码器decoder，
        // 启动dummyAudioTrack，启动解码器decoder。
        mediaExtractor?.let {
            val numTracks = it.trackCount
            for (i in 0 until numTracks) {
                val format = it.getTrackFormat(i)
                mime = format.getString(MediaFormat.KEY_MIME)
                if (mime != null && mime!!.startsWith("audio")) {
                    mediaFormat = format
                    // 建立dummy audioTrack
                    dummyAudioTrack = AudioUtil.initAudioTrack(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                    // 选择音频轨
                    it.selectTrack(i)
                    // 准备解码器
                    initDecoder(mime!!)
                    // 启动dummy audioTrack
                    dummyAudioTrack.play()
                    // 启动解码器
                    decoder.start()
                    break
                }
            }
        }
    }

    private fun pause() {
        if (dummyAudioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            dummyAudioTrack.pause()
            decoder.stop()
            decoder.release()
        }
    }

    private fun resume() {
        if (dummyAudioTrack.playState == AudioTrack.PLAYSTATE_PAUSED) {
            initDecoder(mime!!)
            dummyAudioTrack.play()
            decoder.start()
        }
    }

    private fun stop() {
        if (dummyAudioTrack.playState == AudioTrack.PLAYSTATE_PLAYING ||
            dummyAudioTrack.playState == AudioTrack.PLAYSTATE_PAUSED
        ) {
            releaseAll()
        }
    }

    private fun mute() {
        dummyAudioTrack.setVolume(0.0F)
    }

    private fun unMute() {
        dummyAudioTrack.setVolume(1.0F)
    }

    private fun setDataSource(dataSource: Any): Boolean {
        var returnValue = true
        mediaExtractor = MediaExtractor()
        when (dataSource) {
            is FileDescriptor -> {
                mediaExtractor!!.setDataSource(dataSource)
            }
            is MediaDataSource -> {
                mediaExtractor!!.setDataSource(dataSource)
            }
            is String -> {
                mediaExtractor!!.setDataSource(dataSource)
            }
            else -> returnValue = false
        }
        return returnValue
    }

    private fun initDecoder(mime: String) {
        decoder = MediaCodec.createDecoderByType(mime)
        decoderCallback = DecoderCallback(mediaExtractor!!, dummyAudioTrack)
        decoder.setCallback(decoderCallback.callback)
        decoder.configure(mediaFormat, null, null, 0)
    }

    private fun releaseAll() {
        dummyAudioTrack.stop()
        dummyAudioTrack.release()
        decoder.stop()
        decoder.release()
        mediaExtractor?.release()
        mediaExtractor = null
    }
}
package com.wxson.audio_player.ui.main

import android.content.res.AssetFileDescriptor
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.wxson.p2p_comm.AudioUtil

class Player(private val transferDataListener: ITransferDataListener) {
    private val thisTag = this.javaClass.simpleName
    private var dummyAudioTrack: AudioTrack? = null
    private var decoder: MediaCodec? = null
    private var mediaFormat: MediaFormat? = null
    private var mediaExtractor: MediaExtractor? = null
    private var sampleTime: Long? = null
    private var isMute: Boolean = false
    private var mime: String? = null

    fun assetFilePlay(afd: AssetFileDescriptor) {
        Log.i(thisTag, "assetFilePlay")
        
        mediaExtractor = MediaExtractor()
        mediaExtractor!!.setDataSource(afd)
        val numTracks = mediaExtractor!!.trackCount
        for (i in 0 until numTracks) {
            val format = mediaExtractor!!.getTrackFormat(i)
            mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime!!.startsWith("audio")) {
                mediaFormat = format
                // 建立dummy audioTrack
                dummyAudioTrack = AudioUtil.initAudioTrack(mediaFormat!!)
                // 选择音频轨
                mediaExtractor!!.selectTrack(i)
                if (sampleTime != null) {
                    // 根据保存的sampleTime寻找播放起点
                    mediaExtractor!!.seekTo(sampleTime!!, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }
                // 准备解码器
                initDecoder(mime!!)
                // 启动dummy audioTrack
                dummyAudioTrack!!.play()
                // 启动解码器
                decoder?.start()
                break
            }
        }
    }

    fun pausePlay() {
        when (dummyAudioTrack?.playState) {
            AudioTrack.PLAYSTATE_PAUSED -> {
                if (mime != null){
                    initDecoder(mime!!)
                    dummyAudioTrack!!.play()
                    decoder?.start()
                }
            }
            AudioTrack.PLAYSTATE_PLAYING -> {
                dummyAudioTrack!!.pause()
                releaseDecoder()
            }
        }
    }

    fun stop() {
        if (dummyAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING ) {
            releaseAll()
            sampleTime = null       // 清除播放时间点
        }
    }

    fun pause() {
        if (dummyAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING ) {
            sampleTime = mediaExtractor?.sampleTime  //保存当前播放时间点
            releaseAll()
        }
    }

    fun mute() {
        isMute = if (isMute) {
            dummyAudioTrack?.setVolume(1.0F)
            false
        } else {
            dummyAudioTrack?.setVolume(0.0F)
            true
        }
    }

    fun releaseAll() {
        dummyAudioTrack?.stop()
        dummyAudioTrack?.release()
        dummyAudioTrack = null
        decoder?.stop()
        decoder?.release()
        decoder = null
        mediaExtractor?.release()
        mediaExtractor = null
    }

    private fun releaseDecoder() {
        decoder?.stop()
        decoder?.release()
        decoder = null
    }

    private fun initDecoder(mime: String) {
        decoder = MediaCodec.createDecoderByType(mime)
        val decoderCallback = DecoderCallback(mediaExtractor!!, dummyAudioTrack!!)
        decoderCallback.setTransferDataListener(transferDataListener)
        decoder?.setCallback(decoderCallback.callback)
        decoder?.configure(mediaFormat, null, null, 0)
    }

}
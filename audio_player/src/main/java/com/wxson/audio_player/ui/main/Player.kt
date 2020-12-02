package com.wxson.audio_player.ui.main

import android.content.res.AssetFileDescriptor
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.wxson.p2p_comm.AudioUtil

class Player {
    private val thisTag = this.javaClass.simpleName
    private var dummyAudioTrack: AudioTrack? = null
    private var decoder: MediaCodec? = null
    private var mediaFormat: MediaFormat? = null
    private var extractor: MediaExtractor? = null
    private var sampleTime: Long? = null

    fun assetFilePlay(afd: AssetFileDescriptor) {
        Log.i(thisTag, "assetFilePlay")


        extractor = MediaExtractor()
        extractor!!.setDataSource(afd)
        val numTracks = extractor!!.trackCount
        for (i in 0 until numTracks) {
            val format = extractor!!.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio")) {
                mediaFormat = format
                // 建立dummy audioTrack
                dummyAudioTrack = AudioUtil.initAudioTrack(mediaFormat!!)
                // 选择音频轨
                extractor!!.selectTrack(i)
                if (sampleTime != null) {
                    // 根据保存的sampleTime寻找播放起点
                    extractor!!.seekTo(sampleTime!!, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }
                // 准备解码器
                decoder = MediaDecoder.init(mime, mediaFormat!!, extractor!!, dummyAudioTrack!!)
                // 启动dummy audioTrack
                dummyAudioTrack!!.play()
                // 启动解码器
                decoder?.start()
                break
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
            sampleTime = extractor?.sampleTime  //保存当前播放时间点
            releaseAll()
        }
    }

    fun releaseAll() {
        dummyAudioTrack?.stop()
        dummyAudioTrack?.release()
        dummyAudioTrack = null
        decoder?.stop()
        decoder?.release()
        decoder = null
        extractor?.release()
        extractor = null
    }
}
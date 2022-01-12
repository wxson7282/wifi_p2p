package com.wxson.p2p_comm

import android.media.*

object AudioUtil {
    /**
     * get audio tracks from media in the file
     */
    fun getAudioTracks(filePath: String) : MutableList<MediaFormat> {
        val mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(filePath)
        val numTracks = mediaExtractor.trackCount
        val trackFormats: MutableList<MediaFormat> = mutableListOf()
        for (i in 0 until numTracks) {
            val mediaFormat = mediaExtractor.getTrackFormat(i)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio")){
                trackFormats.add(mediaFormat)
            }
        }
        return trackFormats
    }

//    fun initAudioTrack(mediaFormat: MediaFormat) : AudioTrack {
//        val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
//        val channels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
//        val buffSize = AudioTrack.getMinBufferSize(sampleRate,channels, AudioFormat.ENCODING_PCM_16BIT)
//        val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
//        val audioFormat = AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
//        return AudioTrack(audioAttributes, audioFormat, buffSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
//    }

    fun initAudioTrack(sampleRateInHz: Int) : AudioTrack {
        val buffSize = AudioTrack.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val audioFormat = AudioFormat.Builder().setSampleRate(sampleRateInHz).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        return AudioTrack(audioAttributes, audioFormat, buffSize * 4, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
    }
}
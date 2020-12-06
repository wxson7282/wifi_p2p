package com.wxson.audio_player.ui.main

import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.wxson.p2p_comm.PcmTransferData

class DecoderCallback(extractor: MediaExtractor, audioTrack: AudioTrack) {
    private val thisTag = this.javaClass.simpleName
    // to inform MainViewModel onOutputBufferAvailable
    private lateinit var transferDataListener: ITransferDataListener

    val callback = object : MediaCodec.Callback() {

        override fun onInputBufferAvailable(codec: MediaCodec, bufferIndex: Int) {
//            Log.i(thisTag, "onInputBufferAvailable")
            try {
                val decoderInputBuffer = codec.getInputBuffer(bufferIndex)
                if (decoderInputBuffer != null) {
                    val sampleSize = extractor.readSampleData(decoderInputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(bufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, bufferIndex: Int, bufferInfo: MediaCodec.BufferInfo) {
//            Log.i(thisTag, "onOutputBufferAvailable")
            try {
                if ((bufferInfo.flags.and(MediaCodec.BUFFER_FLAG_END_OF_STREAM)) != 0) {
                    // end of stream
                    extractor.release()
                    codec.stop()
                    codec.release()
                } else {
                    val outputBuffer = codec.getOutputBuffer(bufferIndex)
                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer?.get(pcmData)
                    // send out pcmTransferData
                    transferDataListener.onTransferDataReady(PcmTransferData(codec.outputFormat, pcmData))
                    audioTrack.write(pcmData, 0, pcmData.size)
                    codec.releaseOutputBuffer(bufferIndex, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(thisTag, "onError")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.i(thisTag, "onOutputFormatChanged")
        }
    }

    fun setTransferDataListener(listener: ITransferDataListener) {
        transferDataListener = listener
    }
}
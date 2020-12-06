package com.wxson.audio_player.ui.main

import com.wxson.p2p_comm.PcmTransferData

interface ITransferDataListener {
    /**
     *  to inform MainViewModel onOutputBufferAvailable in DecoderCallback
     */
    fun onTransferDataReady(pcmTransferData: PcmTransferData)
}
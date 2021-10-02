package com.wxson.p2p_comm

import java.io.Serializable

class PcmTransferData(val sampleRateInHz: Int, val pcmData: ByteArray) : Serializable {}

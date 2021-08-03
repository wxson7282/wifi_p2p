package com.wxson.p2p_comm

import android.media.MediaFormat
import java.io.Serializable

class PcmTransferData(val mediaFormat: MediaFormat, val pcmData: ByteArray) : Serializable {
}
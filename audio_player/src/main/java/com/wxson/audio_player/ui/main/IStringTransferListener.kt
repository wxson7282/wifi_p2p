package com.wxson.audio_player.ui.main

import java.net.InetAddress

interface IStringTransferListener {
    fun onStringArrived(arrivedString: String, clientInetAddress: InetAddress)
    fun onMsgTransfer(msgType: String, msg: String)
}
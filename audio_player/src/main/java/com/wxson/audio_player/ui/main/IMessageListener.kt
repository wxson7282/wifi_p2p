package com.wxson.audio_player.ui.main

import java.net.InetAddress

interface IMessageListener {
    fun onRemoteMsgArrived(arrivedString: String, clientInetAddress: InetAddress)
    fun onLocalMsgOccurred(msgType: String, msg: String)
}
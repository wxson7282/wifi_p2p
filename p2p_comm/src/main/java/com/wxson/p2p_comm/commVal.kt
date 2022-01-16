package com.wxson.p2p_comm

object Val {
    const val msgClientConnected = "客户端已连接"
    const val msgClientDisconnectRequest = "客户端中断连接请求"
    const val msgDisconnectReply = "终止连接应答"
    const val msgStopRunnable = "stopRunnable"
    const val msgCodeByteArray = 0x123
    const val msgThreadInterrupted = 0x334
    const val AudioType = 0xfa.toByte()
    const val TextType = 0xfc.toByte()
    const val AudioBuffCapacity = 16384
    const val InputBuffCapacity = 16384
    const val CacheBuffCapacity = 16384
}
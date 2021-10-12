package com.wxson.audio_player.ui.main

import android.os.Handler
import android.os.Looper
import android.util.Log

class DummyPlayerRunnable(
    private val transferDataListener: ITransferDataListener) : Runnable {
    private val thisTag = this.javaClass.simpleName

    var threadHandler = Handler { false }

    override fun run() {
        Log.i(thisTag, "run")
        // 为当前线程初始化Looper
        Looper.prepare()
        threadHandler = PlayThreadHandler(transferDataListener)
        // 启动Looper
        Looper.loop()
    }
}
package com.wxson.audio_player.ui.main

import android.os.Looper
import android.util.Log

class DummyPlayerRunnable : Runnable {
    private val thisTag = this.javaClass.simpleName

//    var threadHandler = Handler { false }
    lateinit var threadHandler: PlayThreadHandler

    override fun run() {
        Log.i(thisTag, "run")
        // 为当前线程初始化Looper
        Looper.prepare()
        threadHandler = PlayThreadHandler()
        // 启动Looper
        Looper.loop()
    }
}
package com.wxson.p2p_comm

import android.os.Looper
import androidx.lifecycle.MutableLiveData

object Util {

    fun <T> sendLiveData(liveData: MutableLiveData<T>, value: T) {
        if (isMainThread())
            liveData.value =  value
        else
            liveData.postValue(value)
    }

    private fun isMainThread() : Boolean {
        return Thread.currentThread() == Looper.getMainLooper().thread
    }

}
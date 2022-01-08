package com.wxson.p2p_comm

import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import java.net.Inet4Address
import java.net.NetworkInterface


object Util {

    private val runningTag = this.javaClass.simpleName

    fun <T> sendLiveData(liveData: MutableLiveData<T>, value: T) {
        if (isMainThread())
            liveData.value =  value
        else
            liveData.postValue(value)
    }

    private fun isMainThread() : Boolean {
        return Thread.currentThread() == Looper.getMainLooper().thread
    }

    fun intToByteArray(input: Int): ByteArray {
        val returnValue = byteArrayOf(0, 0, 0, 0)
        for (index in 0 until 3) {
            //转换顺序从右向左
            returnValue[index] = (input.shr(8 * index) and 0xff).toByte()
        }
        return returnValue
    }

    fun byteArrayToInt(input: ByteArray): Int {
        var returnValue: Int = 0
        for (index in 0 until 3) {
            returnValue += (input[index].toInt() and 0xff).shl(8 * index)
        }
        return returnValue
    }

    fun pcmTransferDataToByteArray(input: PcmTransferData): ByteArray {
        return intToByteArray(input.sampleRateInHz) + intToByteArray(input.frameCount) + (input.pcmData)
    }

    fun byteArrayToPcmTransferData(input: ByteArray): PcmTransferData {
        return PcmTransferData(byteArrayToInt(input.copyOfRange(0,3)),
            input.copyOfRange(8, input.size - 1),
            byteArrayToInt(input.copyOfRange(4, 7)))
    }

    fun getLocalP2pHostIp(): String {
        try {
            val enumNetworkInterface = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in enumNetworkInterface) {
                if (networkInterface.name == "p2p0") {
                    val enumInetAddress = networkInterface.inetAddresses
                    for (inetAddress in enumInetAddress) {
                        if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress) {
                            if (inetAddress is Inet4Address) {
                                Log.i(runningTag, "Ip address: ${inetAddress.hostAddress}")
                                return inetAddress.hostAddress.toString();
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}
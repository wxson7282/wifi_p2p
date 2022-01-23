package com.wxson.p2p_comm

import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

fun WifiP2pManager.setDeviceName(channel: WifiP2pManager.Channel, deviceName: String) {
    val thisTag = this.javaClass.simpleName
    try {
        val paramTypes0 = WifiP2pManager.Channel::class.java
        val paramTypes1 = String::class.java
        val paramTypes2 = WifiP2pManager.ActionListener::class.java

        val setDeviceName: Method = this.javaClass.getMethod(
            "setDeviceName", paramTypes0, paramTypes1, paramTypes2
        )
        setDeviceName.isAccessible = true
        setDeviceName.invoke(this, channel,
            deviceName,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(thisTag, "setDeviceName succeeded")
                }
                override fun onFailure(reason: Int) {
                    Log.e(thisTag, "setDeviceName failed")
                }
            })
    } catch (e: NoSuchMethodException) {
        e.printStackTrace()
    } catch (e: IllegalAccessException) {
        e.printStackTrace()
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
    } catch (e: InvocationTargetException) {
        e.printStackTrace()
    }
}

package com.wxson.p2p_comm

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Parcelable
import android.util.Log
import androidx.core.app.ActivityCompat
import pub.devrel.easypermissions.EasyPermissions
import java.util.*

class DirectBroadcastReceiver(
    private val wifiP2pManager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val directActionListener: IDirectActionListener
) : BroadcastReceiver() {

    private val runningTag = this.javaClass.simpleName

    companion object {
        fun getIntentFilter(): IntentFilter {
            Log.d("DirectBroadcastReceiver", "getIntentFilter")
            val intentFilter = IntentFilter()
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
            return intentFilter
        }
    }


    override fun onReceive(context: Context, intent: Intent) {
        Log.d(runningTag, "onReceive intent.action=${intent.action}")
        when (intent.action){
            // indicate whether Wi-Fi p2p is enabled or disabled
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    directActionListener.onWifiP2pEnabled(true)
                } else {
                    directActionListener.onWifiP2pEnabled(false)
                    val wifiP2pDeviceList: List<WifiP2pDevice> = ArrayList()
                    directActionListener.onPeersAvailable(wifiP2pDeviceList)
                }
            }
            // indicating that the available peer list has changed. This can be sent as a result of peers being found, lost or updated.
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    val perms = Manifest.permission.ACCESS_FINE_LOCATION
                    Log.i(runningTag, "申请ACCESS_FINE_LOCATION权限")
                    // Do not have permissions, request them now
                    EasyPermissions.requestPermissions(context as Activity, "本APP运行必须取得位置控制权限", 1, perms)
                    return
                }
                wifiP2pManager.requestPeers(channel) { wifiP2pDeviceList -> directActionListener.onPeersAvailable(wifiP2pDeviceList.deviceList) }
            }
            // indicating that peer discovery has either started or stopped
            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED)
                //如果peer discovery已经结束
                if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    directActionListener.onP2pDiscoveryStopped()
                }
            }
            // indicating that the state of Wi-Fi p2p connectivity has changed.
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (networkInfo != null && networkInfo.isConnected) {
                    wifiP2pManager.requestConnectionInfo(channel) { wifiP2pInfo -> directActionListener.onConnectionInfoAvailable(wifiP2pInfo) }
                    Log.i(runningTag, "已连接p2p设备")
                } else {
                    Log.i(runningTag, "与p2p设备已断开连接")
                    directActionListener.onDisconnection()
                }
            }
            // indicating that this device details have changed
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                directActionListener.onSelfDeviceAvailable(intent.getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice)
            }
        }
    }
}
package com.wxson.p2p_comm

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager.ChannelListener

interface IDirectActionListener : ChannelListener {

    fun onWifiP2pEnabled(enabled: Boolean)

    fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo)

    fun onDisconnection()

    fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice)

    fun onPeersAvailable(deviceList: Collection<WifiP2pDevice>)

    fun onP2pDiscoveryStopped()
}
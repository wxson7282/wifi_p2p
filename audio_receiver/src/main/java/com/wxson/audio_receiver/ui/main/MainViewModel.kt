package com.wxson.audio_receiver.ui.main

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wxson.p2p_comm.DirectBroadcastReceiver
import com.wxson.p2p_comm.IDirectActionListener
import com.wxson.p2p_comm.ModelMsg
import java.lang.ref.WeakReference
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application), WifiP2pManager.ChannelListener, IDirectActionListener {
    private val thisTag = this.javaClass.simpleName
    private val app = application
    private val wifiP2pManager: WifiP2pManager
    private val channel: WifiP2pManager.Channel
    private val receiver: BroadcastReceiver
    private var mainHandler: Handler
    private lateinit var clientThread: ClientThread
    private var wifiP2pEnabled = false
    private val wifiP2pDeviceList = ArrayList<WifiP2pDevice>()
    val deviceAdapter: DeviceAdapter
    private lateinit var wifiP2pDevice: WifiP2pDevice

    //region LiveData
//    private val wifiP2pInfoLiveData = MutableLiveData<WifiP2pInfo>()
//    fun getServerMsg(): LiveData<WifiP2pInfo> {
//        return wifiP2pInfoLiveData
//    }
//
//    private val localMsgLiveData = MutableLiveData<String>()
//    fun getLocalMsg(): LiveData<String>{
//        return localMsgLiveData
//    }
//
//    private val connectStatusLiveData = MutableLiveData<Boolean>()
//    fun getConnectStatus(): LiveData<Boolean> {
//        return connectStatusLiveData
//    }

    private val modelMsgLiveData = MutableLiveData<ModelMsg>()
    fun getModelMsg(): LiveData<ModelMsg> {
        return modelMsgLiveData
    }
    //endregion

    class MainHandler(private var viewModel: WeakReference<MainViewModel>) : Handler(){
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                0x123 -> viewModel.get()?.modelMsgLiveData?.postValue(ModelMsg(MsgType.MSG.ordinal, "remote msg:" + msg.obj.toString()))
                0x124 -> viewModel.get()?.modelMsgLiveData?.postValue(ModelMsg(MsgType.MSG.ordinal, "local msg:" + msg.obj.toString()))
            }
        }
    }

    init {
        Log.i(thisTag, "init")
        mainHandler = MainHandler(WeakReference(this))
        wifiP2pManager =  app.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(app, Looper.getMainLooper(), this)
        receiver = DirectBroadcastReceiver(wifiP2pManager, channel, this)
        app.registerReceiver(receiver, DirectBroadcastReceiver.getIntentFilter())
        wifiP2pDeviceList.clear()
        deviceAdapter = DeviceAdapter(wifiP2pDeviceList)
        deviceAdapter.setClickListener(object : DeviceAdapter.OnClickListener {
            override fun onItemClick(position: Int) {
                wifiP2pDevice = wifiP2pDeviceList[position]
//                localMsgLiveData.postValue(wifiP2pDeviceList.get(position).deviceName + "将要连接")
                modelMsgLiveData.postValue(ModelMsg(MsgType.MSG.ordinal, wifiP2pDevice.deviceName + "将要连接"))

                connect()
            }
        })
        mainHandler = MainHandler(WeakReference(this))
    }

    override fun onCleared() {
        Thread(clientThread).interrupt()
        clientThread.socket.close()
        app.unregisterReceiver(receiver)
        super.onCleared()
    }

    fun startWifiSetting() {
        app.applicationContext.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
    }

    fun startDiscoverPeers() {
        if (!wifiP2pEnabled) {
//            localMsgLiveData.postValue("需要先打开Wifi")
            modelMsgLiveData.postValue(ModelMsg(MsgType.MSG.ordinal, "需要先打开Wifi"))
            return
        }
//        localMsgLiveData.postValue("正在搜索附近设备")
        modelMsgLiveData.postValue(ModelMsg(MsgType.SHOW_LOADING_DIALOG.ordinal, "正在搜索附近设备"))
        wifiP2pDeviceList.clear()
        deviceAdapter.notifyDataSetChanged()
        //搜寻附近带有 Wi-Fi P2P 的设备
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
//            localMsgLiveData.postValue("需要申请ACCESS_FINE_LOCATION权限")
            modelMsgLiveData.postValue(ModelMsg(MsgType.MSG.ordinal, "需要申请ACCESS_FINE_LOCATION权限"))
            return
        }
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
//                localMsgLiveData.postValue("discoverPeers success")
                modelMsgLiveData.postValue(ModelMsg(MsgType.MSG.ordinal, "discoverPeers success"))
            }

            override fun onFailure(reasonCode: Int) {
//                localMsgLiveData.postValue("discoverPeers failure")
                modelMsgLiveData.postValue(ModelMsg(MsgType.MSG.ordinal, "discoverPeers failure"))
                modelMsgLiveData.postValue(ModelMsg(MsgType.CANCEL_LOADING_DIALOG.ordinal, ""))
            }
        })
    }

    //region private method
    private fun startClientThread(hostIp: String, port: Int) {
        clientThread = ClientThread(mainHandler, hostIp, port)
        Thread(clientThread).start()
    }

    private fun connect() {
        val config = WifiP2pConfig()
        config.deviceAddress = wifiP2pDevice.deviceAddress
        config.wps.setup = WpsInfo.PBC
//        localMsgLiveData.postValue("正在连接")
        modelMsgLiveData.postValue(ModelMsg(MsgType.MSG.ordinal, "正在连接"))
        // permission check
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
//            localMsgLiveData.postValue("需要申请ACCESS_FINE_LOCATION权限")
            modelMsgLiveData.postValue(ModelMsg(MsgType.MSG.ordinal, "需要申请ACCESS_FINE_LOCATION权限"))
            return
        }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
//                connectStatusLiveData.postValue(true)
                modelMsgLiveData.postValue(ModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, true))
                Log.i(thisTag, "connect onSuccess")
            }

            override fun onFailure(reason: Int) {
//                connectStatusLiveData.postValue(false)
                modelMsgLiveData.postValue(ModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, false))
                Log.e(thisTag, "连接失败 ${getGetConnectFailureReason(reason)}")
                modelMsgLiveData.postValue(ModelMsg(MsgType.DISMISS_LOADING_DIALOG.ordinal, ""))
            }
        })
    }

    private fun getGetConnectFailureReason(reasonCode: Int) : String {
        return when (reasonCode) {
            WifiP2pManager.ERROR -> "ERROR"
            WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
            WifiP2pManager.BUSY -> "BUSY"
            WifiP2pManager.NO_SERVICE_REQUESTS -> "NO_SERVICE_REQUESTS"
            else -> "UNKNOWN ERROR"
        }
    }

    //endregion

    //region wifi p2p events
    override fun onWifiP2pEnabled(enabled: Boolean) {
        wifiP2pEnabled = enabled
    }

    override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
        //关闭“连接中”信息显示
        modelMsgLiveData.postValue(ModelMsg(MsgType.DISMISS_LOADING_DIALOG.ordinal, ""))
        //显示WifiP2pInfo
        modelMsgLiveData.postValue(ModelMsg(MsgType.SHOW_WIFI_P2P_INFO.ordinal, wifiP2pInfo))
        //显示选中的wifiP2pDevice
        //***********************





        wifiP2pDeviceList.clear()
        deviceAdapter.notifyDataSetChanged()



        TODO("Not yet implemented")
    }

    override fun onDisconnection() {
        TODO("Not yet implemented")
    }

    override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
        Log.i(thisTag, "onPeersAvailable" )
        modelMsgLiveData.postValue(ModelMsg(MsgType.SHOW_SELF_DEVICE_INFO.ordinal, wifiP2pDevice))
    }

    override fun onPeersAvailable(deviceList: Collection<WifiP2pDevice>) {
        Log.i(thisTag, "onPeersAvailable :" + wifiP2pDeviceList.size)
        wifiP2pDeviceList.clear()
        wifiP2pDeviceList.addAll(deviceList)
        deviceAdapter.notifyDataSetChanged()
        modelMsgLiveData.postValue(ModelMsg(MsgType.CANCEL_LOADING_DIALOG.ordinal, ""))
        if (wifiP2pDeviceList.size == 0) {
            Log.e(thisTag, "No devices found")
        }
    }

    override fun onP2pDiscoveryStopped() {
        TODO("Not yet implemented")
    }

    override fun onChannelDisconnected() {
        TODO("Not yet implemented")
    }
    //endregion

    // TODO: Implement the ViewModel
}
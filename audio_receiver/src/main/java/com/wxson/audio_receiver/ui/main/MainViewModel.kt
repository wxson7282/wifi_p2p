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
import com.wxson.audio_receiver.R
import com.wxson.p2p_comm.DirectBroadcastReceiver
import com.wxson.p2p_comm.IDirectActionListener
import com.wxson.p2p_comm.ViewModelMsg
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
    private val msgLiveData = MutableLiveData<ViewModelMsg>()
    fun getModelMsg(): LiveData<ViewModelMsg> {
        return msgLiveData
    }
    //endregion

    class MainHandler(private var viewModel: WeakReference<MainViewModel>) : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MsgType.ARRIVED_STRING.ordinal ->
                    viewModel.get()?.msgLiveData?.postValue(ViewModelMsg(MsgType.MSG.ordinal, "remote msg:" + msg.obj.toString()))
                MsgType.LOCAL_MSG.ordinal ->
                    viewModel.get()?.msgLiveData?.postValue(ViewModelMsg(MsgType.MSG.ordinal, "local msg:" + msg.obj.toString()))
                MsgType.PCM_TRANSFER_DATA.ordinal -> TODO("Not yet implemented")
            }
        }
    }

    init {
        Log.i(thisTag, "init")
//        mainHandler = MainHandler(WeakReference(this))
        wifiP2pManager =  app.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(app, Looper.getMainLooper(), this)
        receiver = DirectBroadcastReceiver(wifiP2pManager, channel, this)
        app.registerReceiver(receiver, DirectBroadcastReceiver.getIntentFilter())
        wifiP2pDeviceList.clear()
        deviceAdapter = DeviceAdapter(wifiP2pDeviceList)
        deviceAdapter.setClickListener(object : DeviceAdapter.OnClickListener {
            override fun onItemClick(position: Int) {
                wifiP2pDevice = wifiP2pDeviceList[position]
                msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, wifiP2pDevice.deviceName + "将要连接"))

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
            msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, "需要先打开Wifi"))
            return
        }
        msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_LOADING_DIALOG.ordinal, "正在搜索附近设备"))
        clearWifiP2pDeviceList()
        //搜寻附近带有 Wi-Fi P2P 的设备
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
            msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, "需要申请ACCESS_FINE_LOCATION权限"))
            return
        }
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, "discoverPeers success"))
            }

            override fun onFailure(reasonCode: Int) {
                msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, "discoverPeers failure"))
                msgLiveData.postValue(ViewModelMsg(MsgType.CANCEL_LOADING_DIALOG.ordinal, ""))
            }
        })
    }

    //region private method
    private fun startClientThread(hostIp: String) {
        clientThread = ClientThread(mainHandler, hostIp, app.resources.getInteger(R.integer.portNumber))
        Thread(clientThread).start()
    }

    private fun connect() {
        val config = WifiP2pConfig()
        config.deviceAddress = wifiP2pDevice.deviceAddress
        config.wps.setup = WpsInfo.PBC
        msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, "正在连接"))
        // permission check
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
            msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, "需要申请ACCESS_FINE_LOCATION权限"))
            return
        }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, true))
                Log.i(thisTag, "connect onSuccess")
            }

            override fun onFailure(reason: Int) {
                msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, false))
                Log.e(thisTag, "连接失败 ${getGetConnectFailureReason(reason)}")
                msgLiveData.postValue(ViewModelMsg(MsgType.DISMISS_LOADING_DIALOG.ordinal, ""))
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

    private fun clearWifiP2pDeviceList() {
        clearWifiP2pDeviceList()
    }

    //endregion

    //region wifi p2p events
    override fun onWifiP2pEnabled(enabled: Boolean) {
        Log.i(thisTag, "onWifiP2pEnabled")
        wifiP2pEnabled = enabled
    }

    override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
        Log.i(thisTag, "onConnectionInfoAvailable")
        //关闭“连接中”信息显示
        msgLiveData.postValue(ViewModelMsg(MsgType.DISMISS_LOADING_DIALOG.ordinal, ""))
        //显示WifiP2pInfo
        msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_WIFI_P2P_INFO.ordinal, wifiP2pInfo))
        //显示选中的wifiP2pDevice
        msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_REMOTE_DEVICE_INFO.ordinal, wifiP2pDevice))
        //判断本机为非群主，且群已经建立
        if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
            //建立并启动ClientThread
            startClientThread(wifiP2pInfo.groupOwnerAddress.hostAddress)
            //向对方发送连接成功消息
            val msg = Message()
            msg.what = 0x345
            msg.what = MsgType.SEND_MSG_TO_REMOTE.ordinal
            msg.obj = "connected"
            clientThread.outputHandler.sendMessage(msg)

        }
        //***********************





        wifiP2pDeviceList.clear()
        deviceAdapter.notifyDataSetChanged()



        TODO("Not yet implemented")
    }

    override fun onDisconnection() {
        Log.i(thisTag, "onDisconnection")
        msgLiveData.postValue(ViewModelMsg(MsgType.SET_BUTTON_DISABLED.ordinal,"btnDisconnect"))
        msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, "已断开连接"))
        clearWifiP2pDeviceList()
        msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_WIFI_P2P_INFO.ordinal, null))
        msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, false))
        msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_SELF_DEVICE_INFO.ordinal, null))
    }

    override fun onSelfDeviceAvailable(selfDevice: WifiP2pDevice) {
        Log.i(thisTag, "onPeersAvailable" )
        msgLiveData.postValue(ViewModelMsg(MsgType.SHOW_SELF_DEVICE_INFO.ordinal, selfDevice))
    }

    override fun onPeersAvailable(deviceList: Collection<WifiP2pDevice>) {
        Log.i(thisTag, "onPeersAvailable :" + wifiP2pDeviceList.size)
        wifiP2pDeviceList.clear()
        wifiP2pDeviceList.addAll(deviceList)
        deviceAdapter.notifyDataSetChanged()
        msgLiveData.postValue(ViewModelMsg(MsgType.CANCEL_LOADING_DIALOG.ordinal, ""))
        if (wifiP2pDeviceList.size == 0) {
            Log.e(thisTag, "No devices found")
        }
    }

    override fun onP2pDiscoveryStopped() {
        Log.i(thisTag, "onP2pDiscoveryStopped")
        if (wifiP2pDeviceList.size == 0) {
            //再度搜寻附近带有 Wi-Fi P2P 的设备
            startDiscoverPeers()
        }
    }

    override fun onChannelDisconnected() {
        Log.i(thisTag, "onChannelDisconnected")
    }
    //endregion

    // TODO: Implement the ViewModel
}
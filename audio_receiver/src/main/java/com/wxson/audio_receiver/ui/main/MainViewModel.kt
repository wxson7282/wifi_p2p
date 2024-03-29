package com.wxson.audio_receiver.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ChannelListener
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wxson.audio_receiver.R
import com.wxson.p2p_comm.*
import java.lang.ref.WeakReference
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application), ChannelListener, IDirectActionListener {

    private val thisTag = this.javaClass.simpleName
    private val app = application
    private val wifiP2pManager: WifiP2pManager
    private val channel: WifiP2pManager.Channel
    private val receiver: BroadcastReceiver
    private var mainHandler: Handler
    private lateinit var connectRunnable: ConnectRunnable
    private lateinit var connectThread: Thread
    private var wifiP2pEnabled = false
    private val wifiP2pDeviceList = ArrayList<WifiP2pDevice>()
    val deviceAdapter: DeviceAdapter
    private var remoteDevice: WifiP2pDevice? = null
    private var pcmPlayer: PcmPlayer? = null
    var leftGain = 1.0f
    var rightGain = 1.0f

    //region LiveData
    private val msgLiveData = MutableLiveData<ViewModelMsg>()
    fun getModelMsg(): LiveData<ViewModelMsg> {
        return msgLiveData
    }
    //endregion

    //region Handler
    class MainHandler(private var viewModel: WeakReference<MainViewModel>) : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MsgType.ARRIVED_STRING.ordinal -> {
//                    viewModel.get()?.sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "remote msg:" + msg.obj.toString()))
                }
                MsgType.PCM_TRANSFER_DATA.ordinal -> viewModel.get()?.playPcmData(msg.obj as PcmTransferData)
                MsgType.LOCAL_MSG.ordinal ->
                    viewModel.get()?.sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "local msg:" + msg.obj.toString()))
            }
        }
    }
    //endregion

    //region init & onCleared
    init {
        Log.i(thisTag, "init")
        wifiP2pManager =  app.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(app, Looper.getMainLooper(), this)
        // set a fixed name to the self device used in wifi p2p group
        wifiP2pManager.setDeviceName(channel, app.getString(R.string.app_name))

        receiver = DirectBroadcastReceiver(wifiP2pManager, channel, this)
        app.registerReceiver(receiver, DirectBroadcastReceiver.getIntentFilter())
        wifiP2pDeviceList.clear()
        deviceAdapter = DeviceAdapter(wifiP2pDeviceList)
        deviceAdapter.setClickListener(object : DeviceAdapter.OnClickListener {
            override fun onItemClick(position: Int) {
                remoteDevice = wifiP2pDeviceList[position]
                sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, remoteDevice!!.deviceName + "将要连接"))
                connect()
            }
        })
        mainHandler = MainHandler(WeakReference(this))
    }

    override fun onCleared() {
        if (this::connectThread.isInitialized) {
            if (connectThread.isAlive){
                val msg = Message()
                msg.what = MsgType.LOCAL_MSG.ordinal
                msg.obj = Val.msgStopRunnable
                connectRunnable.connectThreadHandler.sendMessage(msg)
            }
        }
        app.unregisterReceiver(receiver)
        super.onCleared()
    }
    //endregion++


    //region public method
    fun startDiscoverPeers() {
        if (!wifiP2pEnabled) {
            sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "需要先打开Wifi"))
            return
        }
        sendMsgLiveData(ViewModelMsg(MsgType.SHOW_LOADING_DIALOG.ordinal, "正在搜索附近设备"))
        clearWifiP2pDeviceList()
        //搜寻附近带有 Wi-Fi P2P 的设备
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
            sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "需要申请ACCESS_FINE_LOCATION权限"))
            return
        }
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "discoverPeers success"))
            }

            override fun onFailure(reasonCode: Int) {
                sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "discoverPeers failure. reasonCode=$reasonCode"))
                sendMsgLiveData(ViewModelMsg(MsgType.CANCEL_LOADING_DIALOG.ordinal, ""))
            }
        })
    }

    fun disconnect() {
        //向对方发送连接断开请求
        val msg = Message()
        msg.what = MsgType.SEND_MSG_TO_REMOTE.ordinal
        msg.obj = Val.msgClientDisconnectRequest
        connectRunnable.connectThreadHandler.sendMessage(msg)
        sendMsgLiveData(ViewModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, false))
        connectThread.join(300)
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                Log.i(thisTag, "disconnect onFailure:$reasonCode")
            }

            override fun onSuccess() {
                Log.i(thisTag, "disconnect onSuccess")
            }
        })
        //停止connectRunnable
        val msg2 = Message()
        msg2.what = MsgType.LOCAL_MSG.ordinal
        msg2.obj = Val.msgStopRunnable
        connectRunnable.connectThreadHandler.sendMessage(msg2)
    }
    //endregion

    //region private method
    private fun startConnectThread(hostIp: String) {
        Log.i(thisTag, "startConnectThread")
        connectRunnable = ConnectRunnable(mainHandler, hostIp, app.resources.getInteger(R.integer.portNumber))
        connectThread = Thread(connectRunnable)
        connectThread.start()
        Thread.sleep(100)
    }

    private fun connect() {
        val config = WifiP2pConfig()
        config.deviceAddress = remoteDevice!!.deviceAddress
        config.wps.setup = WpsInfo.PBC
        sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "正在连接"))
        // permission check
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
            sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "需要申请ACCESS_FINE_LOCATION权限"))
            return
        }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                sendMsgLiveData(ViewModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, true))
                Log.i(thisTag, "connect onSuccess")
            }

            override fun onFailure(reason: Int) {
                sendMsgLiveData(ViewModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, false))
                Log.e(thisTag, "连接失败 ${getGetConnectFailureReason(reason)}")
                sendMsgLiveData(ViewModelMsg(MsgType.DISMISS_LOADING_DIALOG.ordinal, ""))
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

    @SuppressLint("NotifyDataSetChanged")
    private fun clearWifiP2pDeviceList() {
        wifiP2pDeviceList.clear()
        deviceAdapter.notifyDataSetChanged()
    }

    private fun playPcmData(pcmTransferData: PcmTransferData) {
        if (pcmPlayer == null) {
            pcmPlayer = PcmPlayer(pcmTransferData.sampleRateInHz, leftGain, rightGain)
        }
        pcmPlayer?.writePcmData(pcmTransferData.pcmData)
    }

    private fun sendMsgLiveData(viewModelMsg: ViewModelMsg) {
        Util.sendLiveData(msgLiveData, viewModelMsg)
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
        sendMsgLiveData(ViewModelMsg(MsgType.DISMISS_LOADING_DIALOG.ordinal, ""))
        //显示WifiP2pInfo
        sendMsgLiveData(ViewModelMsg(MsgType.SHOW_WIFI_P2P_INFO.ordinal, wifiP2pInfo))
        //显示选中的wifiP2pDevice
        if (remoteDevice != null) {
            sendMsgLiveData(ViewModelMsg(MsgType.SHOW_REMOTE_DEVICE_INFO.ordinal, remoteDevice))
        }
        //判断本机为非群主，且群已经建立
        if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
            startConnectThread(wifiP2pInfo.groupOwnerAddress.hostAddress)
            //向对方发送连接成功消息
            val msg = Message()
            msg.what = MsgType.SEND_MSG_TO_REMOTE.ordinal
            msg.obj = Val.msgClientConnected
            connectRunnable.connectThreadHandler.sendMessage(msg)
        }
    }

    override fun onDisconnection() {
        Log.i(thisTag, "onDisconnection")
        sendMsgLiveData(ViewModelMsg(MsgType.MSG.ordinal, "已断开连接"))
        clearWifiP2pDeviceList()
        sendMsgLiveData(ViewModelMsg(MsgType.SHOW_WIFI_P2P_INFO.ordinal, null))
        sendMsgLiveData(ViewModelMsg(MsgType.SHOW_CONNECT_STATUS.ordinal, false))
        sendMsgLiveData(ViewModelMsg(MsgType.SHOW_REMOTE_DEVICE_INFO.ordinal, null))
        pcmPlayer = null
    }

    override fun onSelfDeviceAvailable(selfDevice: WifiP2pDevice) {
        Log.i(thisTag, "onSelfDeviceAvailable" )
        sendMsgLiveData(ViewModelMsg(MsgType.SHOW_SELF_DEVICE_INFO.ordinal, selfDevice))
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onPeersAvailable(deviceList: Collection<WifiP2pDevice>) {
        Log.i(thisTag, "onPeersAvailable :" + wifiP2pDeviceList.size)
        wifiP2pDeviceList.clear()
        wifiP2pDeviceList.addAll(deviceList)
        deviceAdapter.notifyDataSetChanged()
        sendMsgLiveData(ViewModelMsg(MsgType.CANCEL_LOADING_DIALOG.ordinal, ""))
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

}
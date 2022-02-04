package com.wxson.audio_player.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ChannelListener
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.wxson.audio_player.MyApplication
import com.wxson.audio_player.R
import com.wxson.p2p_comm.*
import com.wxson.p2p_comm.DirectBroadcastReceiver.Companion.getIntentFilter
import java.net.InetAddress

class MainViewModel : ViewModel(), ChannelListener, IDirectActionListener {

    private val thisTag = this.javaClass.simpleName
    private val wifiP2pManager: WifiP2pManager
    private val channel: WifiP2pManager.Channel
    private val receiver: BroadcastReceiver
    private val dummyPlayerRunnable: DummyPlayerRunnable
    private val playThread: Thread
    val clientList = ArrayList<String>()

    @SuppressLint("StaticFieldLeak")
    var connectIntentService: ConnectIntentService? = null

    /**
     *  on service connected, start PlayerIntentService.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(thisTag, "onServiceConnected")
            val binder = service as ConnectIntentService.MyBinder
            connectIntentService = binder.connectIntentService
            connectIntentService?.setMessageListener(messageListener)
            val intent = Intent(MyApplication.context, ConnectIntentService::class.java)
            intent.action = ACTION_TCP_IP
            MyApplication.context.startService(intent)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(thisTag, "onServiceDisconnected")
        }
    }

    private val messageListener = object : IMessageListener {
        @SuppressLint("NotifyDataSetChanged")
        override fun onRemoteMsgArrived(arrivedString: String, clientInetAddress: InetAddress) {
            Log.i(thisTag, "onRemoteMsgArrived")
            Util.sendLiveData(localMsgLiveData, "arrivedString:$arrivedString clientInetAddress:$clientInetAddress")
            when (arrivedString) {
                Val.msgClientConnected -> {
                    // add new client to client list
                    clientList.add(clientInetAddress.hostAddress)
                    Util.sendLiveData(localMsgLiveData, "clientListChanged")
                }
                Val.msgClientDisconnectRequest -> {
                    // search this client and delete it from client list
                    clientList.removeIf{it == clientInetAddress.hostAddress }
                    Util.sendLiveData(localMsgLiveData, "clientListChanged")
                }
                "remote_cmd_pausePlay" -> {
                    this@MainViewModel.pause()
                }
                "remote_cmd_play" -> {
                }
            }
        }

        override fun onLocalMsgOccurred(msgType: String, msg: String) {
//            Log.i(thisTag, "onLocalMsgOccurred $msgType : $msg")
            when (msgType) {
                "TcpSocketServiceStatus" -> {}
                "TcpSocketClientStatus" -> {
                    //set clientConnected=true,把PlayThreadHandler的实例通过参数传给DecoderCallback，
                    //在DecoderCallback中观察，clientConnected=true时表示消费者已经启动，SynchronousQueue<PcmTransferData>可以使用
                    //否则SynchronousQueue<PcmTransferData>不能使用
                    clientConnected = msg == "ON"
                    Log.d(thisTag,
                        "mainViewModel.onLocalMsgOccurred msgType=TcpSocketClientStatus clientConnected = ${msg == "ON"}")
                }
                else -> {
                    Util.sendLiveData(localMsgLiveData, "$msgType:$msg")
                }
            }
        }
    }

    //region LiveData
    private var localMsgLiveData = MutableLiveData<String>()
    fun getLocalMsg(): LiveData<String> {
        return localMsgLiveData
    }

    //endregion

    init {
        Log.i(thisTag, "init")
        wifiP2pManager =  MyApplication.context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(MyApplication.context, Looper.getMainLooper(), this)
        createGroup()       //建立p2p组，否则无法取得hostIpAddress，ServerSocketChannel无法绑定本机ip
        // set a fixed name to the self device used in wifi p2p group
        wifiP2pManager.setDeviceName(channel, MyApplication.context.getString(R.string.app_name))
        //
        clientList.clear()
        //
        receiver = DirectBroadcastReceiver(wifiP2pManager, channel, this)
        MyApplication.context.registerReceiver(receiver, getIntentFilter())
        bindConnectIntentService()
        // 启动PlayThread
        dummyPlayerRunnable = DummyPlayerRunnable()
        playThread = Thread(dummyPlayerRunnable)
        playThread.start()
    }

    override fun onCleared() {
        Log.i(thisTag, "onCleared")
        unbindConnectIntentService()
        connectIntentService = null
        unregisterBroadcastReceiver()
        removeGroup()
        dummyPlayerRunnable.threadHandler.looper.quitSafely()
        super.onCleared()
    }

    // ChannelListener
    override fun onChannelDisconnected() {
        Log.i(thisTag, "onChannelDisconnected")
    }

    //region DirectActionListener
    override fun onWifiP2pEnabled(enabled: Boolean) {
        Log.i(thisTag, "onWifiP2pEnabled=$enabled")
    }

    override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
        Log.i(thisTag, "onConnectionInfoAvailable=$wifiP2pInfo")

        if (wifiP2pInfo.groupFormed) {
            Util.sendLiveData(localMsgLiveData, "group is formed")
        } else {
            Log.i(thisTag, "未建组！")
            Util.sendLiveData(localMsgLiveData, "group is not formed")
            Util.sendLiveData(localMsgLiveData, "未建组！")
        }
        if (!wifiP2pInfo.isGroupOwner) {
            Log.i(thisTag, "本机不是组长！")
            Util.sendLiveData(localMsgLiveData, "本机不是组长！")
        }
    }

    override fun onDisconnection() {
        Log.i(thisTag, "onDisconnection")
        clearClientList()
    }

    override fun onSelfDeviceAvailable(selfDevice: WifiP2pDevice) {
        Log.i(thisTag, "onSelfDeviceAvailable=$selfDevice")
    }

    override fun onPeersAvailable(deviceList: Collection<WifiP2pDevice>) {
        Log.i(thisTag, "onPeersAvailable=$deviceList")
    }

    override fun onP2pDiscoveryStopped() {
        Log.i(thisTag, "onP2pDiscoveryStopped")
    }
    //endregion

    //region public methods
    fun createGroup() {
        Log.i(thisTag, "createGroup()")
        if (ActivityCompat.checkSelfPermission(MyApplication.context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
            Util.sendLiveData(localMsgLiveData, "需要申请ACCESS_FINE_LOCATION权限")
            return
        }
        wifiP2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(thisTag, "createGroup onSuccess")
                Util.sendLiveData(localMsgLiveData, "group is formed")
            }

            override fun onFailure(reason: Int) {
                Log.i(thisTag, "createGroup onFailure: $reason")
                Util.sendLiveData(localMsgLiveData, "createGroup onFailure")
            }
        })
    }

    fun removeGroup() {
        Log.i(thisTag, "removeGroup()")
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(thisTag, "removeGroup onSuccess")
                clearClientList()
                // notify MediaCodecCallback of connect status
                Util.sendLiveData(localMsgLiveData, "group is not formed")
            }

            override fun onFailure(reason: Int) {
                Log.i(thisTag, "removeGroup onFailure")
                Util.sendLiveData(localMsgLiveData, "removeGroup onFailure")
            }
        })
    }

    fun play(fileName: String) {
        Log.i(thisTag, "play()")
        val pathName: String = MyApplication.context.cacheDir.path
        dummyPlayerRunnable.threadHandler.sendMessage(setMsg(
            MsgType.DUMMY_PLAYER_PLAY.ordinal,
            "$pathName/$fileName"))
    }

    fun stop() {
        Log.i(thisTag, "stop()")
        dummyPlayerRunnable.threadHandler.sendMessage(setMsg(
            MsgType.DUMMY_PLAYER_STOP.ordinal, ""))
    }

    fun pause() {
        Log.i(thisTag, "pause()")
        dummyPlayerRunnable.threadHandler.sendMessage(setMsg(
            MsgType.DUMMY_PLAYER_PAUSE.ordinal, ""))
    }

    fun resume() {
        Log.i(thisTag, "resume()")
        dummyPlayerRunnable.threadHandler.sendMessage(setMsg(
            MsgType.DUMMY_PLAYER_RESUME.ordinal, ""))
    }

    fun mute() {
        Log.i(thisTag, "mute()")
        dummyPlayerRunnable.threadHandler.sendMessage(setMsg(
            MsgType.DUMMY_PLAYER_MUTE.ordinal, ""))
    }

    fun unMute() {
        Log.i(thisTag, "unMute()")
        dummyPlayerRunnable.threadHandler.sendMessage(setMsg(
            MsgType.DUMMY_PLAYER_UNMUTE.ordinal, ""))
    }

    //endregion

    //region private methods

    private fun unbindConnectIntentService() {
        Log.i(thisTag, "unbindConnectIntentService")
        if (connectIntentService != null) MyApplication.context.unbindService(serviceConnection)
    }

    private fun unregisterBroadcastReceiver() {
        MyApplication.context.unregisterReceiver(receiver)
    }

    private fun bindConnectIntentService() {
        MyApplication.context.bindService(Intent(MyApplication.context, ConnectIntentService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE)
    }

    private fun setMsg(msgWhat: Int, msgObj: Any) : Message {
        val msg = Message()
        msg.what = msgWhat
        msg.obj = msgObj
        return msg
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearClientList() {
        clientList.clear()
        Util.sendLiveData(localMsgLiveData, "clientListChanged")
    }
    //endregion
}

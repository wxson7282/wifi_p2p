package com.wxson.audio_player.ui.main

import android.Manifest
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ChannelListener
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wxson.p2p_comm.DirectBroadcastReceiver
import com.wxson.p2p_comm.DirectBroadcastReceiver.Companion.getIntentFilter
import com.wxson.p2p_comm.IDirectActionListener
import java.net.InetAddress

class MainViewModel(application: Application) : AndroidViewModel(application), ChannelListener, IDirectActionListener {

    private val thisTag = this.javaClass.simpleName
    private val app: Application
    private val wifiP2pManager: WifiP2pManager
    private val channel: WifiP2pManager.Channel
    private val receiver: BroadcastReceiver
    private val player: Player

    var playerIntentService: PlayerIntentService? = null

    /**
     *  on service connected, start PlayerIntentService.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(thisTag, "onServiceConnected")
            val binder = service as PlayerIntentService.MyBinder
            playerIntentService = binder.playerIntentService
            playerIntentService?.setStringTransferListener(stringTransferListener)
            PlayerIntentService.startActionTcp(application)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(thisTag, "onServiceDisconnected")
        }
    }

    private val stringTransferListener = object : IStringTransferListener {
        override fun onStringArrived(arrivedString: String, clientInetAddress: InetAddress) {
            Log.i(thisTag, "onStringArrived")
            localMsgLiveData.postValue("arrivedString:$arrivedString clientInetAddress:$clientInetAddress")
            when (arrivedString) {
                "remote_cmd_pause" -> {
                }
                "remote_cmd_play" -> {
                }
            }
        }

        override fun onMsgTransfer(msgType: String, msg: String) {
            Log.i(thisTag, "onMsgTransfer $msgType : $msg")
            when (msgType) {
                "TcpSocketServiceStatus" -> {
                }
                "TcpSocketClientStatus" -> {
                }
                else -> {
                    localMsgLiveData.postValue("$msgType:$msg")
                }
            }
        }
    }

    //region LiveData
    private var localMsgLiveData = MutableLiveData<String>()
    fun getLocalMsg(): LiveData<String> {
        return localMsgLiveData
    }

    private var connectStatusLiveData = MutableLiveData<Boolean>()
    fun getConnectStatus(): LiveData<Boolean> {
        return connectStatusLiveData
    }
    //endregion

    init {
        Log.i(thisTag, "init")
        app = application
        wifiP2pManager =  application.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(application, Looper.getMainLooper(), this)
        receiver = DirectBroadcastReceiver(wifiP2pManager, channel, this)
        application.registerReceiver(receiver, getIntentFilter())
        bindService(application)
        player = Player()
    }

    override fun onCleared() {
        Log.i(thisTag, "onCleared")
        if(IsTcpSocketServiceOn){
            playerIntentService?.stopTcpSocketService()
        }
        unbindServiceConnection()
        playerIntentService = null
        unregisterBroadcastReceiver()
        removeGroup()
        player.releaseAll()

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

    override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo?) {
        Log.i(thisTag, "onConnectionInfoAvailable=$wifiP2pInfo")
//        if (wifiP2pInfo != null) {
//            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
//                bindService(app)
//            }
//        }
        //service is started at init()
    }

    override fun onDisconnection() {
        Log.i(thisTag, "onDisconnection")
        connectStatusLiveData.postValue(false)
    }

    override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice?) {
        Log.i(thisTag, "onSelfDeviceAvailable=$wifiP2pDevice")
    }

    override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice?>?) {
        Log.i(thisTag, "onPeersAvailable=$wifiP2pDeviceList")
    }

    override fun onP2pDiscoveryStopped() {
        Log.i(thisTag, "onP2pDiscoveryStopped")
    }
    //endregion

    //region public methods
    fun createGroup() {
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
            localMsgLiveData.postValue("需要申请ACCESS_FINE_LOCATION权限")
            return
        }
        wifiP2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(thisTag, "createGroup onSuccess")
                localMsgLiveData.postValue("createGroup onSuccess")
            }

            override fun onFailure(reason: Int) {
                Log.i(thisTag, "createGroup onFailure: $reason")
                localMsgLiveData.postValue("createGroup onFailure: $reason")
            }
        })
    }

    fun removeGroup() {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(thisTag, "removeGroup onSuccess")
                connectStatusLiveData.postValue(false)
                // notify MediaCodecCallback of connect status
                // TODO("Not yet implemented")
                localMsgLiveData.postValue("removeGroup onSuccess")
            }

            override fun onFailure(reason: Int) {
                Log.i(thisTag, "removeGroup onFailure")
                localMsgLiveData.postValue("removeGroup onFailure")
            }
        })
    }

    fun play(afd: AssetFileDescriptor) {
        Log.i(thisTag, "play()")
        player.assetFilePlay(afd)
    }

    fun stop() {
        Log.i(thisTag, "stop()")
        player.stop()
    }

    fun pause() {
        Log.i(thisTag, "pause()")
        player.pause()
    }

    fun mute() {
        Log.i(thisTag, "mute()")
        player.mute()
    }
    //endregion

    //region private methods
    private fun unbindServiceConnection() {
        Log.i(thisTag, "unbindServiceConnection")
        if (playerIntentService != null) {
            app.unbindService(serviceConnection)
        }
    }

    private fun unregisterBroadcastReceiver() {
        app.unregisterReceiver(receiver)
    }

    private fun bindService(application: Application) {
        val intent = Intent(application, PlayerIntentService::class.java)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    //endregion


    // TODO: Implement the ViewModel
}

package com.wxson.audio_player.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wxson.audio_player.R
import com.wxson.p2p_comm.*
import com.wxson.p2p_comm.DirectBroadcastReceiver.Companion.getIntentFilter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.InetAddress

class MainViewModel(application: Application) : AndroidViewModel(application), ChannelListener, IDirectActionListener {

    private val thisTag = this.javaClass.simpleName
    private val app = application
    private val wifiP2pManager: WifiP2pManager
    private val channel: WifiP2pManager.Channel
    private val receiver: BroadcastReceiver
    private val dummyPlayerRunnable: DummyPlayerRunnable
    private val playThread: Thread

    @SuppressLint("StaticFieldLeak")
    var playerIntentService: PlayerIntentService? = null

    /**
     *  on service connected, start PlayerIntentService.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(thisTag, "onServiceConnected")
            val binder = service as PlayerIntentService.MyBinder
            playerIntentService = binder.playerIntentService
            playerIntentService?.setMessageListener(messageListener)
            playerIntentService?.queue = synchronousQueue
            PlayerIntentService.startActionTcp(app)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(thisTag, "onServiceDisconnected")
        }
    }

    private val messageListener = object : IMessageListener {
        override fun onRemoteMsgArrived(arrivedString: String, clientInetAddress: InetAddress) {
            Log.i(thisTag, "onRemoteMsgArrived")
            Util.sendLiveData(localMsgLiveData, "arrivedString:$arrivedString clientInetAddress:$clientInetAddress")
            when (arrivedString) {
                Val.msgClientConnected -> {
                    Util.sendLiveData(connectStatusLiveData, true)
                }
                Val.msgClientDisconnectRequest -> {
                    // 向客户端发出应答，客户端收到应答后关闭socket线程
                    val msg1 = Message()
                    msg1.what = Val.msgCodeByteArray
                    msg1.obj = Val.msgDisconnectReply.toByteArray()
                    playerIntentService?.serverRunnable?.outputHandler?.sendMessage(msg1)
                    // 变更连接标识
                    Util.sendLiveData(connectStatusLiveData, false)
                    clientConnected = false
                    Log.d(thisTag, "messageListener.messageListener clientConnected = false")
                    playerIntentService?.serverRunnable?.isClientSocketOn = false
                    //*********************************************
                    playerIntentService?.serverRunnable?.interruptThread()
                    val msg2 = Message()
                    msg2.what = Val.msgThreadInterrupted
                    msg2.obj = null
                    playerIntentService?.serverRunnable?.outputHandler?.sendMessage(msg2)
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

    private var connectStatusLiveData = MutableLiveData<Boolean>()
    fun getConnectStatus(): LiveData<Boolean> {
        return connectStatusLiveData
    }
    //endregion

    init {
        Log.i(thisTag, "init")
        wifiP2pManager =  app.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(app, Looper.getMainLooper(), this)
        // set a fixed name to the self device used in wifi p2p group
        setDeviceName(app.getString(R.string.app_name))
        receiver = DirectBroadcastReceiver(wifiP2pManager, channel, this)
        app.registerReceiver(receiver, getIntentFilter())
        bindPlayerIntentService()
        // 启动PlayThread
        dummyPlayerRunnable = DummyPlayerRunnable()
        playThread = Thread(dummyPlayerRunnable)
        playThread.start()
    }

    override fun onCleared() {
        Log.i(thisTag, "onCleared")
        if(isTcpSocketServiceOn){
            playerIntentService?.stopTcpSocketService()
        }
        unbindPlayerIntentService()
        playerIntentService = null
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
//        if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
//            startPlayerIntentService()
//            bindPlayerIntentService()
//        }
        if (wifiP2pInfo.groupFormed) {
            Util.sendLiveData(localMsgLiveData, "createGroup onSuccess")
        } else {
            Log.i(thisTag, "未建组！")
            Util.sendLiveData(localMsgLiveData, "removeGroup onSuccess")
            Util.sendLiveData(localMsgLiveData, "未建组！")
        }
        if (!wifiP2pInfo.isGroupOwner) {
            Log.i(thisTag, "本机不是组长！")
            Util.sendLiveData(localMsgLiveData, "本机不是组长！")
        }
    }

    override fun onDisconnection() {
        Log.i(thisTag, "onDisconnection")
        connectStatusLiveData.postValue(false)
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
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(thisTag, "需要申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
            Util.sendLiveData(localMsgLiveData, "需要申请ACCESS_FINE_LOCATION权限")
            return
        }
        wifiP2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(thisTag, "createGroup onSuccess")
                Util.sendLiveData(localMsgLiveData, "createGroup onSuccess")
            }

            override fun onFailure(reason: Int) {
                Log.i(thisTag, "createGroup onFailure: $reason")
                Util.sendLiveData(localMsgLiveData, "createGroup onFailure")
            }
        })
    }

    fun removeGroup() {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(thisTag, "removeGroup onSuccess")
                Util.sendLiveData(connectStatusLiveData, false)
                // notify MediaCodecCallback of connect status
                // TODO("Not yet implemented")
                Util.sendLiveData(localMsgLiveData, "removeGroup onSuccess")
            }

            override fun onFailure(reason: Int) {
                Log.i(thisTag, "removeGroup onFailure")
                Util.sendLiveData(localMsgLiveData, "removeGroup onFailure")
            }
        })
    }

    fun play(fileName: String) {
        Log.i(thisTag, "play()")
        val pathName: String = app.cacheDir.path
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
    private fun unbindPlayerIntentService() {
        Log.i(thisTag, "unbindPlayerIntentService")
        if (playerIntentService != null) {
            app.unbindService(serviceConnection)
        }
    }

    private fun unregisterBroadcastReceiver() {
        app.unregisterReceiver(receiver)
    }

    private fun bindPlayerIntentService() {
        val intent = Intent(app, PlayerIntentService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setDeviceName(deviceName: String) {
        try {
            val paramTypes0 = WifiP2pManager.Channel::class.java
            val paramTypes1 = String::class.java
            val paramTypes2 = WifiP2pManager.ActionListener::class.java

            val setDeviceName: Method = wifiP2pManager.javaClass.getMethod(
                "setDeviceName", paramTypes0, paramTypes1, paramTypes2
            )
            setDeviceName.isAccessible = true
            setDeviceName.invoke(wifiP2pManager, channel,
                deviceName,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(thisTag, "setDeviceName succeeded")
                    }

                    override fun onFailure(reason: Int) {
                        Log.i(thisTag, "setDeviceName failed")
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

    private fun setMsg(msgWhat: Int, msgObj: Any) : Message {
        val msg = Message()
        msg.what = msgWhat
        msg.obj = msgObj
        return msg
    }
    //endregion
}

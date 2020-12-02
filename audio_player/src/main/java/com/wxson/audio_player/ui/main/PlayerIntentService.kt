package com.wxson.audio_player.ui.main

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wxson.audio_player.R
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket

private const val ACTION_TCP = "com.wxson.audio_player.connect.action.TCP"
var IsTcpSocketServiceOn = false  //switch for TcpSocketService ON/OFF
private lateinit var stringTransferListener: IStringTransferListener


class PlayerIntentService : IntentService("PlayerIntentService") {
    private val runningTag = this.javaClass.simpleName
    var serverThread: ServerThread? = null

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_TCP -> {
                handleActionTcp()
            }
        }
    }

    /**
     * Handle action TCP in the provided background thread with the provided
     */
    private fun handleActionTcp() {
        var clientSocket: Socket? = null
        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket(resources.getInteger(R.integer.ServerSocketPort))
            Log.i(runningTag, "handleActionTcp: create ServerSocket")
            while (IsTcpSocketServiceOn) {
                stringTransferListener.onMsgTransfer("TcpSocketClientStatus", "ON")
                //等待客户端来连接
                clientSocket = serverSocket.accept()   //blocks until a connection is made
                Log.i(runningTag, "client IP address: " + clientSocket.inetAddress.hostAddress)
                serverThread = ServerThread(clientSocket)
                Thread(serverThread).start()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw Exception("I/O Exception")
        } finally {
            clientSocket?.close()
            serverSocket?.close()
            IsTcpSocketServiceOn = false
            stringTransferListener.onMsgTransfer("TcpSocketServiceStatus", "OFF")
        }
    }

    companion object {
        /**
         * Starts this service to perform action Tcp. If
         * the service is already performing a task this action will be queued.
         *
         * @see IntentService
         */
        @JvmStatic
        fun startActionTcp(context: Context) {
            val intent = Intent(context, PlayerIntentService::class.java).apply {
                action = ACTION_TCP
                IsTcpSocketServiceOn = true
            }
            context.startService(intent)
        }
    }

    inner class MyBinder: Binder(){
        val playerIntentService: PlayerIntentService = this@PlayerIntentService
    }

    override fun onBind(intent: Intent): IBinder {
        return MyBinder()
    }

    override fun onDestroy() {
        serverThread?.handler?.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private var localSocket: Socket? = null
    private var objectOutputStream: ObjectOutputStream? = null

    /**
     *  sends message to local host for stopping service
     */
    fun stopTcpSocketService() {
        object :Thread(){
            override fun run() {
                try{
                    localSocket = Socket("127.0.0.1", resources.getInteger(R.integer.ServerSocketPort))
                    objectOutputStream =  ObjectOutputStream(localSocket?.outputStream)
                    objectOutputStream?.writeObject("StopTcpSocketService".toByteArray())
                }
                catch (e:IOException){
                    Log.e(runningTag, "stopTcpSocketService: IOException")
                }
            }
        }.start()
        IsTcpSocketServiceOn = false
    }

    fun setStringTransferListener(listener: IStringTransferListener) {
        stringTransferListener = listener
    }
}

class ServerThread(private var clientSocket: Socket) : Runnable {
    private val runningTag = this.javaClass.simpleName
    private val objectInputStream: ObjectInputStream = ObjectInputStream(clientSocket.getInputStream())
    private val objectOutputStream: ObjectOutputStream = ObjectOutputStream(clientSocket.getOutputStream())
    // 定义接收外部线程的消息的Handler对象
    internal var handler: Handler? = null
    override fun run() {
        try {
            Log.i(runningTag, "ServerThread run")
            // 启动一条子线程来读取客户响应的数据
            object : Thread() {
                override fun run() {
                    var inputObject = readObjectFromClient()
                    // 采用循环不断从Socket中读取客户端发送过来的数据
                    while (inputObject != null && IsTcpSocketServiceOn) {
                        Log.i(runningTag, inputObject.javaClass.simpleName + " class received")
                        when (inputObject.javaClass.simpleName) {
                            "byte[]" -> {
                                val arrivedString = String(inputObject as ByteArray)
                                Log.i(runningTag, "$arrivedString received")
                                stringTransferListener.onStringArrived(arrivedString, clientSocket.inetAddress)
                            }
                            else -> {
                                Log.i(runningTag, "other class received")
                            }
                        }
                        inputObject = readObjectFromClient()
                    }
                }
            }.start()
            // create Lopper for output
            Looper.prepare()
            handler = MyHandler(objectOutputStream)
            Looper.loop()
        } catch (e: InterruptedException) {
            Log.e(runningTag, "InterruptedException")
        }
    }

    // 定义读取客户端数据的方法
    private fun readObjectFromClient(): Any? {
        try{
            return objectInputStream.readObject()
        }
        // 如果捕捉到异常，表明该Socket对应的客户端已经关闭
        catch (e: IOException){
            Log.i(runningTag, "readObjectFromClient: socket is closed")
            stringTransferListener.onMsgTransfer("TcpSocketClientStatus", "OFF")
        }
        return null
    }

    private class MyHandler(private val objectOutputStream: ObjectOutputStream) : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what){
                0x334 -> {  // audio
                    writeObjectToClient(msg.obj)
                    this.removeMessages(0x334)
                }
                0x123 -> {  // ByteArray
                    writeObjectToClient(msg.obj)
                    this.removeMessages(0x123)
                }
            }
        }

        private fun writeObjectToClient(obj: Any) {
            try {
                objectOutputStream.writeObject(obj)
                objectOutputStream.reset()  // It is necessary to avoid OOM.
            } catch (e: IOException) {
                stringTransferListener.onMsgTransfer("TcpSocketClientStatus", "OFF")
            }
        }
    }
}
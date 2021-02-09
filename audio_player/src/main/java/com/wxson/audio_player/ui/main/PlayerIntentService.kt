package com.wxson.audio_player.ui.main

import android.app.IntentService
import android.content.Intent
import android.os.*
import android.util.Log
import com.wxson.audio_player.R
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket

//private const val ACTION_TCP = "com.wxson.audio_player.connect.action.TCP"
var isTcpSocketServiceOn = false  //switch for TcpSocketService ON/OFF
private lateinit var messageListener: IMessageListener


class PlayerIntentService : IntentService("PlayerIntentService") {
    private val runningTag = this.javaClass.simpleName

    var serverThread: ServerThread? = null

    override fun onHandleIntent(intent: Intent?) {
//        when (intent?.action) {
//            ACTION_TCP -> {
//                handleActionTcp()
//            }
//        }
        handleActionTcp()
    }

    /**
     * Handle action TCP in the provided background thread with the provided
     */
    private fun handleActionTcp() {
        var clientSocket: Socket? = null
        var serverSocket: ServerSocket? = null
        isTcpSocketServiceOn = true
        try {
            serverSocket = ServerSocket(resources.getInteger(R.integer.ServerSocketPort))
            Log.i(runningTag, "handleActionTcp: create ServerSocket")
            while (isTcpSocketServiceOn) {
                messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "ON")
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
            isTcpSocketServiceOn = false
            messageListener.onLocalMsgOccurred("TcpSocketServiceStatus", "OFF")
        }
    }

//    companion object {
//        /**
//         * Starts this service to perform action Tcp. If
//         * the service is already performing a task this action will be queued.
//         *
//         * @see IntentService
//         */
//        @JvmStatic
//        fun startActionTcp(context: Context) {
//            val intent = Intent(context, PlayerIntentService::class.java).apply {
//                action = ACTION_TCP
//                isTcpSocketServiceOn = true
//            }
//            context.startService(intent)
//        }
//    }

    inner class MyBinder: Binder(){
        val playerIntentService: PlayerIntentService = this@PlayerIntentService
    }

    override fun onBind(intent: Intent): IBinder {
        return MyBinder()
    }

    override fun onDestroy() {
        serverThread?.outputHandler?.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private var localSocket: Socket? = null
    private var objectOutputStream: ObjectOutputStream? = null

    /**
     *  sends message to local host for stopping service
     */
    fun stopTcpSocketService() {
        isTcpSocketServiceOn = false
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
//        isTcpSocketServiceOn = false
    }

    fun setMessageListener(listener: IMessageListener) {
        messageListener = listener
    }
}

class ServerThread(private var clientSocket: Socket) : Runnable {
    private val runningTag = this.javaClass.simpleName
    private val objectInputStream: ObjectInputStream = ObjectInputStream(clientSocket.getInputStream())
    private val objectOutputStream: ObjectOutputStream = ObjectOutputStream(clientSocket.getOutputStream())
    // 定义向外输出的Handler对象
    internal var outputHandler: Handler? = null
    override fun run() {
        try {
            Log.i(runningTag, "ServerThread run")
            // 启动一条子线程来读取客户响应的数据
            object : Thread() {
                override fun run() {
                    var inputObject = readObjectFromClient()
                    // 采用循环不断从Socket中读取客户端发送过来的数据
                    while (inputObject != null && isTcpSocketServiceOn) {
                        Log.i(runningTag, inputObject.javaClass.simpleName + " class received")
                        when (inputObject.javaClass.simpleName) {
                            "byte[]" -> {
                                val arrivedString = String(inputObject as ByteArray)
                                Log.i(runningTag, "$arrivedString received")
                                messageListener.onRemoteMsgArrived(arrivedString, clientSocket.inetAddress)
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
            outputHandler = OutputHandler(objectOutputStream)
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
            messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "OFF")
        }
        return null
    }

    private class OutputHandler(private val objectOutputStream: ObjectOutputStream) : Handler() {
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
                messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "OFF")
            }
        }
    }
}
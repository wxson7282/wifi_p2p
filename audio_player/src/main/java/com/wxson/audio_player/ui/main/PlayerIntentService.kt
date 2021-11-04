package com.wxson.audio_player.ui.main

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import com.wxson.audio_player.R
import com.wxson.p2p_comm.PcmTransferData
import com.wxson.p2p_comm.Val
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.SynchronousQueue

private const val ACTION_TCP = "com.wxson.audio_player.connect.action.TCP"
var isTcpSocketServiceOn = false  //switch for TcpSocketService ON/OFF
private lateinit var messageListener: IMessageListener
@Volatile
var clientConnected = false
val synchronousQueue: SynchronousQueue<PcmTransferData> = SynchronousQueue<PcmTransferData>(true)

class PlayerIntentService : IntentService("PlayerIntentService") {
    private val runningTag = this.javaClass.simpleName
    lateinit var queue: SynchronousQueue<PcmTransferData>

    var serverRunnable: ServerRunnable? = null

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_TCP -> {
                handleActionTcp()
            }
        }
        handleActionTcp()
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
            while (isTcpSocketServiceOn) {
                //等待客户端来连接
                clientSocket = serverSocket.accept()   //blocks until a connection is made
                Log.i(runningTag, "client IP address: " + clientSocket.inetAddress.hostAddress)
                serverRunnable = ServerRunnable(clientSocket)
                Thread(serverRunnable).start()
                messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "ON")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw Exception("I/O Exception")
        } finally {
            clientSocket?.close()
            serverSocket?.close()
            isTcpSocketServiceOn = false
            messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "OFF")
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
                isTcpSocketServiceOn = true
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
        serverRunnable?.outputHandler?.removeCallbacksAndMessages(null)
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
                Log.d(runningTag, "ServerRunnable stopTcpThread: Start of ${currentThread().name}")
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

class ServerRunnable(private var clientSocket: Socket) : Runnable {
    private val runningTag = this.javaClass.simpleName
    private val objectInputStream: ObjectInputStream = ObjectInputStream(clientSocket.getInputStream())
    private val objectOutputStream: ObjectOutputStream = ObjectOutputStream(clientSocket.getOutputStream())
    @Volatile
    var isClientSocketOn = true
    private val subThread2 = object : Thread() {
        override fun run() {
            Log.e(runningTag, "subThread2 ${currentThread().name} started")
            while (isClientSocketOn && !currentThread().isInterrupted) {
                try {
//                    Log.d(runningTag, "writeObjectToClient(synchronousQueue.take())")
                    writeObjectToClient(synchronousQueue.take())
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                }
            }
            Log.e(runningTag, "subThread2 ${currentThread().name} ended")
        }
    }

    // 定义向外输出的Handler对象
    internal var outputHandler: Handler? = null
    override fun run() {
        Log.e(runningTag, "serverThread ${Thread.currentThread().name} started")
        try {
            Log.i(runningTag, "ServerRunnable run")
            // 启动一条子线程来读取客户响应的数据
            object : Thread() {
                override fun run() {
                    Log.e(runningTag, "subThread1 ${currentThread().name} started")
                    var inputObject = readObjectFromClient()
                    // 采用循环不断从Socket中读取客户端发送过来的数据
                    while (inputObject != null && isTcpSocketServiceOn && isClientSocketOn) {
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
                    interruptThread()
                    Log.e(runningTag, "subThread1 ${currentThread().name} ended")
                }
            }.start()
            // 启动一条子线程来接收SynchronousQueue中的pcm数据，并将其发送到客户端
            subThread2.start()
            Log.d(runningTag, "启动一条子线程来接收SynchronousQueue中的pcm数据，并将其发送到客户端")
            // create Lopper for output
            Looper.prepare()
            outputHandler = OutputHandler(objectOutputStream)
            Looper.loop()
        } catch (e: InterruptedException) {
            Log.e(runningTag, "InterruptedException")
        }
        Log.e(runningTag, "serverThread ${Thread.currentThread().name} ended")
    }

    // 定义读取客户端数据的方法
    private fun readObjectFromClient(): Any? {
        try{
            return objectInputStream.readObject()
        }
        // 如果捕捉到异常，表明该Socket对应的客户端已经关闭
        catch (e: IOException){
            Log.i(runningTag, "readObjectFromClient: socket is closed")
            interruptThread()
            isClientSocketOn = false
            messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "OFF")
        }
        return null
    }

    private fun writeObjectToClient(obj: Any) {
        try {
            objectOutputStream.writeObject(obj)
            objectOutputStream.reset()  // It is necessary to avoid OOM.
        } catch (e: IOException) {
            messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "OFF")
        }
    }

    fun interruptThread() {
        if (!clientSocket.isClosed) {
            if (!clientSocket.isInputShutdown)
                objectInputStream.close()
            if (!clientSocket.isOutputShutdown)
                objectOutputStream.close()
            clientSocket.close()
        }
    }

    //该Handler维持一个向客户端发送信息的通道
    private class OutputHandler(private val objectOutputStream: ObjectOutputStream) : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what){
                Val.msgCodeByteArray -> {  // ByteArray
                    writeObjectToClient(msg.obj)
                    this.removeMessages(Val.msgCodeByteArray)
                }
                Val.msgThreadInterrupted -> {
                    super.getLooper().quitSafely()
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
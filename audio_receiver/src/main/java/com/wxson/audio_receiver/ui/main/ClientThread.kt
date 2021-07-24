package com.wxson.audio_receiver.ui.main

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.wxson.p2p_comm.PcmTransferData
import java.io.*
import java.lang.ref.WeakReference
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.UnknownHostException
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class ClientThread(private val mainHandler: Handler, private val serverIp: String, private val serverSocketPort: Int) : Runnable {
    private val thisTag = this.javaClass.simpleName
    lateinit var socket: Socket
    // 该线程所处理的Socket所对应的输入出流
    private lateinit var objectOutputStream: ObjectOutputStream
    private lateinit var objectInputStream: ObjectInputStream

    // 定义向外输出的Handler对象
    class OutputHandler(private var clientThread: WeakReference<ClientThread>) : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what == MsgType.SEND_MSG_TO_REMOTE.ordinal){
                // 将客户端的文字信息写入网络
                clientThread.get()?.objectOutputStream?.writeObject((msg.obj.toString()).toByteArray())
                clientThread.get()?.objectOutputStream?.reset()
                Log.i(clientThread.get()?.thisTag, "handleMessage " + msg.obj.toString())
            }
        }
    }

    var outputHandler = Handler(Handler.Callback { false })

    override fun run() {
        Log.i(thisTag, "run")
        try {
            socket = Socket(serverIp, serverSocketPort)
            sendLocalMsg("Connected")
            objectOutputStream = ObjectOutputStream(socket.getOutputStream())
            objectInputStream = ObjectInputStream(socket.getInputStream())
            // 启动一条子线程来读取服务器响应的数据
            thread {
                var inputObject: Any? = objectInputStream.readObject()
                // 不断读取Socket输入流中的内容
                while (inputObject != null) {
                    when (inputObject) {
                        is PcmTransferData -> {     // pcm数据
                            Log.i(thisTag, "接收到PcmTransferData类")
                            val pcmTransferData: PcmTransferData = inputObject
                            // pcmTransferData -> MainViewModel -> AudioTrack
                            val msg = Message()
                            msg.what = MsgType.PCM_TRANSFER_DATA.ordinal
                            msg.obj = pcmTransferData
                            mainHandler.sendMessage(msg)
                        }
                        is ByteArray -> {           // 字符数据
                            // 每当读到来自服务器的文字数据之后，发送消息通知调用者
                            val arrivedString = String(inputObject)
                            Log.i(thisTag, "接收到ByteArray类 内容：${arrivedString}")
                            val msg = Message()
                            msg.what = MsgType.ARRIVED_STRING.ordinal
                            msg.obj = arrivedString
                            mainHandler.sendMessage(msg)
                        }
                        else -> {                   // 其他数据
                            Log.i(thisTag, "接收到其它类 ${inputObject.javaClass.simpleName}")
                        }
                    }
                    inputObject = objectInputStream.readObject()
                }
            }
            // 为当前线程初始化Looper
            Looper.prepare()
//            OutputHandler(WeakReference(this))
            outputHandler = OutputHandler(WeakReference(this))
            // 启动Looper
            Looper.loop()
        }
        catch (e: UnknownHostException){
            Log.e(thisTag, "未知IP地址！！")
            sendLocalMsg("未知IP地址！！")
        }
        catch(e: NoRouteToHostException){
            Log.e(thisTag, "服务器连接失败！！")
            sendLocalMsg("服务器连接失败！！")
        }
        catch (e: ConnectException){
            Log.e(thisTag, "服务器未启动！！")
            sendLocalMsg("服务器未启动！！")
        }
        catch (e: EOFException){
            Log.e(thisTag, "EOFException")
            sendLocalMsg("服务器关闭！！")
        }
        catch (re: RuntimeException){
            Log.e(thisTag, "RuntimeException")
        }
        catch (e: StreamCorruptedException) {
            Log.e(thisTag, "StreamCorruptedException")
            e.printStackTrace()
        }
        catch (e: IOException){
            e.printStackTrace()
            exitProcess(1)
        }
        catch(e:Exception){
            e.printStackTrace()
            exitProcess(1)
        }
        catch(e:Exception){
            e.printStackTrace()
            exitProcess(1)
        }
        finally {
            objectOutputStream.close()
            objectInputStream.close()
            socket.close()
            sendLocalMsg("Disconnected")
        }
        TODO("Not yet implemented")
    }

    private fun sendLocalMsg(localMsg: String){
        val msg = Message()
        msg.what = MsgType.LOCAL_MSG.ordinal
        msg.obj = localMsg
        mainHandler.sendMessage(msg)
    }
}
package com.wxson.audio_receiver.ui.main

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.wxson.p2p_comm.PcmTransferData
import com.wxson.p2p_comm.Util
import com.wxson.p2p_comm.Val
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import kotlin.concurrent.thread

class ConnectRunnable(private val mainHandler: Handler, private val serverIp: String, private val serverSocketPort: Int) : Runnable {
    private val thisTag = this.javaClass.simpleName
    private lateinit var selector: Selector
    private val charset = Charsets.UTF_8
    private lateinit var sockChannel: SocketChannel
    var connectThreadHandler = Handler { false }

    // 定义线程的Handler对象，用以响应线程调用者发来的消息
    class ThreadHandler(private var connectRunnable: WeakReference<ConnectRunnable>) : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what == MsgType.SEND_MSG_TO_REMOTE.ordinal) {
                // 将客户端的文字信息写入网络
                connectRunnable.get()?.sockChannel?.write(connectRunnable.get()?.charset?.encode(msg.obj.toString()))
                Log.i(connectRunnable.get()?.thisTag, "handleMessage " + msg.obj.toString())
            }
        }
    }

    private val clientThread = thread(false) {
        try {
            outer@ while (selector.select() > 0) {
                 for (selectionKey in selector.selectedKeys()) {
                    selector.selectedKeys().remove(selectionKey)
                    if (selectionKey.isReadable) {
                        val socketChannel = selectionKey.channel() as SocketChannel
                        val typeBuff = ByteBuffer.allocate(1)
                        if (socketChannel.read(typeBuff) > 0) {
                            typeBuff.flip()
                            val typeByte: Byte = typeBuff.get()
                            typeBuff.clear()
                            val buff = ByteBuffer.allocate(1024)
                            when (typeByte) {
                                Val.TextType -> {       //text data received
                                    var arrivedString = ""
                                    while (socketChannel.read(buff) > 0) {
                                        buff.flip()
                                        arrivedString += charset.decode(buff)
                                        buff.clear()
                                    }
                                    Log.i(thisTag, "接收到text data 内容：${arrivedString}")
                                    val msg = Message()
                                    msg.what = MsgType.ARRIVED_STRING.ordinal
                                    msg.obj = arrivedString
                                    mainHandler.sendMessage(msg)
                                    // 如果收到中断连接应答，则停止当前线程
                                    if (arrivedString == Val.msgDisconnectReply) {
                                        break@outer
                                    }
                                }
                                Val.AudioType -> {      //audio data received
                                    val pcmTransferDataBuff = ByteBuffer.allocate(Val.AudioBuffCapacity)
                                    while (socketChannel.read(buff) > 0 && pcmTransferDataBuff.position() < pcmTransferDataBuff.capacity() - 1024) {
                                        buff.flip()
                                        pcmTransferDataBuff.put(buff)
                                        buff.clear()
                                    }
                                    pcmTransferDataBuff.flip()
                                    if (pcmTransferDataBuff.limit() > 0) {
                                        val byteArray = ByteArray(pcmTransferDataBuff.limit())
                                        pcmTransferDataBuff.get(byteArray)
                                        val pcmTransferData: PcmTransferData = Util.byteArrayToPcmTransferData(byteArray)
                                        if (pcmTransferData.sampleRateInHz > 0) {
                                            Log.i(thisTag, "接收到pcm data 第${pcmTransferData.frameCount}帧")
                                            val msg = Message()
                                            msg.what = MsgType.PCM_TRANSFER_DATA.ordinal
                                            msg.obj = pcmTransferData
                                            mainHandler.sendMessage(msg)
                                        }
                                    }
                                    pcmTransferDataBuff.clear()
                                }
                                else -> {
                                    while (socketChannel.read(buff) > 0) {
                                        buff.clear()
                                    }
                                    Log.e(thisTag, "Undefined data.")
                                }
                            }
                        }
                        selectionKey.interestOps(SelectionKey.OP_READ)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    override fun run() {
        selector = Selector.open()
        val inetSocketAddress = InetSocketAddress(serverIp, serverSocketPort)
        sockChannel = SocketChannel.open(inetSocketAddress)
        sockChannel.configureBlocking(false)
        sockChannel.register(selector, SelectionKey.OP_READ)
        //启动读取服务器的线程
        clientThread.start()
        Looper.prepare()
        connectThreadHandler = ThreadHandler(WeakReference(this))
        Looper.loop()
    }
}
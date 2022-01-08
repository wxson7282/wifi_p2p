package com.wxson.audio_player.ui.main

import android.app.IntentService
import android.content.Intent
import android.os.*
import android.util.Log
import com.wxson.audio_player.R
import com.wxson.p2p_comm.PcmTransferData
import com.wxson.p2p_comm.SerializableUtil
import com.wxson.p2p_comm.Util
import com.wxson.p2p_comm.Val
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.Charset
import kotlin.concurrent.thread

// IntentService can perform TCP and UDP protocol
const val ACTION_TCP_IP = "com.wxson.audio_player.ui.main.action.TCP_IP"
const val ACTION_UDP = "com.wxson.audio_player.ui.main.action.UDP"
private lateinit var messageListener: IMessageListener

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
class ConnectIntentService : IntentService("ConnectIntentService") {
    private val runningTag = this.javaClass.simpleName
    private lateinit var selector: Selector
    private val charset = Charsets.UTF_8
    internal var outputHandler: Handler? = null

    inner class MyBinder: Binder() {
        val connectIntentService: ConnectIntentService = this@ConnectIntentService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return MyBinder()
    }

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_TCP_IP -> {
                try {
                    handleActionTcp()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            ACTION_UDP -> {
                handleActionUdp()
            }
        }
    }

    /**
     * Handle action Tcp in the provided background thread
     */
    @Throws(IOException::class)
    private fun handleActionTcp() {
        Log.i(runningTag, "handleActionTcp 服务线程 ${Thread.currentThread().name} started")
        selector = Selector.open()
        val server = ServerSocketChannel.open()
        val hostIpAddress = Util.getLocalP2pHostIp()
        if (hostIpAddress == "") {  //wifi功能没有打开，或者wifi p2p组未建立
            messageListener.onLocalMsgOccurred("WifiP2pEnabled", "false")
            Log.e(runningTag, "handleActionTcp WifiP2pEnabled=false")
            return
        }
        val inetSocketAddress = InetSocketAddress(hostIpAddress, resources.getInteger(R.integer.ServerSocketPort))
        server.bind(inetSocketAddress)
        server.configureBlocking(false)
        server.register(selector, SelectionKey.OP_ACCEPT)
        //启动输出用线程
        thread(name = "msgOutputThread") {
            Log.i(runningTag, "handleActionTcp 消息输出线程 ${Thread.currentThread().name} started")
            Looper.prepare()
            outputHandler = OutputMsgHandler(selector, charset)
            Looper.loop()
        }
        outputAudioThread.start()
        messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "ON")
        while (selector.select() > 0) {     //这是一个阻塞方法，It returns only after at least one channel is selected
            for (selectionKey in selector.selectedKeys()) {
                selector.selectedKeys().remove(selectionKey)
                if (selectionKey.isAcceptable) {          //如果该channel包含客户端的连接请求
                    val socketChannel = server.accept()
                    socketChannel.configureBlocking(false)
                    socketChannel.register(selector, SelectionKey.OP_READ)
                    selectionKey.interestOps(SelectionKey.OP_ACCEPT)
//                    messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "ON")
                }
                if (selectionKey.isReadable) {          //如果该channel有数据需要读取
                    val socketChannel = selectionKey.channel() as SocketChannel
                    val clientSocket = socketChannel.socket()
                    val buff = ByteBuffer.allocate(1024)
                    var arrivedString = ""
                    try {
                        while (socketChannel.read(buff) > 0) {
                            buff.flip()
                            arrivedString += charset.decode(buff)
                            buff.clear()
                        }
                        Log.i(runningTag, "handleActionTcp arrivedString: $arrivedString")
                        messageListener.onRemoteMsgArrived(arrivedString, clientSocket.inetAddress)
                        selectionKey.interestOps(SelectionKey.OP_READ)
                    } catch (ex: IOException) {
                        selectionKey.cancel()
                        selectionKey.channel()?.close()    //从Selector中删除指定的SelectionKey
                        messageListener.onLocalMsgOccurred("TcpSocketClientStatus", "OFF")
                    }
                }
            }
        }
    }

    private class OutputMsgHandler(private val selector: Selector, private val charset: Charset) : Handler() {
        private val runningTag = this.javaClass.simpleName
        private val typeBuff = ByteBuffer.allocate(1)
        private val sizeBuff = ByteBuffer.allocate(2)
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                Val.msgCodeByteArray -> {
                    for (selectionKey in selector.keys()) {
                        val channel = selectionKey.channel()
                        if (channel is SocketChannel) {
                            if (selectionKey.isWritable) {
                                //输出文本数据标志
                                typeBuff.put(Val.TextType)
                                typeBuff.flip()
                                channel.write(typeBuff)
                                typeBuff.clear()
                                //输出数据长度
                                sizeBuff.put(Util.intToByteArray((msg.obj as ByteArray).count()))
                                sizeBuff.flip()
                                channel.write(sizeBuff)
                                sizeBuff.clear()
                                //输出文本数据
                                channel.write(charset.encode((msg.obj as ByteArray).toString()))
                                this.removeMessages(Val.msgCodeByteArray)
                                Log.i(runningTag, "handleMessage: $msg")
                            }
                        }
                    }
                }
                Val.msgThreadInterrupted -> {
                    super.getLooper().quitSafely()
                }
            }
        }
    }

    private val outputAudioThread = thread(false) {
        Log.i(runningTag, "outputAudioThread 音频输出线程 ${Thread.currentThread().name} started")
        //实测解码器outputBuffer java.nio.DirectByteBuffer[pos=0 lim=4608 cap=9216]
        //实测播放器AudioTrack:
        //        sampleRateInHz = 44100
        //        buffSize = 14144
        //        audioAttributes = {AudioAttributes@5609} "AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x0 tags= bundle=null"
        //        audioFormat = {AudioFormat@5610} "AudioFormat: props=3 enc=2 chan=0x0 chan_index=0x0 rate=44100"
        //考虑到传输通道使用的是解码后outputBuffer中的pcm数据，因此选择用outputBuffer大小+两个Int的长度作为传输用buffer的尺寸。
        val buffer = ByteBuffer.allocate(Val.AudioBuffCapacity)
        val typeBuff = ByteBuffer.allocate(1)
        val sizeBuff = ByteBuffer.allocate(2)
        while (selector.isOpen) {
            //从阻塞队列中取出一帧音频，发送到每一个已连接的通道。如果没有音频数据到来，此处为阻塞状态
            val pcmTransferData = synchronousQueue.take() as PcmTransferData
            for (selectionKey in selector.keys()) {
                val channel = selectionKey.channel()
                if (channel is SocketChannel) {
                    //音频数据转换为字节数组
//                    val pcmTransferDataByteArray = Util.pcmTransferDataToByteArray(pcmTransferData)
                    val pcmTransferDataByteArray = SerializableUtil.serialize(pcmTransferData)
                    if (pcmTransferDataByteArray != null) {
                        //输出音频数据类型
                        typeBuff.put(Val.AudioType)
                        typeBuff.flip()
                        channel.write(typeBuff)
                        typeBuff.clear()
                        //输出音频数据长度
                        sizeBuff.put(Util.intToByteArray(pcmTransferDataByteArray.count()))
                        sizeBuff.flip()
                        channel.write(sizeBuff)
                        sizeBuff.clear()
                        //输出音频数据
//                    buffer.clear()
                        buffer.put(pcmTransferDataByteArray)
                        buffer.flip()
                        while (buffer.hasRemaining()) {
                            channel.write(buffer)
                        }
                        buffer.clear()
                        Log.i(runningTag, "outputAudioThread: audio data output")
                    }
                }
            }
        }
    }

    fun endTasks() {
        try {
            for (selectionKey in selector.keys()) {
                selectionKey.cancel()
                selectionKey.channel()?.close()
            }
            selector.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    /**
     * Handle action Udp in the provided background thread
     */
    private fun handleActionUdp() {
        TODO("Handle action Udp")
    }

    fun setMessageListener(listener: IMessageListener) {
        messageListener = listener
    }

}




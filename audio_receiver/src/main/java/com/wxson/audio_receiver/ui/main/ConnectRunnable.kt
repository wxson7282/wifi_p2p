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
    private val inputBuff = ByteBuffer.allocate(Val.InputBuffCapacity)
    private val cacheBuff = ByteBuffer.allocate(Val.CacheBuffCapacity)
    private var typeByte: Byte = 0
    private var sizeByteArray: ByteArray = ByteArray(4)
    private var messageSize: Int = 0
    private var messageRemainSize: Int = 0
    private var messageReadableSize: Int = 0
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

    //定义读取线程
    private val inputThread = thread(false) {
        try {
            cacheBuff.clear()       //清除缓存
            while (selector.select() > 0) {
                for (selectionKey in selector.selectedKeys()) {
                    selector.selectedKeys().remove(selectionKey)
                    if (selectionKey.isReadable) {
                        val socketChannel = selectionKey.channel() as SocketChannel
                        while (socketChannel.read(inputBuff) > 0) {     //循环读入输入区
                            inputBuff.flip()
                            while (inputBuff.remaining() > 0) {     //循环至inputBuff处理完
                                //包头处理
                                if (cacheBuff.position() == 0) {            //包类型未缓存
                                    typeByte = inputBuff.get()                           //取得包类型
                                    //如果包类型无法识别应该退出循环，废弃该输入数据
                                    if (typeByte != Val.TextType && typeByte != Val.AudioType) {
                                        Log.e(thisTag, "inputThread : unknown data type")
                                        break
                                    }
                                    cacheBuff.put(typeByte)                              //缓存包类型
                                }
                                if (!cacheByte(1)) continue       //缓存包长度第一字节
                                if (!cacheByte(2)) continue       //缓存包长度第二字节
                                if (!cacheByte(3)) continue       //缓存包长度第三字节
                                if (!cacheByte(4)) continue       //缓存包长度第四字节
                                //包头处理完
                                //包处理
                                //从缓冲区取得messageSizeByteArray
                                sizeByteArray[0] = cacheBuff.get(1)
                                sizeByteArray[1] = cacheBuff.get(2)
                                sizeByteArray[2] = cacheBuff.get(3)
                                sizeByteArray[3] = cacheBuff.get(4)
                                messageSize = Util.byteArrayToInt(sizeByteArray)         //转换为包长度
                                messageRemainSize = messageSize - (cacheBuff.position() - 5)       //计算未读完的包长度=包长度-已缓存包数据长度
                                //计算可读长度
                                messageReadableSize = if (inputBuff.remaining() >= messageRemainSize) messageRemainSize else inputBuff.remaining()
                                val messageReadableByteArray = ByteArray(messageReadableSize)           //根据可读长度建立messageByteArray
                                inputBuff.get(messageReadableByteArray)                                 //取得包数据
                                cacheBuff.put(messageReadableByteArray)                                 //缓存包数据
                                if (messageSize == cacheBuff.position() - 5) {                  //如果包完整
                                    cacheBuff.flip()
                                    val messagePacket = ByteArray(cacheBuff.limit())            //根据包长度定义messagePacket
                                    cacheBuff.get(messagePacket)                                //取得包
                                    cacheBuff.clear()                                           //清除缓存
                                    consume(messagePacket)                                      //消费messagePacket
                                } else {                                                        //如果包不完整
                                    continue            //跳到循环尾
                                }
                            }
                            inputBuff.clear()                           //清除inputBuff
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun consume(messagePacket: ByteArray) {
        when (messagePacket[0]) {
            Val.TextType -> {
                val arrivedString: String = messagePacket.copyOfRange(5, messagePacket.size - 5).toString()
                Log.i(thisTag, "接收到text data 内容：${arrivedString}")
                val msg = Message()
                msg.what = MsgType.ARRIVED_STRING.ordinal
                msg.obj = arrivedString
                mainHandler.sendMessage(msg)
                // 如果收到中断连接应答，则停止当前线程
                if (arrivedString == Val.msgDisconnectReply) {
                    TODO("Not yet implemented")
                }
            }
            Val.AudioType -> {
                if (messagePacket.size > 15) {
                    val pcmTransferData: PcmTransferData = Util.byteArrayToPcmTransferData(messagePacket.copyOfRange(5, messagePacket.size - 5))
//                    Log.i(thisTag, "接收到pcm data 第${pcmTransferData.frameCount}帧")
                    val msg = Message()
                    msg.what = MsgType.PCM_TRANSFER_DATA.ordinal
                    msg.obj = pcmTransferData
                    mainHandler.sendMessage(msg)
                }
            }
            else -> {
                Log.e(thisTag, "consume : unknown data type")
            }
        }
    }

    private fun cacheByte(cachePosition: Int) : Boolean {
        if (cacheBuff.position() == cachePosition) {
            if (inputBuff.remaining() > 0) {
                cacheBuff.put(inputBuff.get())
            } else {
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    override fun run() {
        selector = Selector.open()
        val inetSocketAddress = InetSocketAddress(serverIp, serverSocketPort)
        sockChannel = SocketChannel.open(inetSocketAddress)
        sockChannel.configureBlocking(false)
        sockChannel.register(selector, SelectionKey.OP_READ)
        //启动读取线程
        inputThread.start()
        Looper.prepare()
        connectThreadHandler = ThreadHandler(WeakReference(this))
        Looper.loop()
    }
}
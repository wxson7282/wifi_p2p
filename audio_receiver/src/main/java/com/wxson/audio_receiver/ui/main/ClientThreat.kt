package com.wxson.audio_receiver.ui.main

import android.os.Handler
import android.os.Message
import android.util.Log
import com.wxson.p2p_comm.PcmTransferData
import com.wxson.p2p_comm.Util
import com.wxson.p2p_comm.Val
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

class ClientThreat(private val mainHandler: Handler): Thread() {
    private val thisTag = this.javaClass.simpleName
    private lateinit var selector: Selector
    private val inputBuff = ByteBuffer.allocate(Val.InputBuffCapacity)
    private val cacheBuff = ByteBuffer.allocate(Val.CacheBuffCapacity)
    private var typeByte: Byte = 0
    private var sizeByteArray: ByteArray = ByteArray(2)
    private var messageSize: Int = 0
    private var messageRemainSize: Int = 0
    private var messageReadableSize: Int = 0

    override fun run() {
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
                                    cacheBuff.put(typeByte)                              //缓存包类型
                                }
                                if (cacheBuff.position() == 1) {            //包长度未缓存
                                    if (inputBuff.remaining() > 0) {
                                        sizeByteArray[0] = inputBuff.get()               //取得包长度第一字节
                                        cacheBuff.put(sizeByteArray[0])                  //缓存包长度第一字节
                                    } else {
                                        continue            //跳到循环尾
                                    }
                                }
                                if (cacheBuff.position() == 2) {            //包长度第二字节未缓存
                                    if (inputBuff.remaining() > 0) {
                                        sizeByteArray[1] = inputBuff.get()               //取得包长度第二字节
                                        cacheBuff.put(sizeByteArray[1])                  //缓存包长度第二字节
                                    } else {
                                        continue            //跳到循环尾
                                    }
                                }
                                //包头处理完
                                //包处理
                                //从缓冲区取得messageSizeByteArray
                                sizeByteArray[0] = cacheBuff.get(1)
                                sizeByteArray[1] = cacheBuff.get(2)
                                messageSize = Util.byteArrayToInt(sizeByteArray)         //转换为包长度
                                messageRemainSize = messageSize - (cacheBuff.limit() - 3)       //计算未读完的包长度=包长度-已缓存包数据长度
                                //计算可读长度
                                messageReadableSize = if (inputBuff.remaining() >= messageRemainSize) messageRemainSize else inputBuff.remaining()
                                val messageReadableByteArray = ByteArray(messageReadableSize)           //根据可读长度建立messageByteArray
                                inputBuff.get(messageReadableByteArray)                                 //取得包数据
                                cacheBuff.put(messageReadableByteArray)                                 //缓存包数据
                                if (messageSize == cacheBuff.position() - 3) {                  //如果包完整
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
                val arrivedString: String = messagePacket.copyOfRange(3, messagePacket.size - 3).toString()
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
                val pcmTransferData: PcmTransferData = Util.byteArrayToPcmTransferData(messagePacket.copyOfRange(3, messagePacket.size - 3))
                Log.i(thisTag, "接收到pcm data 第${pcmTransferData.frameCount}帧")
                val msg = Message()
                msg.what = MsgType.PCM_TRANSFER_DATA.ordinal
                msg.obj = pcmTransferData
                mainHandler.sendMessage(msg)
            }
            else -> {
                Log.e(thisTag, "consume : unknown data type")
            }
        }
    }
}
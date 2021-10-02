package com.wxson.audio_receiver.ui.main

import android.net.wifi.p2p.WifiP2pDevice
import android.os.Handler
import android.os.Message
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.wxson.p2p_comm.PcmTransferData
import com.wxson.p2p_comm.ViewModelMsg
import java.lang.ref.WeakReference
import java.util.*

class SampleViewModel : ViewModel() {
    val deviceAdapter: DeviceAdapter
//    private val app: Application =
    private val thisTag = this.javaClass.simpleName
    private val wifiP2pDeviceList = ArrayList<WifiP2pDevice>()
    private lateinit var wifiP2pDevice: WifiP2pDevice
    private var pcmPlayer: PcmPlayer? = null

    //region LiveData
    private val msgLiveData = MutableLiveData<ViewModelMsg>()
    fun getModelMsg(): LiveData<ViewModelMsg> {
        return msgLiveData
    }
    //endregion

    //region init & onCleared
    init {
        wifiP2pDeviceList.clear()
        deviceAdapter = DeviceAdapter(wifiP2pDeviceList)
        deviceAdapter.setClickListener(object : DeviceAdapter.OnClickListener {
            override fun onItemClick(position: Int) {
                wifiP2pDevice = wifiP2pDeviceList[position]
                msgLiveData.postValue(ViewModelMsg(MsgType.MSG.ordinal, wifiP2pDevice.deviceName + "将要连接"))
//                connect()
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
    }
    //endregion

    //region Handler
    class MainHandler(private var viewModel: WeakReference<SampleViewModel>) : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MsgType.ARRIVED_STRING.ordinal ->
                    viewModel.get()?.msgLiveData?.postValue(ViewModelMsg(MsgType.MSG.ordinal, "remote msg:" + msg.obj.toString()))
                MsgType.LOCAL_MSG.ordinal ->
                    viewModel.get()?.msgLiveData?.postValue(ViewModelMsg(MsgType.MSG.ordinal, "local msg:" + msg.obj.toString()))
                MsgType.PCM_TRANSFER_DATA.ordinal -> viewModel.get()?.playPcmData(msg.obj as PcmTransferData)
            }
        }
    }
    //endregion

    //region private method
    private fun playPcmData(pcmTransferData: PcmTransferData) {
        if (pcmPlayer == null) {
            pcmPlayer = PcmPlayer(pcmTransferData.sampleRateInHz)
        }
        pcmPlayer?.writePcmData(pcmTransferData.pcmData)
    }
    //endregion

}
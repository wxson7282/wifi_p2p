package com.wxson.audio_receiver.ui.main

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wxson.audio_receiver.R
import com.wxson.p2p_comm.ViewModelMsg
import com.wxson.p2p_comm.WifiP2pUtil.getDeviceStatus

class MainFragment : Fragment(), View.OnClickListener {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel
    private val runningTag = this.javaClass.simpleName
    private var tvMyDeviceName: TextView? = null
    private var tvMyDeviceMacAddress: TextView? = null
    private var tvRemoteDeviceName: TextView? = null
    private var tvRemoteDeviceAdress: TextView? = null
    private var tvGroupOwnerAddress: TextView? = null
    private var tvMyDeviceStatus: TextView? = null
//    private var tvMyConnectStatus: TextView? = null
    private var tvIsGroupOwner: TextView? = null
    private var tvGroupFormed: TextView? = null
    private var imgConnectStatus: ImageView? = null
    private var rvDeviceList: RecyclerView? = null
    private var btnDisconnect: Button? = null
    private lateinit var loadingDialog: LoadingDialog

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        view?.let {
            it.apply {
                // button
                findViewById<Button>(R.id.btnDisconnect)?.setOnClickListener(this@MainFragment)
                // textView
                tvMyDeviceName = findViewById(R.id.tvMyDeviceName)
                tvMyDeviceMacAddress = findViewById(R.id.tvMyDeviceMacAddress)
                tvRemoteDeviceName = findViewById(R.id.tvRemoteDeviceName)
                tvRemoteDeviceAdress = findViewById(R.id.tvRemoteDeviceAddress)
                tvGroupOwnerAddress = findViewById(R.id.tvGroupOwnerAddress)
                tvMyDeviceStatus = findViewById(R.id.tvMyDeviceStatus)
//                tvMyConnectStatus = findViewById(R.id.tvMyConnectStatus)
                tvIsGroupOwner = findViewById(R.id.tvIsGroupOwner)
                tvGroupFormed = findViewById(R.id.tvGroupFormed)
                // imageView
                imgConnectStatus = findViewById(R.id.imgConnectStatus)
                // RecyclerView
                rvDeviceList = findViewById(R.id.rvDeviceList)
                // Button
                btnDisconnect = findViewById(R.id.btnDisconnect)
                // LoadingDialog
                loadingDialog = LoadingDialog(this.context)
            }
        }

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        // set modelMsgObserver
        val viewModelMsgObserver: Observer<ViewModelMsg> = Observer { modelMsg -> modelMsgHandler(modelMsg) }
        viewModel.getModelMsg().observe(this, viewModelMsgObserver)
        // set adapter
        rvDeviceList?.adapter = viewModel.deviceAdapter
        rvDeviceList?.layoutManager = LinearLayoutManager(this.context)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuDirectEnable -> {
                viewModel.startWifiSetting()
            }
            R.id.menuDirectDiscover -> {
                viewModel.startDiscoverPeers()
            }
        }
        return true
    }

    override fun onClick(v: View?) {
        when (v?.id){
            R.id.btnDisconnect -> {
                viewModel.disconnect()
            }
        }
    }

    private fun modelMsgHandler(viewModelMsg: ViewModelMsg) {
        when (viewModelMsg.type) {
            MsgType.SHOW_CONNECT_STATUS.ordinal -> showConnectStatus(viewModelMsg.obj as Boolean)
            MsgType.MSG.ordinal -> showMsg(viewModelMsg.obj as String)
            MsgType.SHOW_SELF_DEVICE_INFO.ordinal -> showSelfDeviceInfo(viewModelMsg.obj as WifiP2pDevice)
            MsgType.SHOW_REMOTE_DEVICE_INFO.ordinal -> showRemoteDeviceInfo(viewModelMsg.obj as WifiP2pDevice)
            MsgType.SHOW_WIFI_P2P_INFO.ordinal -> showWifiP2pInfo(viewModelMsg.obj as WifiP2pInfo?)
            MsgType.SHOW_LOADING_DIALOG.ordinal -> loadingDialog.show(viewModelMsg.obj as String, cancelable = true, canceledOnTouchOutside = false)
            MsgType.DISMISS_LOADING_DIALOG.ordinal -> loadingDialog.dismiss()
            MsgType.CANCEL_LOADING_DIALOG.ordinal -> loadingDialog.cancel()
            MsgType.SET_BUTTON_ENABLED.ordinal -> {
                when (viewModelMsg.obj as String) {
                    "btnDisconnect" -> btnDisconnect?.isEnabled = true
                }
            }
            MsgType.SET_BUTTON_DISABLED.ordinal -> {
                when (viewModelMsg.obj as String) {
                    "btnDisconnect" -> btnDisconnect?.isEnabled = false
                }
            }
        }
    }

    private fun showMsg(msg: String){
        Toast.makeText(this.context, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showConnectStatus(connected: Boolean) {
        imgConnectStatus?.setImageResource(if (connected) R.drawable.ic_connected else R.drawable.ic_disconnected)
    }

    private fun showWifiP2pInfo(wifiP2pInfo: WifiP2pInfo?) {
        if (wifiP2pInfo == null) {
            tvIsGroupOwner?.text = ""
            tvGroupOwnerAddress?.text = ""
            tvGroupFormed?.text = ""
        } else {
            tvIsGroupOwner?.text = if (wifiP2pInfo.isGroupOwner) "是" else "否"
            tvGroupOwnerAddress?.text = wifiP2pInfo.groupOwnerAddress.hostAddress
            tvGroupFormed?.text = if (wifiP2pInfo.groupFormed) "是" else "否"
        }
    }

    private fun showSelfDeviceInfo(myDevice: WifiP2pDevice) {
        tvMyDeviceName?.text = myDevice.deviceName
        tvMyDeviceMacAddress?.text = myDevice.deviceAddress
        tvMyDeviceStatus?.text = getDeviceStatus(myDevice.status)
    }

    private fun showRemoteDeviceInfo(remoteDevice: WifiP2pDevice) {
        tvRemoteDeviceName?.text = remoteDevice.deviceName
        tvRemoteDeviceAdress?.text = remoteDevice.deviceAddress
    }

}
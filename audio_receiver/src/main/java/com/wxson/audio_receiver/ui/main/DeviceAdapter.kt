package com.wxson.audio_receiver.ui.main

import android.net.wifi.p2p.WifiP2pDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wxson.audio_receiver.R
import com.wxson.p2p_comm.WifiP2pUtil.getDeviceStatus

class DeviceAdapter(private val wifiP2pDeviceList: List<WifiP2pDevice>) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private lateinit var clickListener: OnClickListener

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvDeviceAddress: TextView = view.findViewById(R.id.tvDeviceAddress)
        val tvDeviceDetails: TextView = view.findViewById(R.id.tvDeviceDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        view.setOnClickListener { v ->
            clickListener.onItemClick((v.tag as Int))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvDeviceName.text = wifiP2pDeviceList[position].deviceName
        holder.tvDeviceAddress.text = wifiP2pDeviceList[position].deviceAddress
        holder.tvDeviceDetails.text = (getDeviceStatus(wifiP2pDeviceList[position].status))
        holder.itemView.tag = position
    }

    override fun getItemCount(): Int {
        return wifiP2pDeviceList.size
    }

    interface OnClickListener {
        fun onItemClick(position: Int)
    }

    fun setClickListener(clickListener: OnClickListener) {
        this.clickListener = clickListener
    }

}
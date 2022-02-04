package com.wxson.audio_player.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wxson.audio_player.R

class ClientsAdapter(private val clientList: List<String>) : RecyclerView.Adapter<ClientsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvClientAddress: TextView = view.findViewById(R.id.tvClientAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_client, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvClientAddress.text = clientList[position]
        holder.itemView.tag = position
    }

    override fun getItemCount() = clientList.size
}
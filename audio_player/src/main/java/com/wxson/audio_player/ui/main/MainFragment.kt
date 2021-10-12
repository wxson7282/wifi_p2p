package com.wxson.audio_player.ui.main

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.wxson.audio_player.R
import pub.devrel.easypermissions.EasyPermissions

class MainFragment : Fragment(), EasyPermissions.PermissionCallbacks, View.OnClickListener {

    private val runningTag = this.javaClass.simpleName
    private lateinit var viewModel: MainViewModel
    private lateinit var imageConnectStatus: ImageView
    private lateinit var btnCreateGroup: Button
    private lateinit var btnDeleteGroup: Button
    private lateinit var imageBtnPlay: ImageButton
    private lateinit var imageBtnPause: ImageButton
    private lateinit var imageBtnStop: ImageButton
    private lateinit var imageBtnMute: ImageButton


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.apply {
            findViewById<Button>(R.id.btnTest).setOnClickListener{
                findNavController().navigate(R.id.action_MainFragment_to_SecondFragment)
            }
            btnCreateGroup = findViewById(R.id.btnCreateGroup)
            btnCreateGroup.setOnClickListener(this@MainFragment)
            btnDeleteGroup = findViewById(R.id.btnDeleteGroup)
            btnDeleteGroup.setOnClickListener(this@MainFragment)
            imageBtnPlay = findViewById(R.id.imageBtnPlay)
            imageBtnPlay.setOnClickListener(this@MainFragment)
            imageBtnStop = findViewById(R.id.imageBtnStop)
            imageBtnStop.setOnClickListener(this@MainFragment)
            imageBtnPause = findViewById(R.id.imageBtnPause)
            imageBtnPause.setOnClickListener(this@MainFragment)
            imageBtnMute = findViewById(R.id.imageBtnMute)
            imageBtnMute.setOnClickListener(this@MainFragment)

            imageConnectStatus = findViewById(R.id.imageConnected)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        //申请权限
        requestLocationPermission()
        // registers observer for information from viewModel
        val localMsgObserver: Observer<String> = Observer { localMsg ->
            when (localMsg) {
                "createGroup onSuccess" -> {
                    btnCreateGroup.isEnabled = false
                    btnDeleteGroup.isEnabled = true
                }
                "removeGroup onSuccess" -> {
                    btnCreateGroup.isEnabled = true
                    btnDeleteGroup.isEnabled = false
                }
            }
            showMsg(localMsg)
        }
        viewModel.getLocalMsg().observe(viewLifecycleOwner, localMsgObserver)
        val connectStatusObserver: Observer<Boolean> = Observer { isConnected -> connectStatusHandler(isConnected!!) }
        viewModel.getConnectStatus().observe(viewLifecycleOwner, connectStatusObserver)
    }

    private fun connectStatusHandler(isConnected: Boolean) {
        if (isConnected){
            imageConnectStatus.setImageDrawable(getDrawable(requireContext(), R.drawable.ic_connected))
        }
        else{
            imageConnectStatus.setImageDrawable(getDrawable(requireContext(), R.drawable.ic_disconnected))
        }
    }

    //申请位置权限
    private fun requestLocationPermission() {
        Log.i(runningTag, "requestLocationPermission")
        val perms = Manifest.permission.ACCESS_FINE_LOCATION
        if (EasyPermissions.hasPermissions(requireContext(), perms)) {
            Log.i(runningTag, "已获取ACCESS_FINE_LOCATION权限")
            // Already have permission, do the thing
        } else {
            Log.i(runningTag, "申请ACCESS_FINE_LOCATION权限")
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, getString(R.string.position_rationale), 1, perms
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.i(runningTag, "onRequestPermissionsResult")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.i(runningTag, "onPermissionsGranted")
        Log.i(runningTag, "获取权限成功$perms")
        showMsg("获取权限成功")
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.i(runningTag, "onPermissionsDenied")
        Log.i(runningTag, "获取权限失败，退出当前页面$perms")
        showMsg("获取权限失败")
        activity?.finish()  //退出当前页面
    }

    private fun showMsg(msg: String){
        Toast.makeText(this.context, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnCreateGroup -> {
                viewModel.createGroup()
            }
            R.id.btnDeleteGroup -> {
                viewModel.removeGroup()
            }
            R.id.imageBtnPlay -> {
                viewModel.play(getString(R.string.sample_music_name))
            }
            R.id.imageBtnStop -> {
                viewModel.stop()
            }
            R.id.imageBtnPause -> {
                viewModel.pause()
            }
            R.id.imageBtnMute -> {
//                imageBtnMute.imageAlpha = 100
//                imageBtnMute.isEnabled = false
                viewModel.mute()
            }
        }
    }

    private fun setImageBtnEnabled(imageBtn: ImageButton, enabled: Boolean) {
        if (enabled) {
            imageBtn.imageAlpha = 0
            imageBtn.isEnabled = true
        } else {
            imageBtn.imageAlpha = 100
            imageBtn.isEnabled = false
        }
    }

    private fun setImageBtnPauseEffective(isEffective: Boolean) {
        if (isEffective) {
            imageBtnPause.setImageResource(R.drawable.ic_play_pause)
        } else {
            imageBtnPause.setImageResource(R.drawable.ic_media_pause_light)
        }
    }

    private fun setImageBtnMuteEffective(isEffective: Boolean) {
        if (isEffective) {
            imageBtnMute.setImageResource(R.drawable.ic_mute_fill)
        } else {
            imageBtnMute.setImageResource(R.drawable.ic_speaker_on)
        }
    }

    private fun setPlayerBtnByState(state: String) {
        when (state){
            "STOPPED" -> {
                setImageBtnEnabled(imageBtnMute, false)
                setImageBtnEnabled(imageBtnPause, false)
                setImageBtnEnabled(imageBtnPlay, true)
                setImageBtnEnabled(imageBtnStop,false)
            }
            "PLAYING" -> {
                setImageBtnEnabled(imageBtnMute, true)
                setImageBtnEnabled(imageBtnPause, true)
                setImageBtnEnabled(imageBtnPlay, false)
                setImageBtnEnabled(imageBtnStop,true)
            }
            "PAUSED" -> {
                setImageBtnEnabled(imageBtnMute, false)
                setImageBtnEnabled(imageBtnPause, true)
                setImageBtnEnabled(imageBtnPlay, true)
                setImageBtnEnabled(imageBtnStop,true)
            }
            "MUTE" -> {
                setImageBtnEnabled(imageBtnMute, false)
                setImageBtnEnabled(imageBtnPause, false)
                setImageBtnEnabled(imageBtnPlay, true)
                setImageBtnEnabled(imageBtnStop,false)
            }
        }

    }
}

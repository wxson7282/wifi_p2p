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
import com.wxson.audio_player.R
import com.wxson.audio_player.ui.main.state.*
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
    private var playerContext: PlayerContext? = null
    private val playingBtnStates: Int = 0b0111
    private val stoppedBtnStates: Int = 0b1000
    private val pausedBtnStates: Int = 0b0010
    private val muteBtnStates: Int = 0b0001
    private val nonEffective: Int = 0b00
    private val pauseEffective: Int = 0b10
    private val muteEffective: Int = 0b01
    private var reservedBtnStates: Int = 0
    private var reservedBtnEffectiveStates: Int = 0

    companion object {
        fun newInstance() = MainFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(runningTag, "onCreate")
        super.onCreate(savedInstanceState)
        retainInstance = true       // 横竖屏切换时不销毁fragment
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.i(runningTag, "onCreateView")
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.i(runningTag, "onViewCreated")
        super.onViewCreated(view, savedInstanceState)
        view.apply {
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
        Log.i(runningTag, "onActivityCreated")
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        //申请权限
        requestLocationPermission()
        // registers observer for information from viewModel
        val localMsgObserver: Observer<String> = Observer { localMsg ->
            when (localMsg) {
                "group is formed" -> {
                    btnCreateGroup.isEnabled = false
                    btnDeleteGroup.isEnabled = true
                }
                "group is not formed" -> {
                    btnCreateGroup.isEnabled = true
                    btnDeleteGroup.isEnabled = false
                }
            }
            showMsg(localMsg)
        }
        viewModel.getLocalMsg().observe(viewLifecycleOwner, localMsgObserver)
        val connectStatusObserver: Observer<Boolean> = Observer { isConnected -> connectStatusHandler(isConnected!!) }
        viewModel.getConnectStatus().observe(viewLifecycleOwner, connectStatusObserver)
        // 新建fragment时初始状态为StoppedState
        if (playerContext == null) {
            playerContext = PlayerContext(viewModel)            //定义环境角色
            playerContext!!.setCurrentState(StoppedState())       //设置初始状态
        }
        setButton(playerContext!!.getCurrentState())
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
        playerContext!!.apply {
            when (v.id) {
                R.id.btnCreateGroup -> {
                    viewModel.createGroup()
                }
                R.id.btnDeleteGroup -> {
                    viewModel.removeGroup()
                }
                R.id.imageBtnPlay -> {
                    playHandle()
                    setButton(getCurrentState())
                }
                R.id.imageBtnStop -> {
                    stopHandle()
                    setButton(getCurrentState())
                }
                R.id.imageBtnPause -> {
                    when (getCurrentState()) {
                        is PlayingState, is ResumeState, is UnMuteState -> {
                            pauseHandle()
                            setButton(getCurrentState())
                        }
                        is PausedState -> {
                            resumeHandle()
                            setButton(getCurrentState())
                        }
                    }
                }
                R.id.imageBtnMute -> {
                    when (getCurrentState()) {
                        is PlayingState, is ResumeState, is UnMuteState -> {
                            muteHandle()
                            setButton(getCurrentState())
                        }
                        is MuteState -> {
                            unMuteHandle()
                            setButton(getCurrentState())
                        }
                    }
                }
            }
        }
    }

    private fun setButton(playerState: AbstractState) {
        when (playerState) {
            is StoppedState -> {
                setBtnEffective(nonEffective)
                setPlayerBtn(stoppedBtnStates)
            }
            is PlayingState, is ResumeState, is UnMuteState -> {
                setBtnEffective(nonEffective)
                setPlayerBtn(playingBtnStates)
            }
            is PausedState -> {
                setBtnEffective(pauseEffective)
                setPlayerBtn(pausedBtnStates)
            }
            is MuteState -> {
                setBtnEffective(muteEffective)
                setPlayerBtn(muteBtnStates)
            }
        }
    }

    private fun setPlayerBtn(btnStates: Int) {
        reservedBtnStates = btnStates
        setImageBtnEnabled(imageBtnPlay, (0b1000 and btnStates) != 0)
        setImageBtnEnabled(imageBtnStop, (0b0100 and btnStates) != 0)
        setImageBtnEnabled(imageBtnPause, (0b0010 and btnStates) != 0)
        setImageBtnEnabled(imageBtnMute, (0b0001 and btnStates) != 0)
    }

    private fun setBtnEffective(effectStates: Int) {
        reservedBtnEffectiveStates = effectStates
        setImageBtnPauseEffective((0b10 and effectStates) != 0)
        setImageBtnMuteEffective((0b01 and effectStates) != 0)
    }

    private fun setImageBtnEnabled(imageBtn: ImageButton, enabled: Boolean) {
        if (enabled) {
            imageBtn.imageAlpha = 255
            imageBtn.isEnabled = true
        } else {
            imageBtn.imageAlpha = 60
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
            imageBtnMute.setImageResource(R.drawable.ic_speaker_on)
        } else {
            imageBtnMute.setImageResource(R.drawable.ic_mute_fill)
        }
    }

}

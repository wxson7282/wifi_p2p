package com.wxson.audio_receiver

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wxson.audio_receiver.ui.main.MainFragment
import pub.devrel.easypermissions.EasyPermissions

class MainActivity : AppCompatActivity() , EasyPermissions.PermissionCallbacks {

    private val runningTag = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, MainFragment.newInstance())
                .commitNow()
        }
        //申请定位权限
        requestLocationPermission()
    }

    //申请位置权限
    private fun requestLocationPermission() {
        Log.i(runningTag, "requestLocationPermission")
        val perms = Manifest.permission.ACCESS_FINE_LOCATION
        if (EasyPermissions.hasPermissions(this, perms)) {
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
        finish()  //退出当前页面
    }

    private fun showMsg(msg: String){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
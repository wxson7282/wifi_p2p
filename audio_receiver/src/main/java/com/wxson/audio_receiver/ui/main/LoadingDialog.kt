package com.wxson.audio_receiver.ui.main

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import com.wxson.audio_receiver.R

/**
 * Created by wxson on 2021/03/10.
 * Package com.wxson.audio_receiver.
 */
class LoadingDialog(context: Context) : Dialog(context, R.style.LoadingDialogTheme) {
    private val ivLoading: ImageView
    private val tvHint: TextView
    private val animation: Animation

    init {
        setContentView(R.layout.dialog_loading)
        ivLoading = findViewById<View>(R.id.iv_loading) as ImageView
        tvHint = findViewById<View>(R.id.tv_hint) as TextView
        animation = AnimationUtils.loadAnimation(context, R.anim.loading_dialog)
    }

    fun show(hintText: String?, cancelable: Boolean, canceledOnTouchOutside: Boolean) {
        setCancelable(cancelable)
        setCanceledOnTouchOutside(canceledOnTouchOutside)
        tvHint.text = hintText
        ivLoading.startAnimation(animation)
        show()
    }

    override fun cancel() {
        super.cancel()
        animation.cancel()
        ivLoading.clearAnimation()
    }

    override fun dismiss() {
        super.dismiss()
        animation.cancel()
        ivLoading.clearAnimation()
    }
}
package com.wxson.audio_player.ui.main

import android.media.MediaFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.wxson.audio_player.R
import com.wxson.p2p_comm.AudioUtil
import kotlinx.android.synthetic.main.fragment_second.*

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_second, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnGetAudioTracks).setOnClickListener {
            val pathName: String? = this.activity?.application?.cacheDir?.path
            if (pathName != null) {
                val audioTracks = AudioUtil.getAudioTracks(getString(R.string.sample_music_name))
                textView.apply {
                    text = ""
                    append("resource name = R.raw.subaru\n")
                    append("audio tracks size = ${audioTracks.size}\n")
                    for (i in 0 until audioTracks.size) {
                        append("MIME = ${audioTracks[i].getString(MediaFormat.KEY_MIME)}\n")
                        append("CHANNEL_COUNT = ${audioTracks[i].getInteger(MediaFormat.KEY_CHANNEL_COUNT)}\n")
                        append("BIT_RATE = ${audioTracks[i].getInteger(MediaFormat.KEY_BIT_RATE)}\n")
                        append("DURATION = ${audioTracks[i].getLong(MediaFormat.KEY_DURATION)}\n")
                        append("KEY_SAMPLE_RATE = ${audioTracks[i].getInteger(MediaFormat.KEY_SAMPLE_RATE)}\n")
                    }
                }
            }
        }
    }
}
package com.arenacast.mediaprojection.rtmp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.arenacast.mediaprojection.EB_RTMP_SERVICE_STOP_STREAM
import com.arenacast.mediaprojection.INTENT_FILTER_RTMP_SERVICE
import com.arenacast.mediaprojection.KEY_ACTION
import com.arenacast.mediaprojection.EXTRA_SEND_BROADCAST

class RtmpServiceBroadcastReceiver private constructor(
    private val onReceive: (intent: Intent?) -> Unit
): BroadcastReceiver() {
//class RtmpServiceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private val TAG = "RtmpSvcBroadcastRcv"

        private var receiver: RtmpServiceBroadcastReceiver? = null

        fun register(context: Context, onReceive: (intent: Intent?) -> Unit) {
            if (receiver == null) {
                receiver = RtmpServiceBroadcastReceiver(onReceive)
                context.registerReceiver(receiver, IntentFilter(INTENT_FILTER_RTMP_SERVICE))
            }
        }

        fun unregister(context: Context) {
            receiver?.let(context::unregisterReceiver)
            receiver = null
        }

        fun sendBroadcast(event: String): Intent =
            Intent(INTENT_FILTER_RTMP_SERVICE).apply {
                putExtra(EXTRA_SEND_BROADCAST, event)
                putExtra(KEY_ACTION, event)
            }

        fun stopStream(): Intent =
            Intent(INTENT_FILTER_RTMP_SERVICE).apply {
                putExtra(KEY_ACTION, EB_RTMP_SERVICE_STOP_STREAM)
            }
    }

    // intent?.action에 intentfilter값이 들어간다.
    override fun onReceive(context: Context?, intent: Intent?) {
        //Log.e(TAG, "onReceive >>>>>>>>>>>>>>>>>>>>>>>>>>> ")
        //intent?.action = intent?.getStringExtra(KEY_ACTION)
        onReceive(intent)
    }
}
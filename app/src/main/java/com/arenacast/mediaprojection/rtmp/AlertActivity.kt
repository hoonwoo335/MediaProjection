package com.arenacast.mediaprojection.rtmp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.arenacast.mediaprojection.EB_RTMP_SERVICE_STOP_SERVICE
import com.arenacast.mediaprojection.R

class AlertActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_alert)

        val dialog = AlertDialog.Builder(this).apply {
            setTitle("Notice")
            setMessage("Are you sure want to stop service?")
            setCancelable(false)
        }
        dialog.setPositiveButton("Ok") { dialog, which ->
            sendBroadcast(RtmpServiceBroadcastReceiver.sendBroadcast(EB_RTMP_SERVICE_STOP_SERVICE))
            //stopService(intent)
            (context as RtmpService).stopSelf()
            finish()
        }
        dialog.setNegativeButton("Cancel") { dialog, which ->
            finish()
        }

        val alert = dialog.create()
        alert.show()

    }

    companion object {
        var context: Context? = null

        fun open(context: Context): Intent {
            this.context = context
            return Intent(context, AlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

    }
}
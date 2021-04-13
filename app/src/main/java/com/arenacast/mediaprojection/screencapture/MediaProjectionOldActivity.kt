package com.arenacast.mediaprojection.screencapture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.arenacast.mediaprojection.R
import com.arenacast.mediaprojection.REQUEST_MEDIA_PROJECTION
import com.arenacast.mediaprojection.model.MessageRoute
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MediaProjectionOldActivity : AppCompatActivity() {
    val TAG = "MdProjectionOldActivity"

    private val STATE_RESULT_CODE = "result_code"
    private val STATE_RESULT_DATA = "result_data"

    //private val REQUEST_MEDIA_PROJECTION = 1

    private var screenDensity: Int = 0

    private var resultCode: Int = 0
    private var resultData: Intent? = null

    private lateinit var surface: Surface
    private lateinit var surfaceView: SurfaceView
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var btnToggle: Button


    //----------------------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate ----------------")
        setContentView(R.layout.activity_media_projection)

        surfaceView = findViewById(R.id.surface)
        surface = surfaceView.holder.surface

        btnToggle = findViewById(R.id.toggle)
        btnToggle.setOnClickListener {
            if (virtualDisplay == null) {
                startScreenCapture()
            }
            else {
                stopScreenCapture()
            }
        }

        //val intent = intent
        savedInstanceState?.let {
            resultCode = savedInstanceState.getInt(STATE_RESULT_CODE)
            resultData = savedInstanceState.getParcelable<Intent>(STATE_RESULT_DATA)
        }

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenDensity = metrics.densityDpi
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        resultData?.let {
            outState.putInt(STATE_RESULT_CODE, resultCode)
            outState.putParcelable(STATE_RESULT_DATA, resultData)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy ----------------")
        tearDownMediaProjection()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause ----------------")
        stopScreenCapture()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "onActivityResult ----------------")

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            when(resultCode) {
                Activity.RESULT_OK -> {
                    Log.i(TAG, "Starting screen capture")
                    this.resultCode = resultCode
                    this.resultData = data

                    setupMediaProjection()
                    setupVirtualDisplay()
                }
                else -> {
                    val parentLayout: View = findViewById(android.R.id.content)
                    Snackbar.make(parentLayout, R.string.user_cancelled, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    //----------------------------------------------------------------------------------------
    private fun setupMediaProjection() {
        Log.d(TAG, "setupMediaProjection ----------------")

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData!!)
    }

    private fun tearDownMediaProjection() {
        Log.d(TAG, "tearDownMediaProjection ----------------")

        mediaProjection?.let {
            mediaProjection!!.stop()
            mediaProjection = null
        }

    }

    private fun startScreenCapture() {
        Log.d(TAG, "startScreenCapture ----------------")

        if (mediaProjection != null) {
            setupVirtualDisplay()
        }
        else if (resultCode != 0 && resultData != null) {
            setupMediaProjection()
            setupVirtualDisplay()
        }
        else {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
        }
    }

    private fun stopScreenCapture() {
        Log.d(TAG, "stopScreenCapture ----------------")

        virtualDisplay?.let {
            virtualDisplay!!.release()
            virtualDisplay = null
            btnToggle.text = "Start"
        }

    }

    private fun setupVirtualDisplay() {
        Log.d(TAG, "Setting up a VirtualDisplay: ${surfaceView.width} x ${surfaceView.height} ($screenDensity)")

        virtualDisplay = mediaProjection!!.createVirtualDisplay("MediaProjection",
            surfaceView.width,
            surfaceView.height,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface,
            null,
            null)
        btnToggle.text = "Stop"
    }

    //---------------------------------------------------------------------------------------
    companion object {

        fun newInstance(context: Context): Intent =
            Intent(context, MediaProjectionActivity::class.java)
    }
}
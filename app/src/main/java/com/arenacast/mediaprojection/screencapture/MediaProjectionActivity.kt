package com.arenacast.mediaprojection.screencapture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arenacast.mediaprojection.*
import com.arenacast.mediaprojection.EB_MEDIA_PROJECTION_ON_STARTED
import com.arenacast.mediaprojection.EB_MEDIA_PROJECTION_PERMISSION_GET
import com.arenacast.mediaprojection.REQUEST_MEDIA_PROJECTION
import com.arenacast.mediaprojection.model.MessageRoute
import com.arenacast.mediaprojection.screencapture.MediaProjectionResultContract
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MediaProjectionActivity : AppCompatActivity() {

    val TAG = "MediaProjectionActivity"

    private val STATE_RESULT_CODE = "result_code"
    private val STATE_RESULT_DATA = "result_data"

    //private val REQUEST_MEDIA_PROJECTION = 1

    private var screenDensity: Int = 0
    private var isStart = false
    private var isBtnLock = false
    private var isPermissionInit = false
    private var isOverlayService = false

    private var resultCode: Int = 0
    private var resultData: Intent? = null

    private lateinit var surface: Surface
    private lateinit var surfaceView: SurfaceView
    private lateinit var btnToggle: Button


    //----------------------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate ----------------")
        isOverlayService = intent.getBooleanExtra("activityType", false)

        if (isOverlayService) {
            setTheme(R.style.Theme_Transparent)
            setContentView(R.layout.activity_media_projection_empty)

            // 서비스로부터 실행되서 퍼미션 획득의 기능만을 수행한다.
            getPermission()
        }
        else {
            setContentView(R.layout.activity_media_projection)

            surfaceView = findViewById(R.id.surface)
            surface = surfaceView.holder.surface

            btnToggle = findViewById(R.id.toggle)
            btnToggle.setOnClickListener {
                if (isBtnLock) {
                    return@setOnClickListener
                }

                isBtnLock = true

                if (isStart) {
                    stopMediaProjection()
                }
                else {
                    mediaProjectionInit()
                }
            }

            EventBus.getDefault().register(this)
        }

        //val intent = intent
        savedInstanceState?.let {
            resultCode = savedInstanceState.getInt(STATE_RESULT_CODE)
            resultData = savedInstanceState.getParcelable<Intent>(STATE_RESULT_DATA)
        }

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        screenDensity = metrics.densityDpi
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

        if (!isOverlayService) {
            stopService()
            EventBus.getDefault().unregister(this)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause ----------------")
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

                    mediaProjectionPermissionInit(null)
                }
                else -> {
                    val parentLayout: View = findViewById(android.R.id.content)
                    Snackbar.make(parentLayout, R.string.user_cancelled, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    //----------------------------------------------------------------------------------------
    // EventBus Receive Event
    // This method will be called when a MessageEvent is posted (in the UI thread for Toast)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(messageRoute: MessageRoute) {
        Log.d(TAG, "onMessageEvent: " + messageRoute)

        when(messageRoute.param1) {
            EB_MEDIA_PROJECTION_PERMISSION_GET -> {
                //getPermission()
                getPermissionForMediaProjection()
            }
            EB_MEDIA_PROJECTION_ON_STARTED -> {
                isStart = true
                btnToggle.text = "Stop"
                isBtnLock = false
            }
            EB_MEDIA_PROJECTION_ON_STOP -> {
                isStart = false
                btnToggle.text = "Start"
                isBtnLock = false
            }
            EB_MEDIA_PROJECTION_ON_REJECT -> {
                val parentLayout: View = findViewById(android.R.id.content)
                Snackbar.make(parentLayout, R.string.user_cancelled, Snackbar.LENGTH_SHORT).show()
            }
            EB_MEDIA_PROJECTION_ON_INIT -> startMediaProjection()
            else -> {

            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == RC_WRITE_STORAGE_RECORD_AUDIO) {
                getPermissionForMediaProjection()
            }
        }
        else {
            val parentLayout: View = findViewById(android.R.id.content)
            Snackbar.make(parentLayout, "Permission request was denied by user!!", Snackbar.LENGTH_SHORT).show()
        }
    }
    private fun getPermission() {
        getPermissionForWriteExternalStorage()
    }

    private fun getPermissionForWriteExternalStorage() {
        val hasWriteExternalStoragePermission: Int = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val hasRecordAudioPermission: Int = ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO)

        if (hasWriteExternalStoragePermission == PackageManager.PERMISSION_GRANTED &&
            hasRecordAudioPermission == PackageManager.PERMISSION_GRANTED) {

            getPermissionForMediaProjection()
        }
        else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO),
                RC_WRITE_STORAGE_RECORD_AUDIO)
        }
    }

    private fun getPermissionForMediaProjection() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        //startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
        //val result = registerForActivityResult(MediaProjectionResultContract()) { }

        val result: ActivityResultLauncher<Intent> = registerForActivityResult(MediaProjectionResultContract()) {
            this.resultCode = it.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            if (this.resultCode == Activity.RESULT_OK) {
                this.resultData = it.getParcelableExtra(EXTRA_REQUEST_DATA)

                mediaProjectionPermissionInit(it)
            }
            else {
                stopService()

                val parentLayout: View = findViewById(android.R.id.content)
                Snackbar.make(parentLayout, R.string.user_cancelled, Snackbar.LENGTH_SHORT).show()
            }
        }
        result.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun mediaProjectionInit() {
        if (isPermissionInit) {
            startMediaProjection()
        }
        else {
            runService(MediaProjectionService.newService(this))
        }
    }

    private fun mediaProjectionPermissionInit(intent: Intent?) {
        if (isOverlayService) {
            //오버레이 서비스는 퍼미션 획득이 목적이므로 이후에 이벤트를 받을필요가 없다. 종료전 기존 구독을 취소한다.
            EventBus.getDefault().unregister(this)
            EventBus.getDefault().post(MessageRoute(EB_MEDIA_PROJECTION_PERMISSION_INIT, "", intent))
            finish()
        }
        else {
            isPermissionInit = true;
            runService(MediaProjectionService.permissionInit(this, this.resultCode, this.resultData!!))
        }
    }

    private fun startMediaProjection() {
        Log.d(TAG, "startMediaProjection --------------------")
        runService(MediaProjectionService.newStartMediaProjection(this, surface,
            "MediaProjection",
            surfaceView.width,
            surfaceView.height,
            screenDensity))
    }
    private fun stopMediaProjection() {
        Log.d(TAG, "stopMediaProjection --------------------")
        runService(MediaProjectionService.newStopMediaProjection(this))
    }

    private fun runService(service: Intent) {
        Log.d(TAG, "runService --------------------")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service)
            //startService(service)
        } else {
            startService(service)
        }
    }

    private fun stopService() {
        Log.d(TAG, "stopService --------------------")
        runService(MediaProjectionService.newStopService(this))
        //MediaProjectionAccessServiceBroadcastReceiver.unregister(this)

        if (isOverlayService) {
            finish()
        }
    }

    //---------------------------------------------------------------------------------------
    companion object {
        fun newInstance(context: Context, isOverlayService: Boolean): Intent =
            Intent(context, MediaProjectionActivity::class.java).apply {
                putExtra("activityType", isOverlayService)
                if (isOverlayService) {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
    }

}

internal class MediaProjectionResultContract : ActivityResultContract<Intent, Intent>() {
    override fun createIntent(context: Context, input: Intent?): Intent =
        input!!

    override fun parseResult(resultCode: Int, intent: Intent?): Intent =
        if (resultCode == Activity.RESULT_OK && intent != null) {
            Intent(INTENT_FILTER_MEDIA_PROJECTION).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_REQUEST_DATA, intent)
                putExtra(KEY_ACTION, ACTION_PERMISSION_INIT)
            }
        }
        else {
            Intent(INTENT_FILTER_MEDIA_PROJECTION).apply {
                putExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            }
        }

}
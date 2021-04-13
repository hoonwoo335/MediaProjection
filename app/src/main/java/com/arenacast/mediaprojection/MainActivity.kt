package com.arenacast.mediaprojection

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Camera
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.View
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.arenacast.mediaprojection.model.MessageRoute
import com.arenacast.mediaprojection.rtmp.RtmpService
import com.arenacast.mediaprojection.rtmp.RtmpServiceBroadcastReceiver
import com.arenacast.mediaprojection.screencapture.*
import com.arenacast.mediaprojection.service.VideoViewService
import com.arenacast.mediaprojection.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus

class MainActivity : AppCompatActivity() {
    val TAG = "MainActivity"

    private var resultCode: Int = 0
    private var resultData: Intent? = null
    private lateinit var notificationManager: NotificationManager

    private var isRunService = false
    private var runServiceType = SERVICE_TYPE_RTMP

    private lateinit var dataBinding: ActivityMainBinding

    /*
    다른앱 위에 그리기 권한설정이 안드로이드11부터는 해당 옵션설정화면으로 바로 가지지 않고 한단계 이전메뉴로만 나타나는걸로 변경된다.
    따라서 유저가 직접 그메뉴에서 앱을 선택해서 권한옵션 화면으로 진입하여야 한다. 한번더 선택을 해야되는 상황으로 변경
    Android11 Meetup에서 이런 부분때문에 Bubble, FullscreenIntent, PIP을 활용하라고 권하고 있다.
     */
    private val canOverlay = registerForActivityResult(OverlayActivityResultContract()) {
        Log.d(TAG, "canOverlay - Permission Granted")
        runService(this.runServiceType)
    }

    //----------------------------------------------------------------------------------------
    // Life Cycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        dataBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        dataBinding.lifecycleOwner = this
        Log.d(TAG, "onCreate ----------------")

        //val r = Resources.getSystem()
        //onConfigurationChanged(r.configuration)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        //val btnStartRTMPService = findViewById<Button>(R.id.rtmp_service_btn_start)
        //val btnStartMediaProjectionService = findViewById<Button>(R.id.mediaprojection_service_btn_start)
        //val btnStartMediaProjectionActivity = findViewById<Button>(R.id.mediaprojection_activity_btn_start)

        dataBinding.rtmpServiceBtnStart.setOnClickListener {
            if (isRunService || RtmpService.isStreaming() || RtmpService.isRecording()) {
                return@setOnClickListener
            }
            // for RtmpService
            runService(SERVICE_TYPE_RTMP)
        }

        dataBinding.mediaprojectionServiceBtnStart.setOnClickListener {
            if (isRunService) return@setOnClickListener
            // for VideoViewService
            //runService(SERVICE_TYPE_LOCAL)
        }

        dataBinding.mediaprojectionActivityBtnStart.setOnClickListener {
            if (isRunService) return@setOnClickListener
            //startActivity(MediaProjectionActivity.newInstance(this, false))
        }

        /*val perms = HashMap<String, Int>()
        perms[Manifest.permission.RECORD_AUDIO] = PackageManager.PERMISSION_GRANTED
        perms[Manifest.permission.CAMERA] = PackageManager.PERMISSION_GRANTED

        if (perms[Manifest.permission.RECORD_AUDIO] == PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "${Manifest.permission.RECORD_AUDIO} - PERMISSION_GRANTED")
        }
        else {
            Log.e(TAG, "${Manifest.permission.RECORD_AUDIO} - PERMISSION_DENIED")
        }

        if (perms[Manifest.permission.CAMERA] == PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "${Manifest.permission.CAMERA} - PERMISSION_GRANTED")
        }
        else {
            Log.e(TAG, "${Manifest.permission.CAMERA} - PERMISSION_DENIED")
        }*/


    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy ----------------")
        stopNotification()
        unregisterReceiver()

        //EventBus.getDefault().post(MessageRoute(EB_RTMP_SERVICE_STOP_SERVICE, ""))
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause ----------------")
    }

    /*
    AndroidManifest.xml
    android:configChanges="orientation|keyboardHidden|screenSize"
    orientation만 추가되면 메소드가 호출되지 않는다. screenSize가 같이 포함되야 정상적으로 호출됨.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        //Log.e(TAG, "onConfigurationChanged - ${newConfig.orientation}")

        /*when(newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                EventBus.getDefault().post(MessageRoute(EB_RTMP_SERVICE_PORTRAIT, "1"))
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                EventBus.getDefault().post(MessageRoute(EB_RTMP_SERVICE_LANDSCAPE, "2"))
            }
        }*/
    }

    //----------------------------------------------------------------------------------------
    // Service Methods
    private fun showRejectDialog() {
        AlertDialog.Builder(this)
            .setTitle("Overlay Permission")
            .setMessage("Please grant permission")
            .setPositiveButton("Ok") { _, _ ->
                canOverlay.launch("package:$packageName")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runService(runServiceType: Int) {
        Log.d(TAG, "runService - type:$runServiceType --------------------")
        this.runServiceType = runServiceType

        if (Settings.canDrawOverlays(this)) {
            when(runServiceType) {
                0 -> {
                    initRegisterReceiver()
                    getInstanceForRtmpService()
                    getPermissionForRtmpService()
                }
                1 -> startService()
            }
        }
        else {
            showRejectDialog()
        }
    }

    private fun startService() {
        Log.d(TAG, "startService --------------------")
        isRunService = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(VideoViewService.newService(this))
        } else {
            startService(VideoViewService.newService(this))
        }
    }
    
    //----------------------------------------------------------------------------------------
    // RTMP Service Methods
    private fun getInstanceForRtmpService() {
        RtmpService.init(this)
    }

    private fun receiveBroadcastEvent(intent: Intent?) {
        val sendEvent = intent?.getStringExtra(EXTRA_SEND_BROADCAST)
        Log.e(TAG, "receiveBroadcastEvent: $sendEvent, ${intent?.action} >>>>>>>>>>>>>>>>>")

        when(sendEvent) {
            EB_RTMP_SERVICE_START_STREAM -> {
                Log.e(TAG, EB_RTMP_SERVICE_START_STREAM)
            }
            EB_RTMP_SERVICE_STOP_STREAM -> {
                //stopNotification()
                Log.e(TAG, EB_RTMP_SERVICE_STOP_STREAM)
            }
            EB_RTMP_SERVICE_STOP_SERVICE -> {
                Log.e(TAG, EB_RTMP_SERVICE_STOP_SERVICE)

                stopNotification()
                unregisterReceiver()
            }
        }
    }

    private fun initRegisterReceiver() {
        RtmpServiceBroadcastReceiver.register(this, ::receiveBroadcastEvent)
    }

    private fun unregisterReceiver() {
        isRunService = false
        RtmpServiceBroadcastReceiver.unregister(this)
    }

    /**
     * This notification is to solve MediaProjection problem that only render surface if something
     * changed.
     * It could produce problem in some server like in Youtube that need send video and audio all time
     * to work.
     */
    private fun initNotification() {
        val notificationBuilder: Notification.Builder =
            Notification.Builder(this).setSmallIcon(R.drawable.notification_anim)
                .setContentTitle("Streaming")
                .setContentText("Display mode stream")
                .setTicker("Stream in progress")
        notificationBuilder.setAutoCancel(true)

        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.build()
        )
    }

    private fun stopNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    //----------------------------------------------------------------------------------------
    // Permission: 요청순서 Overlay -> RecordAudio -> MediaProjection 순서대로 요청
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult ----------------")

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            when(resultCode) {
                Activity.RESULT_OK -> {
                    Log.e(TAG, "REQUEST_MEDIA_PROJECTION - Permission Granted")

                    mediaProjectionPermissionGranted(resultCode, data)
                }
                else -> {
                    Log.e(TAG, "REQUEST_MEDIA_PROJECTION - Permission Denied")
                    unregisterReceiver()

                    val parentLayout: View = findViewById(android.R.id.content)
                    Snackbar.make(parentLayout, R.string.user_cancelled, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mediaProjectionPermissionGranted(resultCode: Int, data: Intent?) {
        this.resultCode = resultCode
        this.resultData = data

        isRunService = true

        initNotification()
        RtmpService.setData(resultCode, data!!)

        val intent = Intent(this, RtmpService::class.java)
        intent.putExtra("endpoint", "rtmp://192.168.0.152:1935/live/arenacast_streaming")
        //intent.putExtra("endpoint", "rtmp://media.wellfare.tv:1935/seniortv/arenacast_streaming")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 서비스를 실행하면 onStartCommand 가 호출되면서 시작
            startForegroundService(intent)
        }
        else {
            startService(intent)
        }
    }

    private fun getPermissionForMediaProjection() {
        if (RtmpService.isStreaming()) return

        startActivityForResult(RtmpService.sendIntent(), REQUEST_MEDIA_PROJECTION)

        //Java.Lang.IllegalArgumentException: Can only use lower 16 bits for requestCode 문제로 일단 주석처리
        /*val result = registerForActivityResult(MediaProjectionResultContract()) {
            val resultCode = it.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)

            if (resultCode == Activity.RESULT_OK) {
                val resultData = it.getParcelableExtra(EXTRA_REQUEST_DATA) as Intent
                mediaProjectionPermissionGranted(resultCode, resultData)
            }
            else {
                val parentLayout: View = findViewById(android.R.id.content)
                Snackbar.make(parentLayout, R.string.user_cancelled, Snackbar.LENGTH_SHORT).show()
            }
        }
        result.launch(RtmpService.sendIntent())
        //val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        //result.launch(mediaProjectionManager.createScreenCaptureIntent())
        */
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty()) {
            when(requestCode) {
                RC_RECORD_AUDIO -> {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        getPermissionForMediaProjection()
                    }
                    else {
                        val parentLayout: View = findViewById(android.R.id.content)
                        Snackbar.make(parentLayout,
                            "Permission request was denied by user!!", Snackbar.LENGTH_SHORT).show()
                    }
                }
                RC_MULTIPLE_PERM -> {// RECORD_AUDIO & CAMERA
                    val perms = HashMap<String, Int>()
                    var isAudioPerm = false
                    var isCameraPerm = false

                    for (i in 0 until grantResults.size) {
                        perms[permissions[i]] = grantResults[i]
                    }

                    if (perms[Manifest.permission.RECORD_AUDIO] == PackageManager.PERMISSION_GRANTED) isAudioPerm = true
                    if (perms[Manifest.permission.CAMERA] == PackageManager.PERMISSION_GRANTED) isCameraPerm = true

                    if (!isAudioPerm) {
                        val parentLayout: View = findViewById(android.R.id.content)
                        Snackbar.make(parentLayout,
                            "AudioPermission request was denied by user!!", Snackbar.LENGTH_SHORT).show()
                    }
                    else {
                        //일단 카메라는 필수가 아닌 옵션으로 처리
                        if (!isCameraPerm) {
                            val dialog = AlertDialog.Builder(this).apply {
                                setTitle("Noticw")
                                setMessage("CameraPermission request was denied by user!!\nDisable camera your streaming")
                                setCancelable(false)
                            }
                            dialog.setPositiveButton("Ok") { dialog, which ->
                                getPermissionForMediaProjection()
                            }
                        }
                        else getPermissionForMediaProjection()
                    }
                }
            }
        }
        else {
            val parentLayout: View = findViewById(android.R.id.content)
            Snackbar.make(parentLayout,
                "Permission request was denied by user!!", Snackbar.LENGTH_SHORT).show()
        }

        /*if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == RC_RECORD_AUDIO) {
                getPermissionForMediaProjection()
            }
        }
        else {
            val parentLayout: View = findViewById(android.R.id.content)
            Snackbar.make(parentLayout,
                "Permission request was denied by user!!", Snackbar.LENGTH_SHORT).show()
        }*/
    }

    private fun getPermissionForRequireAll() {
        val hasRecordAudioPermission: Int =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val hasCameraPermission: Int =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

        // Audio는 필수 Camera는 옵션
        if (hasRecordAudioPermission == PackageManager.PERMISSION_GRANTED &&
            hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
            getPermissionForMediaProjection()
        }
        else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA),
                RC_MULTIPLE_PERM)
        }
    }

    private fun getPermissionForRecordAudio() {
        val hasRecordAudioPermission: Int =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        // Audio는 필수 Camera는 옵션
        if (hasRecordAudioPermission == PackageManager.PERMISSION_GRANTED) {
            getPermissionForMediaProjection()
        }
        else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), RC_RECORD_AUDIO)
        }
    }

    private fun getPermissionForCamera() {
        val hasCameraPermission: Int =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

        // Audio는 필수 Camera는 옵션
        if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
            getPermissionForMediaProjection()
        }
        else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), RC_CAMERA)
        }
    }

    private fun getPermissionForRtmpService() {
        getPermissionForRequireAll()
        //getPermissionForMediaProjection()
    }

    //----------------------------------------------------------------------------------------
    // companion
    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, MainActivity::class.java)
        }
    }
}

internal class OverlayActivityResultContract : ActivityResultContract<String, Boolean>() {
    override fun createIntent(context: Context, input: String?): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(input))

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}

internal class MediaProjectionResultContract : ActivityResultContract<Intent, Intent>() {
    override fun createIntent(context: Context, input: Intent?): Intent = input!!

    override fun parseResult(resultCode: Int, intent: Intent?): Intent =
        if (resultCode == Activity.RESULT_OK && intent != null) {
            Intent(INTENT_FILTER_MEDIA_PROJECTION).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_REQUEST_DATA, intent)
                //putExtra(KEY_ACTION, ACTION_PERMISSION_INIT)
            }
        }
        else {
            Intent(INTENT_FILTER_MEDIA_PROJECTION).apply {
                putExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            }
        }

}
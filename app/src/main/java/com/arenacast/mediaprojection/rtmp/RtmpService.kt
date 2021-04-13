package com.arenacast.mediaprojection.rtmp

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.Camera
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import androidx.core.app.NotificationCompat
import com.arenacast.mediaprojection.*
import com.arenacast.mediaprojection.databinding.WindowVideoViewBinding
import com.arenacast.mediaprojection.model.MessageRoute
import com.arenacast.mediaprojection.screencapture.MediaProjectionActivity
import com.arenacast.mediaprojection.service.WindowTouchEvent
import com.arenacast.mediaprojection.surface.SurfaceViewHolder
import com.pedro.rtplibrary.base.DisplayBase
import com.pedro.rtplibrary.rtmp.RtmpDisplay
import com.pedro.rtplibrary.rtsp.RtspDisplay
import com.pedro.rtplibrary.util.SensorRotationManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class RtmpService : Service() {

    private lateinit var windowBinding: WindowVideoViewBinding
    private lateinit var windowViewLayoutParams: WindowManager.LayoutParams

    private val windowManager: WindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private val surfaceViewHolder: SurfaceViewHolder by lazy {
        SurfaceViewHolder()
    }

    private var isPlayState = 0
    private var endpoint: String? = null
    private var orientationEventListener: OrientationEventListener? = null
    private var orientationValue = 0
    private var sensorRotationManager: SensorRotationManager? = null

    // for camera
    private var camera2Preview: Camera2Preview? = null
    private var cameraPreview: CameraPreview? = null

    private val orientationArr = arrayOf(0,90,180,270)

    //----------------------------------------------------------------------------------------
    // Life Cycle
    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "RTP Display service create")
        startForegroundService()

        initWindowLayout(getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        //::연산자로 lateinit변수가 초기화가 되었는지 체크 가능.
        if (::windowBinding.isInitialized) {
            windowBinding.initCreate()

            cameraPreview = CameraPreview(this, windowBinding.surfaceView).apply {
                setupCameraOpen(
                    windowManager.defaultDisplay.rotation,
                    resources.configuration.orientation
                )
            }
            /*camera2Preview = Camera2Preview(this, windowBinding.textureView).apply {
                setupCameraPreview(resources.configuration.orientation)
            }*/
        }

        // stream orientation
        // cannot create an instance of an abstract class 에러인 경우 object :을 붙여준다.
        /*orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                println(orientation)
                Log.e(TAG, "onOrientationChanged - $orientation >>>>>>>>>>>>>>>>>>>>>>>>")

                val rotation: Int = (orientation + 45) / 90 % 4
                Log.e(TAG, "rotation: $rotation >>>>>>>>>>>>>>>>>>>>>>>>")
                val rotationDegrees = rotation * 90
                Log.e(TAG, "rotationDegrees: $rotationDegrees >>>>>>>>>>>>>>>>>>>>>>>>")
            }
        }
        if (orientationEventListener!!.canDetectOrientation())
            orientationEventListener!!.enable()*/

        sensorRotationManager = SensorRotationManager(this,
            SensorRotationManager.RotationChangedListener { rotation ->
                Log.e(TAG, "RotationChangedListener - $rotation >>>>>>>>>>>>>>>>>>>>>>>>")

                /*if (displayBase!!.isStreaming) {
                    if (orientationValue == 0) {
                        if (rotation == 90) {
                            this.orientationValue = 1
                            displayBase!!.glInterface.setStreamRotation(rotation)
                        }
                        else if (rotation == 270) {
                            this.orientationValue = 3
                            displayBase!!.glInterface.setStreamRotation(rotation)
                        }
                    }
                    else if (orientationValue == 1 || orientationValue == 3) {
                        if (rotation == 0) {
                            this.orientationValue = 0
                            displayBase!!.glInterface.setStreamRotation(rotation)
                        }
                    }
                }*/

                cameraPreview?.setupCameraOrientation(rotation, resources.configuration.orientation)
                //camera2Preview?.setupCamera2Orientation(rotation, resources.configuration.orientation)

        })
        sensorRotationManager?.start()

        //EventBus.getDefault().register(this)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "RTP Display service started")
        endpoint = intent?.extras?.getString("endpoint")
        /*if (endpoint != null) {
            prepareStreamRtp()
            startStreamRtp(endpoint!!)
        }*/
        return START_STICKY
    }

    //----------------------------------------------------------------------------------------
    // EventBus Receive Event
    // This method will be called when a MessageEvent is posted (in the UI thread for Toast)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(messageRoute: MessageRoute) {
        Log.d(TAG, "onMessageEvent: " + messageRoute)

        when(messageRoute.param1) {
            EB_RTMP_SERVICE_STOP_SERVICE -> {
                stopRtmpService()
            }
            /*EB_RTMP_SERVICE_PORTRAIT -> {
                setupCameraOrientation(orientationArr[windowManager.defaultDisplay.rotation])
            }
            EB_RTMP_SERVICE_LANDSCAPE -> {
                setupCameraOrientation(orientationArr[windowManager.defaultDisplay.rotation])
            }*/
        }
    }

    //----------------------------------------------------------------------------------------
    // Methods
    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Foreground Service Channel",//channelId,
                NotificationManager.IMPORTANCE_HIGH)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MediaProjectionActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0,
            notificationIntent, 0)

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
            .setContentTitle("Foreground Service")
            .setSmallIcon(R.drawable.ic_baseline_fiber_manual_record_24)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(FOREGROUND_SERVICE_ID, notification)

        /*if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val notification = NotificationCompat.Builder(this, channelId)
                .setOngoing(true)
                .setContentTitle("")
                .setContentText("").build()
            startForeground(FOREGROUND_SERVICE_ID, notification)
        } else {
            startForeground(FOREGROUND_SERVICE_ID, Notification())
        }*/
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun WindowVideoViewBinding.initCreate() {
        //surfaceView.holder.addCallback(surfaceViewHolder)
        root.setOnTouchListener(WindowTouchEvent(updateViewLayout = ::updateViewPosition))

        imageView.drawable.alpha = 50

        // StopService
        btnStopService.setOnClickListener {

            /*val dialog = AlertDialog.Builder(this@RtmpService).apply {
                setTitle("Notice")
                setMessage("Are you sure want to stop service?")
                setCancelable(false)
            }
            dialog.setPositiveButton("Ok") { dialog, which ->
                //stopService()
            }
            dialog.setNegativeButton("Cancel") { dialog, which ->

            }

            val alert = dialog.create()
            alert.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG)
            alert.show()*/

            //displayBase?.disableAudio()

            //stopRtmpService()
            startActivity(AlertActivity.open(this@RtmpService))
        }

        // MediaProjection Start or Stop Toggle
        mainPlayPauseButton.run {
            //setColor(Color.DKGRAY)
            setOnClickListener {
                // Do nothing.
                when (isPlayState) {
                    0 -> {
                        //mainPlayPauseButton.setImageResource(android.R.drawable.ic_media_play)
                        mainPlayPauseButton.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                        isPlayState = 1

                        if (endpoint != null) {
                            prepareStreamRtp()
                            startStreamRtp(endpoint!!)

                            // send broadcast receiver
                            sendBroadcast(
                                RtmpServiceBroadcastReceiver.sendBroadcast(EB_RTMP_SERVICE_START_STREAM)
                            )
                        }
                    }
                    1 -> {
                        //mainPlayPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                        mainPlayPauseButton.setImageResource(R.drawable.ic_baseline_pause_24)
                        isPlayState = 0

                        notificationManager?.cancel(NOTIFICATION_ID)
                        stopStream()

                        // send broadcast receiver
                        sendBroadcast(
                            RtmpServiceBroadcastReceiver.sendBroadcast(EB_RTMP_SERVICE_STOP_STREAM)
                        )

                        //cameraPreview?.closeCamera()
                    }
                }
            }
        }
    }

    private fun updateViewPosition(x: Int, y: Int) {
        windowViewLayoutParams.x += x
        windowViewLayoutParams.y += y
        windowManager.updateViewLayout(windowBinding.root, windowViewLayoutParams)
    }

    /**
     * Window View 를 초기화 한다. X, Y 좌표는 0, 0으로 지정한다.
     * ?: ?. 엘비스 연산자
     * val name: String = myName ?: "Jerry" // myName이 널이면 Jerry를 할당
     * val name: String = myName ?: return  // 또는 강제 리턴을 하거나 하는 용도로 사용 가능
     */
    private fun initWindowLayout(layoutInflater: LayoutInflater) {
        windowBinding = WindowVideoViewBinding.inflate(layoutInflater,
            null,
            false).also {
            windowViewLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                30, 30,  // X, Y 좌표
                WindowManager.LayoutParams.TYPE_TOAST.takeIf {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                } ?: WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,//Oreo버전 이상이면 TYPE_APPLICATION_OVERLAY
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            windowViewLayoutParams.gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(windowBinding.root, windowViewLayoutParams)
    }

    //----------------------------------------------------------------------------------------
    // static methods
    companion object {
        private val TAG = "RTMP_Service >>>"
        private val channelId = "rtpDisplayStreamChannel"
        private val notifyId = NOTIFICATION_ID
        private var notificationManager: NotificationManager? = null
        private var displayBase: DisplayBase? = null
        private var contextApp: Context? = null
        private var resultCode: Int? = null
        private var data: Intent? = null

        fun init(context: Context) {
            contextApp = context
            if (displayBase == null) {
                Log.e(TAG, "RTMP Service displayBase is created!! >>>>>>>>>>>>>>>>>>>>>>>>")
                displayBase = RtmpDisplay(context, true, connectCheckerRtp)
            }
        }

        fun setData(resultCode: Int, data: Intent) {
            this.resultCode = resultCode
            this.data = data
        }

        fun sendIntent(): Intent? {
            if (displayBase != null) {
                return displayBase!!.sendIntent()
            } else {
                return null
            }
        }

        fun isStreaming(): Boolean {
            return if (displayBase != null) displayBase!!.isStreaming else false
        }

        fun isRecording(): Boolean {
            return if (displayBase != null) displayBase!!.isRecording else false
        }

        fun stopStream() {
            if (displayBase != null) {
                if (displayBase!!.isStreaming) displayBase!!.stopStream()
            }
        }

        private val connectCheckerRtp = object : ConnectCheckerRtp {
            override fun onConnectionSuccessRtp() {
                showNotification("Stream started")
                Log.e(TAG, "RTP service started")
            }

            override fun onNewBitrateRtp(bitrate: Long) {
            }

            override fun onConnectionFailedRtp(reason: String) {
                showNotification("Stream connection failed")
                Log.e(TAG, "RTP service destroy")
            }

            override fun onDisconnectRtp() {
                showNotification("Stream stopped")
            }

            override fun onAuthErrorRtp() {
                showNotification("Stream auth error")
            }

            override fun onAuthSuccessRtp() {
                showNotification("Stream auth success")
            }
        }

        private fun showNotification(text: String) {
            contextApp?.let {
                val notification = NotificationCompat.Builder(it, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("RTP Display Stream")
                    .setContentText(text).build()
                notificationManager?.notify(notifyId, notification)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "RTP Display service destroy")
        stopStream()

        if (::windowBinding.isInitialized) {
            windowManager.removeView(windowBinding.root)
        }

        notificationManager?.let {
            it.cancel(NOTIFICATION_ID)
            notificationManager = null
        }
        orientationEventListener?.let {
            it.disable()
            orientationEventListener = null
        }
        sensorRotationManager?.let {
            it.stop()
            sensorRotationManager = null
        }

        // for camera
        cameraPreview?.let {
            it.closeCamera()
            cameraPreview = null
        }

        // for camera2
        camera2Preview?.let {
            it.closeCamera()
            camera2Preview = null
        }

        EventBus.getDefault().unregister(this)
    }

    private fun stopRtmpService() {
        // unregister broadcast receiver
        sendBroadcast(
            RtmpServiceBroadcastReceiver.sendBroadcast(EB_RTMP_SERVICE_STOP_SERVICE)
        )
        // stop service
        stopSelf()
    }

    private fun prepareStreamRtp() {
        stopStream()
        if (endpoint!!.startsWith("rtmp")) {
            displayBase = RtmpDisplay(baseContext, true, connectCheckerRtp)
            displayBase?.setIntentResult(resultCode!!, data)
        } else {
            displayBase = RtspDisplay(baseContext, true, connectCheckerRtp)
            displayBase?.setIntentResult(resultCode!!, data)
        }

        //displayBase.glInterface.setStreamRotation()
    }

    /*
    720, 24fps : 2000Kbps = 2Mbps
    720, 30fps : 2500Kbps = 2.5Mbps
    720, 60fps : 3500Kbps = 3.5Mbps
   1080, 24fps : 3500Kbps = 3.5Mbps
   1080, 30fps : 4000Kbps = 4Mbps
   1080, 60fps : 5500Kbps = 5.5Mbps
     */
    private fun startStreamRtp(endpoint: String) {
        if (!displayBase!!.isStreaming) {
            val dpi = application.resources.displayMetrics.densityDpi
            orientationValue = windowManager.defaultDisplay.rotation
            Log.d(TAG, "orientation - $orientationValue --------------------")

            /*var widthTmp = DEFAULT_VALUE_SIZE_WIDTH
            var heightTmp = DEFAULT_VALUE_SIZE_HEIGHT

            // for landscape (해상도를 반대로 설정해줄 필요가 없고 orientation값에 의해 내부에서 자동으로 처리됨)
            if (orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270) {
                widthTmp = DEFAULT_VALUE_SIZE_HEIGHT
                heightTmp = DEFAULT_VALUE_SIZE_WIDTH
            }*/
            //val orientationArr = arrayOf(0,90,180,270)

            // 1280x720x30/1024/8 = 3375kbps
            // prepareVideo(640, 480, 30, 1200*1024, 0, 320) // bitrate 1228800, 2560000
            val isPrepareVideo = displayBase!!.prepareVideo(
                DEFAULT_VALUE_SIZE_WIDTH,
                DEFAULT_VALUE_SIZE_HEIGHT,
                30,
                2500*1024, // 2.5Mbps
                orientationArr[orientationValue],
                dpi
            )

            // android.permission.RECORD_AUDIO 권한이 granted 상태가 아니면 초기화 실패가 나고 스트리밍 실패가 된다.
            val isPrepareAudio = displayBase!!.prepareAudio(
                64 * 1024,
                32000,
                true,
                false,
                false
            )

            if (isPrepareVideo && isPrepareAudio) {
                displayBase!!.startStream(endpoint)
            }
        } else {
            showNotification("You are already streaming :(")
        }
    }
}
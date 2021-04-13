package com.arenacast.mediaprojection.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.arenacast.mediaprojection.R
import com.arenacast.mediaprojection.databinding.WindowVideoViewBinding
import com.arenacast.mediaprojection.model.MessageRoute
import com.arenacast.mediaprojection.*
import com.arenacast.mediaprojection.EB_MEDIA_PROJECTION_ON_FAIL
import com.arenacast.mediaprojection.EB_MEDIA_PROJECTION_ON_INIT
import com.arenacast.mediaprojection.EB_MEDIA_PROJECTION_ON_STARTED
import com.arenacast.mediaprojection.EB_MEDIA_PROJECTION_ON_STOP
import com.arenacast.mediaprojection.screencapture.MediaProjectionService
import com.arenacast.mediaprojection.surface.SurfaceViewHolder
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class VideoViewService : MediaProjectionService() {

    companion object {
        val TAG = "VideoViewService"

        fun newService(context: Context): Intent {
            return Intent(context, VideoViewService::class.java)
        }
    }

    private var isPlayState = 0

    private lateinit var windowBinding: WindowVideoViewBinding

    private val windowManager: WindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private lateinit var windowViewLayoutParams: WindowManager.LayoutParams

    private val surfaceViewHolder: SurfaceViewHolder by lazy {
        SurfaceViewHolder(null,null)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate ------------------")

        initWindowLayout(getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        windowBinding.initCreate()

        EventBus.getDefault().register(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    //----------------------------------------------------------------------------------------
    // EventBus Receive Event
    // This method will be called when a MessageEvent is posted (in the UI thread for Toast)
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(messageRoute: MessageRoute) {
        Log.d("VideoViewService", "onMessageEvent: " + messageRoute)

        when(messageRoute.param1) {
            EB_MEDIA_PROJECTION_PERMISSION_INIT -> {
                messageRoute.param3?.let {
                    permissionInitMediaProjection(it, true)
                }
            }
            else -> {

            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun WindowVideoViewBinding.initCreate() {
        surfaceView.holder.addCallback(surfaceViewHolder)
        root.setOnTouchListener(WindowTouchEvent(updateViewLayout = ::updateViewPosition))

        // StopService
        btnStopService.setOnClickListener {
            EventBus.getDefault().unregister(this)
            stopSelf()
        }

        // MediaProjection Start or Stop Toggle
        mainPlayPauseButton.run {
            //setColor(Color.DKGRAY)
            setOnClickListener {
                // Do nothing.
                when (isPlayState) {
                    0 -> {
                        createMediaProjection()
                        mainPlayPauseButton.setImageResource(android.R.drawable.ic_media_play)
                        isPlayState = 1
                    }
                    1 -> {
                        stopMediaProjection()
                        mainPlayPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                        isPlayState = 0
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
        windowBinding = WindowVideoViewBinding.inflate(layoutInflater, null, false).also {
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

    /**
     * Play button design change
     */
    private fun setPlayed(isPlayed: Boolean) {
        /*windowBinding.mainPlayPauseButton.run {
            if (this.isPlayed != isPlayed) {
                this.isPlayed = isPlayed
                startAnimation()
            }
        }*/
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::windowBinding.isInitialized) {
            windowManager.removeView(windowBinding.root)
        }
    }

    override fun onChangeStatus(statusData: String) {
        super.onChangeStatus(statusData)

        Log.d("VideoViewService",
            "surfaceW: ${windowBinding.surfaceView.width}, surfaceH: ${windowBinding.surfaceView.height}")
        when (statusData) {
            EB_MEDIA_PROJECTION_ON_INIT -> {
                startMediaProjection(windowBinding.surfaceView.holder.surface,
                    EXTRA_PROJECTION_NAME,
                    windowBinding.surfaceView.width,
                    windowBinding.surfaceView.height)
            }
            EB_MEDIA_PROJECTION_ON_STARTED -> {
                setPlayed(true)
                Toast.makeText(this, R.string.media_projection_started, Toast.LENGTH_SHORT).show()
            }
            EB_MEDIA_PROJECTION_ON_STOP -> {
                setPlayed(false)
                Toast.makeText(this, R.string.media_projection_stopped, Toast.LENGTH_SHORT).show()
            }
            EB_MEDIA_PROJECTION_ON_FAIL -> {
                setPlayed(false)
                Toast.makeText(this, R.string.media_projection_fail, Toast.LENGTH_SHORT).show()
            }
            EB_MEDIA_PROJECTION_ON_REJECT -> {
                setPlayed(false)
                Toast.makeText(this, R.string.media_projection_reject, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
package com.arenacast.mediaprojection.rtmp

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.hardware.Camera
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import com.arenacast.mediaprojection.surface.SurfaceViewHolder
import com.google.android.material.snackbar.Snackbar

class CameraPreview(context: Context, surfaceView: SurfaceView) : Thread() {
    val TAG = "CameraPreview"

    private val context: Context
    private val surfaceView: SurfaceView

    private lateinit var camera: Camera
    private lateinit var cameraHolder: SurfaceHolder

    private var sensorOrientation = 0
    private var configurationValue = 0
    private var landscapeRotate = 0

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private val surfaceViewHolder: SurfaceViewHolder by lazy {
        SurfaceViewHolder(camera, cameraHolder)
    }

    private val ORIENTATIONS = SparseIntArray()
    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    //----------------------------------------------------------------------------------------
    init {
        this.context = context
        this.surfaceView = surfaceView
    }

    //----------------------------------------------------------------------------------------
    fun setupCameraOpen(rotation: Int, configuration: Int) {
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
        camera.setDisplayOrientation(ORIENTATIONS[rotation])

        configurationValue = configuration

        cameraHolder = surfaceView.holder
        cameraHolder.addCallback(surfaceViewHolder)
        cameraHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        //val parentLayout: View = (context as Activity).findViewById(android.R.id.content)
        //Snackbar.make(parentLayout, "Camera permission denied", Snackbar.LENGTH_SHORT).show()
    }

    fun setOrientation(rotation: Int) {
        camera.setDisplayOrientation(ORIENTATIONS[rotation])
    }

    fun closeCamera() {
        camera.stopPreview()
    }

    fun setupCameraOrientation(rotation: Int, configuration: Int) {
        /*when (rotation) {
            0 -> camera?.setDisplayOrientation(90)
            90 -> camera?.setDisplayOrientation(180)
            270 -> camera?.setDisplayOrientation(0)
        }*/

        when(configuration) {
            Configuration.ORIENTATION_PORTRAIT -> {// 0 or 180
                if (configuration != configurationValue) {
                    Log.e(TAG, "Configuration Change portrait - reset camera")
                    configurationValue = configuration
                    setOrientation(Surface.ROTATION_0)//90
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {// 90 or 270
                configurationValue = configuration
                if (rotation == 90 || rotation == 270) {
                    if (rotation != landscapeRotate) {
                        landscapeRotate = rotation
                        Log.e(TAG, "Configuration Change landscape $rotation - reset camera")
                        when (rotation) {
                            90 -> setOrientation(Surface.ROTATION_270)//180
                            270 -> setOrientation(Surface.ROTATION_90)//0
                        }
                    }
                }
            }
        }
    }
}
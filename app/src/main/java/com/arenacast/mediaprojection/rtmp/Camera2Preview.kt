package com.arenacast.mediaprojection.rtmp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.arenacast.mediaprojection.R
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Semaphore

class Camera2Preview(context: Context, textureView: AutoFitTextureView) : Thread() {
    val TAG = "Camera2Preview"

    private val context: Context
    private val textureView: AutoFitTextureView

    private var thread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var previewSize: Size? = null
    private var cameraDevice: CameraDevice? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var previewSession: CameraCaptureSession? = null
    private val cameraOpenCloseLock: Semaphore = Semaphore(1)

    private var sensorOrientation = 0
    private var configurationValue = 0
    private var landscapeRotate = 0

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        this.textureView = textureView
    }

    //----------------------------------------------------------------------------------------
    private fun getBackFacingCameraId(cManager: CameraManager): String? {
        try {
            for (cameraId in cManager.cameraIdList) {
                val characteristics = cManager.getCameraCharacteristics(cameraId)
                val cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING)!!
                if (cOrientation == CameraCharacteristics.LENS_FACING_FRONT) return cameraId
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return null
    }

    private fun setSurfaceTextureListener() {
        textureView.surfaceTextureListener = surfaceTextureListener
    }

    private val surfaceTextureListener: TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.e(TAG, "onSurfaceTextureAvailable, width=$width,height=$height")
            openCamera()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.e(TAG, "onSurfaceTextureSizeChanged")
            configureTransform(width, height)
        }
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        }
    }

    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.e(TAG, "onOpened")
            cameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.e(TAG, "onDisconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "onError")
        }
    }

    protected fun startPreview() {
        if (null == cameraDevice || !textureView.isAvailable || null == previewSize) {
            Log.e(TAG, "startPreview fail, return")
        }

        val texture = textureView.surfaceTexture
        if (null == texture) {
            Log.e(TAG, "texture is null, return")
            return
        }

        texture.setDefaultBufferSize(previewSize!!.getWidth(), previewSize!!.getHeight())

        val surface = Surface(texture)
        try {
            previewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        previewBuilder!!.addTarget(surface)

        try {
            cameraDevice!!.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        previewSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(context, "onConfigureFailed", Toast.LENGTH_LONG).show()
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    protected fun updatePreview() {
        if (cameraDevice == null) {
            Log.e(TAG, "updatePreview error, return")
        }
        previewBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        /*val rotation = windowManager.defaultDisplay.rotation
        Log.e(TAG, "rotation - $rotation")
        val orientation = (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360
        Log.e(TAG, "orientation - $orientation")
        previewBuilder!!.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))*/

        thread = HandlerThread("CameraPreview").also { it.start() }
        backgroundHandler = Handler(thread!!.looper)

        try {
            previewSession!!.setRepeatingRequest(
                previewBuilder!!.build(),
                null,
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    //----------------------------------------------------------------------------------------
    private fun openCamera() {
        Log.e(TAG, "openCamera E")
        try {
            val permissionCamera =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            if (permissionCamera == PackageManager.PERMISSION_DENIED) {
                /*ActivityCompat.requestPermissions(
                    (context as Activity),
                    arrayOf(Manifest.permission.CAMERA),
                    MainActivity.REQUEST_CAMERA
                )*/
                //val parentLayout: View = (context as Activity).findViewById(android.R.id.content)
                //Snackbar.make(parentLayout, "Camera permission denied", Snackbar.LENGTH_SHORT).show()
            } else {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = getBackFacingCameraId(manager)
                val characteristics = manager.getCameraCharacteristics(cameraId!!)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                previewSize = map!!.getOutputSizes(SurfaceTexture::class.java)[0]
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                Log.e(TAG, "sensorOrientation - $sensorOrientation")

                configureTransform(textureView.width, textureView.height)
                manager.openCamera(cameraId, stateCallback, null)
            }
        } catch (e: CameraAccessException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        Log.e(TAG, "openCamera X")
    }

    private fun stopThread() {
        Log.e(TAG, "stopThread")
        thread?.quitSafely()
        try {
            thread?.join()
            thread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
            Log.e(TAG, e.toString())
        }
    }

    fun closeCamera() {
        Log.e(TAG, "closeCamera")
        stopThread()

        try {
            cameraOpenCloseLock.acquire()
            previewSession?.let {
                it.close()
                previewSession = null
            }
            cameraDevice?.let {
                it.close()
                cameraDevice = null
            }

        } catch (e: InterruptedException) {
            throw java.lang.RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    fun setupCameraPreview(configuration: Int) {
        configurationValue = configuration

        if (textureView.isAvailable) {
            Log.e(TAG, "textureView.isAvailable")
            openCamera()
        }
        else {
            Log.e(TAG, "setup surfaceTextureListener")
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    fun setupCamera2Orientation(rotation: Int, configuration: Int) {
        when(configuration) {
            Configuration.ORIENTATION_PORTRAIT -> {// 0 or 180
                if (configuration != configurationValue) {
                    Log.e(TAG, "Configuration Change portrait - reset camera2")
                    configurationValue = configuration
                    closeCamera()
                    openCamera()
                }
            }
            Configuration.ORIENTATION_LANDSCAPE -> {// 90 or 270
                configurationValue = configuration
                if (rotation == 90 || rotation == 270) {
                    if (rotation != landscapeRotate) {
                        landscapeRotate = rotation
                        Log.e(TAG, "Configuration Change landscape $rotation - reset camera2")
                        closeCamera()
                        openCamera()
                    }
                }
            }
        }
    }

    fun onResume() {
        Log.d(TAG, "onResume")
        setSurfaceTextureListener()
    }

    fun onPause() {
        Log.d(TAG, "onPause")
        closeCamera()
    }

    /**
     * Determines if the dimensions are swapped given the phone's current rotation.
     *
     * @param displayRotation The current rotation of the display
     *
     * @return true if the dimensions are swapped, false otherwise.
     */
    private fun areDimensionsSwapped(displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {

        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                viewHeight.toFloat() / previewSize!!.height,
                viewWidth.toFloat() / previewSize!!.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

}
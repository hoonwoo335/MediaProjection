package com.arenacast.mediaprojection.surface

import android.hardware.Camera
import android.util.Log
import android.view.SurfaceHolder
import java.lang.Exception

class SurfaceViewHolder : SurfaceHolder.Callback2 {

    private var camera: Camera? = null
    private var cameraHolder: SurfaceHolder? = null

    constructor(camera: Camera?, cameraHolder: SurfaceHolder?) {
        this.camera = camera
        this.cameraHolder = cameraHolder
    }

    constructor() {
        this.camera = null
        this.cameraHolder = null
    }

    override fun surfaceRedrawNeeded(holder: SurfaceHolder?) {

    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        Log.e("SurfaceViewHolder", "surfaceChanged - fm:$format, w:$width, h:$height >>>>>>>>")

        cameraHolder?.let {
            if (it.surface == null) {
                return@let
            }

            try {
                camera!!.stopPreview()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val parameters: Camera.Parameters = camera!!.parameters

            val focusModes = parameters.supportedFocusModes
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            }
            camera!!.parameters = parameters

            try {
                camera!!.setPreviewDisplay(it)
                camera!!.startPreview()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        camera?.stopPreview()
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Log.e("SurfaceViewHolder", "surfaceCreated >>>>>>>>")
        camera?.let {
            it.setPreviewDisplay(holder)
            it.startPreview()
        }
    }
}
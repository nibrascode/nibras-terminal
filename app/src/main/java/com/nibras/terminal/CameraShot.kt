package com.nibras.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Silent photo like termux-camera-photo: Camera2 + ImageReader, a short preview run so exposure settles. */
object CameraShot {
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun take(context: Context, cameraId: String, out: File) {
        val cm = context.getSystemService(CameraManager::class.java)
        if (cameraId !in cm.cameraIdList) throw IllegalArgumentException("no camera $cameraId (see nibras-camera-info)")
        val ch = cm.getCameraCharacteristics(cameraId)
        val size = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.width.toLong() * it.height }!!
        val ht = HandlerThread("nibras-camera").apply { start() }
        val h = Handler(ht.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        val done = CountDownLatch(1)
        var error: String? = null
        var device: CameraDevice? = null
        reader.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { img ->
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                runCatching { out.writeBytes(bytes) }.onFailure { error = it.message }
            }
            done.countDown()
        }, h)
        try {
            cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam
                    val dummy = android.graphics.SurfaceTexture(0).apply { setDefaultBufferSize(640, 480) }
                    val preview = Surface(dummy)
                    cam.createCaptureSession(listOf(preview, reader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            val warm = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(preview)
                                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            }.build()
                            s.setRepeatingRequest(warm, null, h)
                            h.postDelayed({
                                runCatching {
                                    val still = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(reader.surface)
                                        set(CaptureRequest.JPEG_ORIENTATION, ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                    }.build()
                                    s.capture(still, object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(x: CameraCaptureSession, r: CaptureRequest, t: TotalCaptureResult) = Unit
                                    }, h)
                                }.onFailure { error = it.message; done.countDown() }
                            }, 1200)
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) { error = "camera session failed"; done.countDown() }
                    }, h)
                }
                override fun onDisconnected(cam: CameraDevice) { error = "camera disconnected"; done.countDown() }
                override fun onError(cam: CameraDevice, e: Int) { error = "camera error $e"; done.countDown() }
            }, h)
            if (!done.await(15, TimeUnit.SECONDS)) error = "camera timeout"
        } finally {
            runCatching { device?.close() }
            reader.close()
            ht.quitSafely()
        }
        error?.let { throw IllegalStateException(it) }
        if (!out.isFile || out.length() == 0L) throw IllegalStateException("no image")
    }
}

package com.parvez.camerapro

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var preview: TextureView
    private lateinit var statusText: TextView
    private lateinit var modeButton: Button
    private lateinit var captureButton: Button
    private lateinit var recordButton: Button
    private lateinit var cameraButton: Button
    private lateinit var intensity: SeekBar

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var recorder: MediaRecorder? = null
    private var recording = false
    private var currentCameraId: String? = null
    private var frontCamera = false
    private var vivid = false
    private var filterStrength = 65
    private var videoUri: Uri? = null
    private var previewSize = Size(1280, 720)
    private var backgroundThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var pendingPermissionAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        buildInterface()
        preview.surfaceTextureListener = textureListener
        if (hasPermissions()) {
            if (preview.isAvailable) openCamera() 
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun buildInterface() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = TextureView(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 16, 18, 12)
            backgroundColor(0x66000000)
        }
        statusText = TextView(this).apply {
            text = "CAMERA PRO  •  STAGE 1"
            textSize = 13f
            setTextColor(Color.WHITE)
        }
        top.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        cameraButton = makeButton("↻ Camera") { switchCamera() }
        top.addView(cameraButton)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 14, 18, 24)
            backgroundColor(0xCC101114.toInt())
        }

        val modes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        modeButton = makeButton("Natural") { 
            vivid = !vivid
            modeButton.text = if (vivid) "Vivid ✓" else "Natural ✓"
            statusText.text = if (vivid) "VIVID PHOTO • ${filterStrength}%" else "NATURAL PHOTO"
        }
        modes.addView(modeButton)
        bottom.addView(modes)

        val label = TextView(this).apply {
            text = "Vivid intensity (applies to saved photos)"
            setTextColor(Color.LTGRAY)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        bottom.addView(label)
        intensity = SeekBar(this).apply {
            max = 100
            progress = filterStrength
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    filterStrength = progress
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (vivid) statusText.text = "VIVID PHOTO • ${filterStrength}%"
                }
            })
        }
        bottom.addView(intensity, LinearLayout.LayoutParams(-1, -2))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        captureButton = makeButton("● Photo") { takePhoto() }
        recordButton = makeButton("▶ Video") { toggleRecording() }
        controls.addView(captureButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(recordButton, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(controls)
        val note = TextView(this).apply {
            text = "Camera2 • Saves to Gallery • Stage 1 prototype"
            textSize = 10f
            setTextColor(0xFFB6BBC4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        bottom.addView(note)
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    private fun LinearLayout.backgroundColor(color: Int) {
        background = android.graphics.drawable.ColorDrawable(color)
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0xFF242830.toInt())
                cornerRadius = 28f
                setStroke(1, 0xFF535B68.toInt())
            }
            setPadding(12, 6, 12, 6)
            setOnClickListener { action() }
        }
    }

    private fun hasPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (hasPermissions()) openCamera()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun startThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraProBackground").also { it.start() }
            cameraHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun openCamera() {
        if (!preview.isAvailable || !hasPermissions() || recording) return
        startThread()
        try {
            val ids = cameraManager.cameraIdList
            val wanted = ids.firstOrNull { id ->
                val facing = cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING)
                facing == if (frontCamera) CameraCharacteristics.LENS_FACING_FRONT
                else CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull() ?: return
            currentCameraId = wanted
            val chars = cameraManager.getCameraCharacteristics(wanted)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpeg = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            val selected = jpeg?.maxByOrNull { it.width.toLong() * it.height } ?: Size(1280, 720)
            imageReader?.close()
            imageReader = ImageReader.newInstance(selected.width, selected.height, android.graphics.ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
                savePhoto(image)
            }, cameraHandler)
            cameraManager.openCamera(wanted, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    runOnUiThread { toast("Camera error: $error") }
                }
            }, cameraHandler)
        } catch (e: Exception) {
            toast("Could not open camera: ${e.message}")
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val texture = preview.surfaceTexture ?: return
        texture.setDefaultBufferSize(preview.width.coerceAtLeast(1), preview.height.coerceAtLeast(1))
        val previewSurface = Surface(texture)
        val readerSurface = imageReader?.surface ?: return
        try {
            device.createCaptureSession(listOf(previewSurface, readerSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraSession: CameraCaptureSession) {
                    session = cameraSession
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(previewSurface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }.build()
                        cameraSession.setRepeatingRequest(request, null, cameraHandler)
                    } catch (e: Exception) { toast("Preview error: ${e.message}") }
                }
                override fun onConfigureFailed(cameraSession: CameraCaptureSession) {
                    toast("Camera preview setup failed")
                }
            }, cameraHandler)
        } catch (e: Exception) { toast("Session error: ${e.message}") }
    }

    private fun takePhoto() {
        val device = cameraDevice ?: return toast("Camera is not ready")
        val activeSession = session ?: return toast("Camera is starting")
        val target = imageReader?.surface ?: return
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(target)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }.build()
            activeSession.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: android.hardware.camera2.TotalCaptureResult
                ) { runOnUiThread { toast("Photo captured") } }
            }, cameraHandler)
        } catch (e: Exception) { toast("Photo failed: ${e.message}") }
    }

    private fun savePhoto(image: Image) {
        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            image.close()
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val finalBitmap = if (vivid && filterStrength > 0) applyVivid(original, filterStrength) else original
            val name = "CameraPro_${stamp()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/CameraPro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            if (finalBitmap !== original) finalBitmap.recycle()
            original.recycle()
            runOnUiThread { toast(if (uri != null) "Saved to Gallery / CameraPro" else "Could not save photo") }
        } catch (e: Exception) {
            try { image.close() } catch (_: Exception) {}
            runOnUiThread { toast("Save photo failed: ${e.message}") }
        }
    }

    private fun applyVivid(source: Bitmap, strength: Int): Bitmap {
        val factor = 1f + (strength / 100f) * 0.45f
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val saturation = android.graphics.ColorMatrix().apply { setSaturation(factor) }
            colorFilter = android.graphics.ColorMatrixColorFilter(saturation)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun switchCamera() {
        if (recording) return toast("Stop recording before switching cameras")
        frontCamera = !frontCamera
        closeCamera()
        openCamera()
    }

    private fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (cameraDevice == null || !hasPermissions()) return toast("Camera or microphone permission missing")
        try {
            session?.close()
            session = null
            val movieName = "CameraPro_${stamp()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, movieName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CameraPro")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            val uri = videoUri ?: throw IllegalStateException("Cannot create video file")
            val temp = File(cacheDir, movieName)
            recorder = newMediaRecorder()
            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(temp.absolutePath)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoSize(1280, 720)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(6_000_000)
                prepare()
            }
            val recorderSurface = recorder!!.surface
            val texture = preview.surfaceTexture ?: throw IllegalStateException("Preview unavailable")
            texture.setDefaultBufferSize(1280, 720)
            val previewSurface = Surface(texture)
            cameraDevice!!.createCaptureSession(listOf(previewSurface, recorderSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(newSession: CameraCaptureSession) {
                    session = newSession
                    try {
                        val request = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(previewSurface)
                            addTarget(recorderSurface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        }.build()
                        newSession.setRepeatingRequest(request, null, cameraHandler)
                        recorder!!.start()
                        recording = true
                        runOnUiThread {
                            recordButton.text = "■ Stop"
                            statusText.text = "RECORDING • 720p"
                            captureButton.isEnabled = false
                        }
                    } catch (e: Exception) {
                        runOnUiThread { toast("Recording could not start: ${e.message}") }
                        cleanRecorder()
                        createPreviewSession()
                    }
                }
                override fun onConfigureFailed(newSession: CameraCaptureSession) {
                    cleanRecorder()
                    runOnUiThread { toast("Video session setup failed") }
                    createPreviewSession()
                }
            }, cameraHandler)
            // The file is copied to MediaStore after recording stops.
            pendingTempVideo = temp
        } catch (e: Exception) {
            cleanRecorder()
            createPreviewSession()
            toast("Video setup failed: ${e.message}")
        }
    }

    private var pendingTempVideo: File? = null


    private fun newMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else legacyMediaRecorder()
    }

    @Suppress("DEPRECATION")
    private fun legacyMediaRecorder(): MediaRecorder = MediaRecorder()

    private fun stopRecording() {
        try {
            recorder?.stop()
            recorder?.reset()
            recorder?.release()
            recorder = null
            recording = false
            val temp = pendingTempVideo
            val uri = videoUri
            if (temp != null && uri != null && temp.exists()) {
                contentResolver.openOutputStream(uri)?.use { output -> temp.inputStream().use { it.copyTo(output) } }
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(uri, values, null, null)
                temp.delete()
            }
            runOnUiThread {
                recordButton.text = "▶ Video"
                statusText.text = "CAMERA PRO • STAGE 1"
                captureButton.isEnabled = true
                toast("Video saved to Gallery / CameraPro")
            }
        } catch (e: Exception) {
            toast("Could not finish video: ${e.message}")
            videoUri?.let { contentResolver.delete(it, null, null) }
        } finally {
            recording = false
            session?.close()
            session = null
            createPreviewSession()
        }
    }

    private fun cleanRecorder() {
        try { recorder?.reset() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        recording = false
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun toast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun closeCamera() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (hasPermissions()) {
                if (preview.isAvailable) openCamera()
            } else toast("Camera and microphone permissions are needed for Stage 1")
        }
    }

    override fun onResume() {
        super.onResume()
        startThread()
        if (::preview.isInitialized && preview.isAvailable && hasPermissions() && cameraDevice == null) openCamera()
    }

    override fun onPause() {
        if (recording) {
            try { stopRecording() } catch (_: Exception) {}
        }
        closeCamera()
        super.onPause()
    }

    override fun onDestroy() {
        closeCamera()
        backgroundThread?.quitSafely()
        try { backgroundThread?.join() } catch (_: InterruptedException) {}
        backgroundThread = null
        cameraHandler = null
        super.onDestroy()
    }
}

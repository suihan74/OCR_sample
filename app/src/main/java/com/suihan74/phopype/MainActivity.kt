package com.suihan74.phopype

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {

    // coroutine

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = job

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    // coroutine over

    companion object {
        private const val MINIMUM_VALID_HEIGHT: Int = 200
    }

    // camera

    private var cameraDevice: CameraDevice? = null
    private val cameraManager: CameraManager by lazy {
        getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var captureSession: CameraCaptureSession? = null

    /** 撮影・固定されたビットマップ */
    private var capturedBitmap: Bitmap? = null

    // camera over


    // views

    private val rootLayout: ViewGroup by lazy { findViewById<ViewGroup>(R.id.root_layout) }

    private val textureView: TextureView by lazy { findViewById<TextureView>(R.id.texture_view) }

    private val topLine: View by lazy { findViewById<View>(R.id.top_line) }

    private val bottomLine: View by lazy { findViewById<View>(R.id.bottom_line) }

    private val topIgnoredArea: LinearLayout by lazy { findViewById<LinearLayout>(R.id.top_ignored_area) }

    private val bottomIgnoredArea: LinearLayout by lazy { findViewById<LinearLayout>(R.id.bottom_ignored_area) }

    private val detectButton: Button by lazy { findViewById<Button>(R.id.detect_button) }

    private val detectedTextView: TextView by lazy { findViewById<TextView>(R.id.detected_text_view) }

    private val overlayLayer: OverlayLayer by lazy { findViewById<OverlayLayer>(R.id.overlay_layer) }

    // views over


    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            onDisconnected(camera)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FirebaseApp.initializeApp(this)

        /*
        // 有効領域を初期化
        topIgnoredArea.setPadding(0, 200, 0, 0)
        bottomIgnoredArea.setPadding(0, 0, 0, 400)

        // 上下バーを移動で有効領域を調整
        topLine.apply {
            var previousY = 0f
            var previousPadding = 0
            setOnTouchListener { _, event -> when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    previousY = event.y
                    previousPadding = topIgnoredArea.paddingTop
                    true
                }

                MotionEvent.ACTION_UP -> {
                    previousY = 0f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val diff = (event.y - previousY).toInt()
                    val updated = previousPadding + diff
                    if (diff < 0 && updated >= 100 || updated + MINIMUM_VALID_HEIGHT < bottomIgnoredArea.y) {
                        topIgnoredArea.updatePadding(top = updated)
                        true
                    }
                    else false
                }

                else -> false
            }}
        }

        bottomLine.apply {
            var previousY = 0f
            var previousPadding = 0
            setOnTouchListener { _, event -> when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    previousY = event.y
                    previousPadding = bottomIgnoredArea.paddingBottom
                    true
                }

                MotionEvent.ACTION_UP -> {
                    previousY = 0f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val diff = (event.y - previousY).toInt()
                    val updated = previousPadding + diff
                    if (diff > 0 && updated >= 100 || rootView.height - topIgnoredArea.height - updated > MINIMUM_VALID_HEIGHT) {
                        bottomIgnoredArea.updatePadding(bottom = updated)
                        true
                    }
                    else false
                }

                else -> false
            }}
        }
         */

        detectButton.setOnClickListener {
//            detect()
            capturedBitmap =
                if (capturedBitmap == null) textureView.bitmap
                else null

            if (capturedBitmap != null) {
                detect(capturedBitmap)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (textureView.isAvailable) {
            openCamera()
        }
        else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture?, p1: Int, p2: Int) {
                    openCamera()
                }

                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture?, p1: Int, p2: Int) {}
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture?) {
                    if (capturedBitmap == null) {
                        detect()
                    }
                }
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture?): Boolean = true
            }
        }
    }

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
                return
            }
            cameraManager.openCamera(cameraId, stateCallback, null)
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createCameraPreviewSession() {
        if (cameraDevice == null) {
            return
        }

        val surface = Surface(textureView.surfaceTexture.apply {
            setDefaultBufferSize(640, 480)
        })

        val previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
//            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        }

        cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, null)
    }

    var task: Task<FirebaseVisionText>? = null

    private fun detect(capturedBitmap: Bitmap? = null) {
        if (task != null && task?.isCanceled != true && task?.isComplete != true) {
            return
        }

//        val (validY, validHeight) = validArea
//        val width = rootLayout.width
//            val bitmap = Bitmap.createBitmap(textureView.bitmap, 0, validY, width, validHeight)
        overlayLayer.clear()

        val bitmap = capturedBitmap ?: textureView.bitmap
        if (capturedBitmap != null) {
            overlayLayer.add(BitmapLayer(bitmap))
        }

        val image = FirebaseVisionImage.fromBitmap(bitmap)
        val detector = FirebaseVision.getInstance().onDeviceTextRecognizer
        task = detector.processImage(image)
            .addOnSuccessListener { result ->
                result.textBlocks
                    .forEach {
                        overlayLayer.add(DetectedTextLayer(it))
                    }
                overlayLayer.invalidate()
                detector.close()
            }
            .addOnFailureListener {
                overlayLayer.invalidate()
                detector.close()
            }
    }

    private val validArea: Pair<Int, Int> get() {
        val y = topIgnoredArea.height
        val rootHeight = rootLayout.height
        val height = rootHeight - y - bottomIgnoredArea.height
        return y to height
    }
}

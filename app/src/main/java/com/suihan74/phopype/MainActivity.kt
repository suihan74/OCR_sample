package com.suihan74.phopype

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {

    private enum class State {
        PREVIEWING,
        CAPTURED
    }

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

    private var cameraState = State.PREVIEWING

    // camera over


    // views

    private val rootLayout: ViewGroup by lazy { findViewById<ViewGroup>(R.id.root_layout) }

    private val textureView: TextureView by lazy { findViewById<TextureView>(R.id.texture_view) }

    private val detectButton: ImageButton by lazy { findViewById<ImageButton>(R.id.detect_button) }

    private val doneButton: ImageButton by lazy { findViewById<ImageButton>(R.id.done_button) }

    private val overlayLayer: OverlayLayer by lazy { findViewById<OverlayLayer>(R.id.overlay_layer) }

    private fun getLayoutSize() =
        Point(rootLayout.width, rootLayout.height)

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

        detectButton.setOnClickListener {
            when (cameraState) {
                State.PREVIEWING ->
                    startCapture()

                State.CAPTURED ->
                    startPreview()
            }
        }

        doneButton.apply {
            visibility = View.GONE
            setOnClickListener {
                // 選択した文字列を入力メソッドに返す
                val result = overlayLayer.selected.filterIsInstance<DetectedTextLayer>()
                    .joinToString(separator = "\n") { it.data.text }

                val intent = Intent().apply {
                    putExtra("replace_key", result)
                }
                setResult(RESULT_OK, intent)
                finish()
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
                    detect()
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

        val displaySize = getLayoutSize()
        val surface = Surface(textureView.surfaceTexture.apply {
            setDefaultBufferSize(displaySize.x, displaySize.y)
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

    private fun detect(image: FirebaseVisionImage) {
        if (task != null && task?.isCanceled != true && task?.isComplete != true) {
            return
        }

        val detector = FirebaseVision.getInstance().onDeviceTextRecognizer
        task = detector.processImage(image)
            .addOnSuccessListener { result ->
                overlayLayer.clear()
                result.textBlocks
                    .forEach {
                        it.lines.forEach { line ->
                            overlayLayer.add(DetectedTextLayer(this, line))
                        }
                    }
                overlayLayer.invalidate()
                detector.close()
            }
            .addOnFailureListener {
                overlayLayer.invalidate()
                detector.close()
            }
    }

    private fun detect(capturedBitmap: Bitmap? = null) {
        val bitmap = capturedBitmap ?: textureView.bitmap
        val image = FirebaseVisionImage.fromBitmap(bitmap)
        detect(image)
    }

    private fun detect(capturedImage: Image) {
        val image = FirebaseVisionImage.fromMediaImage(capturedImage, 0)
        detect(image)
    }

    private fun createCameraCaptureSession() {
        if (cameraDevice == null) {
            return
        }

        val displaySize = getLayoutSize()
        val surface = Surface(textureView.surfaceTexture.apply {
            setDefaultBufferSize(displaySize.x, displaySize.y)
        })

        val imageReader = ImageReader.newInstance(
            displaySize.x,
            displaySize.y,
            ImageFormat.JPEG,
            2)
            .apply {
                setOnImageAvailableListener({ image ->
                    Log.d("message", "gotcha!")
                    image.close()
                }, null)
            }
        val surfaces = listOf(surface, imageReader.surface)

        val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageReader.surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
//            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        }

        cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                val request = captureRequestBuilder.build()

                captureSession?.capture(request, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        captureSession = null
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        super.onCaptureFailed(session, request, failure)
                        captureSession = null
                    }
                }, null)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }, null)

    }

    private fun startCapture() {
        cameraState = State.CAPTURED
        captureSession?.stopRepeating()
        overlayLayer.clear()
        doneButton.visibility = View.VISIBLE

        createCameraCaptureSession()
    }

    private fun startPreview() {
        cameraState = State.PREVIEWING
        overlayLayer.clear()
        doneButton.visibility = View.GONE

        createCameraPreviewSession()
    }
}

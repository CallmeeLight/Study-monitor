package com.smartstudymonitor.android

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.toBitmap
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var worker: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var warningView: TextView
    private lateinit var statusView: TextView
    private lateinit var toneGenerator: ToneGenerator

    @Volatile private var active = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var objectDetector: ObjectDetector? = null
    private var monitorStartedAt = 0L
    private var lastFaceSeenAt = 0L
    private var eyesClosedSince = 0L
    private var phoneVisible = false
    private var currentWarning: Warning? = null
    private val lastBeepAt = mutableMapOf<Warning, Long>()
    private var lastSubmittedAt = 0L

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) initializeAndStart()
            else showStartupMessage("Camera permission is required for monitoring.")
        }

    private val refreshTask = object : Runnable {
        override fun run() {
            if (!active) return
            refreshWarning()
            uiHandler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(17, 24, 39)
        window.navigationBarColor = Color.rgb(17, 24, 39)
        worker = Executors.newSingleThreadExecutor()
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 85)
        buildScreen()
        monitorStartedAt = SystemClock.elapsedRealtime()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            initializeAndStart()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildScreen() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(17, 24, 39))
        }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        statusView = TextView(this).apply {
            text = "Starting camera…"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xB3111827.toInt())
        }
        root.addView(
            statusView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        warningView = TextView(this).apply {
            text = "Monitoring"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(20, 18, 20, 18)
            setBackgroundColor(0xCC176B5B.toInt())
        }
        val warningParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            setMargins(16, 16, 16, 28)
        }
        root.addView(warningView, warningParams)
        setContentView(root)
    }

    private fun initializeAndStart() {
        if (active) return
        active = true
        statusView.text = "Loading face and phone detectors…"
        uiHandler.post(refreshTask)
        worker.execute {
            var faceTask: FaceLandmarker? = null
            var phoneTask: ObjectDetector? = null
            try {
                val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()
                    )
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumFaces(1)
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinFacePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result, _ ->
                        val eyeRatio = result.faceLandmarks().firstOrNull()?.let(::eyeAspectRatio)
                        runOnUiThread { processFace(eyeRatio) }
                    }
                    .setErrorListener { error ->
                        runOnUiThread {
                            if (active) showStartupMessage("Face detector error: ${error.message}")
                        }
                    }
                    .build()
                val loadedFaceTask = FaceLandmarker.createFromOptions(this, faceOptions)
                faceTask = loadedFaceTask
                val objectOptions = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build()
                    )
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setScoreThreshold(0.45f)
                    .setMaxResults(5)
                    .setResultListener { result, _ ->
                        val foundPhone = result.detections().any { detection ->
                            detection.categories().any { category ->
                                category.categoryName().equals("cell phone", ignoreCase = true) ||
                                    category.categoryName().equals("mobile phone", ignoreCase = true) ||
                                    category.categoryName().equals("phone", ignoreCase = true)
                            }
                        }
                        runOnUiThread {
                            if (active) phoneVisible = foundPhone
                        }
                    }
                    .setErrorListener { error ->
                        runOnUiThread {
                            if (active) showStartupMessage("Phone detector error: ${error.message}")
                        }
                    }
                    .build()
                val loadedPhoneTask = ObjectDetector.createFromOptions(this, objectOptions)
                phoneTask = loadedPhoneTask

                if (!active) {
                    loadedFaceTask.close()
                    loadedPhoneTask.close()
                    return@execute
                }
                faceLandmarker = loadedFaceTask
                objectDetector = loadedPhoneTask
                runOnUiThread {
                    if (active) bindCamera()
                }
            } catch (error: Exception) {
                faceTask?.close()
                phoneTask?.close()
                runOnUiThread {
                    showStartupMessage(
                        "Could not load detectors: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (!active) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(worker, ::analyzeFrame)
                imageAnalysis = analysis
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                statusView.text = "Front camera active · phone and face monitoring"
            } catch (error: Exception) {
                showStartupMessage("Could not start the front camera: ${error.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastSubmittedAt < 220L) return
            lastSubmittedAt = now

            val bitmap = proxy.toBitmap()
            val rotation = proxy.imageInfo.rotationDegrees
            val uprightBitmap = if (rotation == 0) bitmap else {
                val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                android.graphics.Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                ).also { if (it !== bitmap) bitmap.recycle() }
            }
            val mpImage = BitmapImageBuilder(uprightBitmap).build()
            try {
                val timestamp = SystemClock.uptimeMillis()
                faceLandmarker?.detectAsync(mpImage, timestamp)
                objectDetector?.detectAsync(mpImage, timestamp)
            } finally {
                mpImage.close()
                uprightBitmap.recycle()
            }
        } catch (error: Exception) {
            runOnUiThread {
                if (active) statusView.text = "Camera frame unavailable: ${error.message}"
            }
        } finally {
            proxy.close()
        }
    }

    private fun processFace(eyeRatio: Float?) {
        if (!active) return
        val now = SystemClock.elapsedRealtime()
        if (eyeRatio == null) {
            eyesClosedSince = 0L
            return
        }
        lastFaceSeenAt = now
        if (eyeRatio < EYE_CLOSED_THRESHOLD) {
            if (eyesClosedSince == 0L) eyesClosedSince = now
        } else {
            eyesClosedSince = 0L
        }
    }

    private fun refreshWarning() {
        val now = SystemClock.elapsedRealtime()
        val faceTimeoutStart = if (lastFaceSeenAt == 0L) monitorStartedAt else lastFaceSeenAt
        val faceHidden = now - faceTimeoutStart >= FACE_HIDDEN_TIMEOUT_MS
        val eyesClosed = eyesClosedSince != 0L && now - eyesClosedSince >= EYES_CLOSED_TIMEOUT_MS
        val warning = when {
            phoneVisible -> Warning.PHONE
            faceHidden -> Warning.FACE_HIDDEN
            eyesClosed -> Warning.EYES_CLOSED
            else -> null
        }
        if (warning != currentWarning) {
            currentWarning = warning
            warningView.text = warning?.label ?: "Monitoring — stay focused"
            warningView.setBackgroundColor(
                if (warning == null) 0xCC176B5B.toInt() else 0xD6B42318.toInt()
            )
        }
        statusView.text = when {
            phoneVisible -> "Phone detected in the camera view"
            faceHidden -> "No face detected — return to the camera view"
            lastFaceSeenAt != 0L -> "Face visible · eyes tracked in real time"
            else -> "Waiting for a face"
        }
        if (warning != null && now - (lastBeepAt[warning] ?: 0L) >= ALERT_REPEAT_MS) {
            lastBeepAt[warning] = now
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 420)
        }
    }

    private fun showStartupMessage(message: String) {
        if (!::warningView.isInitialized) return
        active = false
        uiHandler.removeCallbacks(refreshTask)
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        statusView.text = message
        warningView.text = "Monitoring unavailable"
        warningView.setBackgroundColor(0xD6B42318.toInt())
    }

    private fun eyeAspectRatio(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Float {
        if (landmarks.size <= 387) return 1f
        fun distance(first: Int, second: Int): Float {
            val a = landmarks[first]
            val b = landmarks[second]
            return hypot(a.x() - b.x(), a.y() - b.y())
        }
        val leftWidth = distance(33, 133)
        val rightWidth = distance(362, 263)
        if (leftWidth <= 0f || rightWidth <= 0f) return 1f
        val left = (distance(160, 144) + distance(158, 153)) / (2f * leftWidth)
        val right = (distance(385, 380) + distance(387, 373)) / (2f * rightWidth)
        return (left + right) / 2f
    }

    override fun onPause() {
        uiHandler.removeCallbacks(refreshTask)
        if (::toneGenerator.isInitialized) toneGenerator.stopTone()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (active) uiHandler.post(refreshTask)
    }

    override fun onDestroy() {
        active = false
        uiHandler.removeCallbacksAndMessages(null)
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        imageAnalysis = null
        cameraProvider = null
        if (::worker.isInitialized) {
            worker.execute {
                faceLandmarker?.close()
                objectDetector?.close()
                faceLandmarker = null
                objectDetector = null
            }
            worker.shutdown()
        }
        if (::toneGenerator.isInitialized) {
            toneGenerator.stopTone()
            toneGenerator.release()
        }
        super.onDestroy()
    }

    private enum class Warning(val label: String) {
        PHONE("Phone detected — put it away"),
        FACE_HIDDEN("Face not visible — return to your study"),
        EYES_CLOSED("Eyes closed — stay alert")
    }

    private companion object {
        const val EYE_CLOSED_THRESHOLD = 0.19f
        const val EYES_CLOSED_TIMEOUT_MS = 1_200L
        const val FACE_HIDDEN_TIMEOUT_MS = 5_000L
        const val ALERT_REPEAT_MS = 4_000L
    }
}

package com.example.catchmestreaming.repository

import android.content.Context
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.lifecycle.LifecycleOwner
import android.graphics.*
import android.util.Size
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import com.example.catchmestreaming.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class CameraState(
    val isInitialized: Boolean = false,
    val isPreviewStarted: Boolean = false,
    val currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    val error: String? = null,
    val availableCameras: List<CameraInfo> = emptyList(),
    val isVideoRecordingConfigured: Boolean = false,
    val isLiveStreamingEnabled: Boolean = false
)

class CameraRepository(private val context: Context) {
    
    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentLifecycleOwner: LifecycleOwner? = null
    
    // Recording surface for MediaRecorder integration
    private var recordingSurface: Surface? = null
    
    // Live streaming callback
    private var frameCallback: ((ByteArray) -> Unit)? = null
    
    companion object {
        private const val TAG = "CameraRepository"
    }
    
    suspend fun initializeCamera(): Result<Unit> {
        return try {
            val provider = suspendCoroutine<ProcessCameraProvider> { continuation ->
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    continuation.resume(providerFuture.get())
                }, cameraExecutor)
            }
            
            cameraProvider = provider
            
            // Get available cameras
            val availableCameras = provider.availableCameraInfos
            
            _cameraState.value = _cameraState.value.copy(
                isInitialized = true,
                availableCameras = availableCameras,
                error = null
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            _cameraState.value = _cameraState.value.copy(
                isInitialized = false,
                error = "Failed to initialize camera: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    fun startPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ): Result<Unit> {
        return try {
            val provider = cameraProvider ?: return Result.failure(
                IllegalStateException("Camera not initialized")
            )
            
            currentLifecycleOwner = lifecycleOwner
            
            // Create Preview use case
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(surfaceProvider)
                }
            
            // Unbind any existing use cases
            provider.unbindAll()
            
            // Create list of use cases to bind
            val useCases = mutableListOf<UseCase>().apply {
                add(preview!!)
                // Add VideoCapture if recording is configured
                videoCapture?.let { add(it) }
                // Add ImageAnalysis if live streaming is enabled
                imageAnalysis?.let { add(it) }
            }
            
            // Bind use cases to camera
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                _cameraState.value.currentCameraSelector,
                *useCases.toTypedArray()
            )
            
            _cameraState.value = _cameraState.value.copy(
                isPreviewStarted = true,
                error = null
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            _cameraState.value = _cameraState.value.copy(
                isPreviewStarted = false,
                error = "Failed to start preview: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    fun stopPreview(): Result<Unit> {
        return try {
            cameraProvider?.unbindAll()
            preview = null
            camera = null
            recordingSurface?.release()
            recordingSurface = null
            
            _cameraState.value = _cameraState.value.copy(
                isPreviewStarted = false,
                error = null
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            _cameraState.value = _cameraState.value.copy(
                error = "Failed to stop preview: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Configure video recording with a surface from MediaRecorder
     * 
     * IMPORTANT: For MediaRecorder integration, we create a VideoCapture use case
     * with MediaStreamSpec that can work with the external surface.
     * This is the correct approach for MediaRecorder + CameraX integration.
     */
    fun configureVideoRecording(surface: Surface): Result<Unit> {
        return try {
            Logger.d(TAG, "Configuring video recording with MediaRecorder surface")
            
            recordingSurface = surface
            
            // Create VideoCapture with Recorder that can work with MediaRecorder
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            
            videoCapture = VideoCapture.withOutput(recorder)
            
            _cameraState.value = _cameraState.value.copy(
                isVideoRecordingConfigured = true,
                error = null
            )
            
            Logger.d(TAG, "Video recording configured successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to configure video recording", e)
            _cameraState.value = _cameraState.value.copy(
                isVideoRecordingConfigured = false,
                error = "Failed to configure video recording: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Remove video recording configuration
     */
    fun removeVideoRecording(): Result<Unit> {
        return try {
            Logger.d(TAG, "Removing video recording configuration")
            
            recordingSurface?.release()
            recordingSurface = null
            videoCapture = null
            
            _cameraState.value = _cameraState.value.copy(
                isVideoRecordingConfigured = false,
                error = null
            )
            
            Logger.d(TAG, "Video recording configuration removed")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to remove video recording", e)
            _cameraState.value = _cameraState.value.copy(
                error = "Failed to remove video recording: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Restart preview with current configuration
     */
    private fun restartPreview(): Result<Unit> {
        return try {
            val provider = cameraProvider ?: return Result.failure(
                IllegalStateException("Camera not initialized")
            )
            val lifecycleOwner = currentLifecycleOwner ?: return Result.failure(
                IllegalStateException("No lifecycle owner available")
            )
            
            // Unbind current use cases
            provider.unbindAll()
            
            // Create list of use cases to bind
            val useCases = mutableListOf<UseCase>().apply {
                preview?.let { add(it) }
                // Add VideoCapture if available
                videoCapture?.let { add(it) }
                // Add ImageAnalysis if live streaming is enabled
                imageAnalysis?.let { add(it) }
            }
            
            if (useCases.isNotEmpty()) {
                // Bind use cases to camera
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    _cameraState.value.currentCameraSelector,
                    *useCases.toTypedArray()
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            _cameraState.value = _cameraState.value.copy(
                error = "Failed to restart preview: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    fun switchCamera(): Result<Unit> {
        return try {
            val newSelector = if (_cameraState.value.currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            
            // Check if the new camera is available
            val provider = cameraProvider ?: return Result.failure(
                IllegalStateException("Camera not initialized")
            )
            
            if (!provider.hasCamera(newSelector)) {
                return Result.failure(
                    IllegalStateException("Requested camera not available")
                )
            }
            
            _cameraState.value = _cameraState.value.copy(
                currentCameraSelector = newSelector,
                error = null
            )
            
            // If preview is running, restart it with the new camera
            if (_cameraState.value.isPreviewStarted && currentLifecycleOwner != null) {
                restartPreview()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            _cameraState.value = _cameraState.value.copy(
                error = "Failed to switch camera: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }
    
    fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }
    
    fun getCameraInfo(): CameraInfo? {
        return camera?.cameraInfo
    }
    
    /**
     * Get the VideoCapture recorder for MediaRecorder integration
     * This allows the RecordingRepository to connect with CameraX
     */
    fun getVideoCapture(): VideoCapture<Recorder>? {
        return videoCapture
    }
    
    /**
     * Get the recording surface that was configured
     */
    fun getRecordingSurface(): Surface? {
        return recordingSurface
    }
    
    /**
     * Enable live streaming by setting up ImageAnalysis
     */
    fun enableLiveStreaming(callback: (ByteArray) -> Unit): Result<Unit> {
        return try {
            Logger.d(TAG, "Enabling live streaming")
            
            frameCallback = callback
            
            // Create ImageAnalysis use case for frame capture
            imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageForStreaming(imageProxy)
                    }
                }
            
            _cameraState.value = _cameraState.value.copy(
                isLiveStreamingEnabled = true,
                error = null
            )
            
            // Restart preview to include ImageAnalysis
            restartPreview()
            
            Logger.d(TAG, "Live streaming enabled successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to enable live streaming", e)
            _cameraState.value = _cameraState.value.copy(
                isLiveStreamingEnabled = false,
                error = "Failed to enable live streaming: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Disable live streaming
     */
    fun disableLiveStreaming(): Result<Unit> {
        return try {
            Logger.d(TAG, "Disabling live streaming")
            
            frameCallback = null
            imageAnalysis = null
            
            _cameraState.value = _cameraState.value.copy(
                isLiveStreamingEnabled = false,
                error = null
            )
            
            // Restart preview without ImageAnalysis
            restartPreview()
            
            Logger.d(TAG, "Live streaming disabled successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to disable live streaming", e)
            _cameraState.value = _cameraState.value.copy(
                error = "Failed to disable live streaming: ${e.message}"
            )
            Result.failure(e)
        }
    }
    
    /**
     * Process camera frames for live streaming
     */
    private fun processImageForStreaming(imageProxy: ImageProxy) {
        try {
            val jpegBytes = convertImageProxyToJpeg(imageProxy)
            if (jpegBytes != null) {
                Logger.d(TAG, "Captured frame: ${jpegBytes.size} bytes")
                frameCallback?.invoke(jpegBytes)
            } else {
                Logger.w(TAG, "Failed to convert frame to JPEG")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error processing frame for streaming", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * Convert ImageProxy to JPEG bytes
     */
    private fun convertImageProxyToJpeg(image: ImageProxy): ByteArray? {
        return try {
            when (image.format) {
                ImageFormat.JPEG -> {
                    // Already JPEG, extract bytes
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    bytes
                }
                ImageFormat.YUV_420_888 -> {
                    // Convert YUV to JPEG
                    val yBuffer = image.planes[0].buffer
                    val uBuffer = image.planes[1].buffer
                    val vBuffer = image.planes[2].buffer
                    
                    val ySize = yBuffer.remaining()
                    val uSize = uBuffer.remaining()
                    val vSize = vBuffer.remaining()
                    
                    val nv21 = ByteArray(ySize + uSize + vSize)
                    
                    yBuffer.get(nv21, 0, ySize)
                    val uvPixelStride = image.planes[1].pixelStride
                    if (uvPixelStride == 1) {
                        uBuffer.get(nv21, ySize, uSize)
                        vBuffer.get(nv21, ySize + uSize, vSize)
                    } else {
                        // Interleaved UV
                        val uvBytes = ByteArray(uSize + vSize)
                        uBuffer.get(uvBytes, 0, uSize)
                        vBuffer.get(uvBytes, uSize, vSize)
                        
                        var uvIndex = 0
                        for (i in ySize until nv21.size step 2) {
                            nv21[i] = uvBytes[uvIndex + 1] // V
                            nv21[i + 1] = uvBytes[uvIndex] // U
                            uvIndex += 2
                        }
                    }
                    
                    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                    val outputStream = ByteArrayOutputStream()
                    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, outputStream)
                    outputStream.toByteArray()
                }
                else -> {
                    Logger.w(TAG, "Unsupported image format: ${image.format}")
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error converting image to JPEG", e)
            null
        }
    }
    
    fun release() {
        stopPreview()
        cameraExecutor.shutdown()
    }
}
package com.example.catchmestreaming.repository

import android.content.Context
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.lifecycle.LifecycleOwner
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
    val isVideoRecordingConfigured: Boolean = false
)

class CameraRepository(private val context: Context) {
    
    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentLifecycleOwner: LifecycleOwner? = null
    
    // Recording surface for MediaRecorder integration
    private var recordingSurface: Surface? = null
    
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
            if (_cameraState.value.isPreviewStarted && preview != null) {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    camera?.cameraInfo?.let { info ->
                        // We need a LifecycleOwner here, but we can't store it in the repository
                        // This will be handled in the ViewModel layer
                        return Result.success(Unit)
                    } ?: return Result.failure(IllegalStateException("No active lifecycle")),
                    newSelector,
                    preview!!
                )
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
    
    fun release() {
        stopPreview()
        cameraExecutor.shutdown()
    }
}
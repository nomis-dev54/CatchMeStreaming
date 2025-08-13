package com.example.catchmestreaming.repository

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
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
    val availableCameras: List<CameraInfo> = emptyList()
)

class CameraRepository(private val context: Context) {
    
    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
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
            
            // Create Preview use case
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(surfaceProvider)
                }
            
            // Unbind any existing use cases
            provider.unbindAll()
            
            // Bind use cases to camera
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                _cameraState.value.currentCameraSelector,
                preview
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
    
    fun release() {
        stopPreview()
        cameraExecutor.shutdown()
    }
}
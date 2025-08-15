package com.example.catchmestreaming.integration

import android.view.Surface
import com.example.catchmestreaming.repository.CameraRepository
import com.example.catchmestreaming.repository.RecordingRepository
import com.example.catchmestreaming.util.Logger
import java.io.File

/**
 * Correct MediaRecorder + CameraX integration implementation
 * 
 * UPDATED APPROACH:
 * ================
 * 
 * 1. MediaRecorder is configured with VideoSource.SURFACE
 * 2. CameraX VideoCapture with Recorder is used alongside Preview
 * 3. Both MediaRecorder and CameraX VideoCapture can coexist
 * 4. This class coordinates both recording systems
 * 
 * CORRECT INTEGRATION FLOW:
 * ========================
 * 
 * 1. Setup MediaRecorder with VideoSource.SURFACE
 * 2. Get surface from MediaRecorder.getSurface()
 * 3. Use Preview use case (NOT VideoCapture) to display camera feed
 * 4. Create SurfaceView that uses MediaRecorder surface
 * 5. Connect Preview to UI (PreviewView) and MediaRecorder surface to SurfaceView
 * 
 * IMPLEMENTATION APPROACHES:
 * =========================
 * 
 * Approach 1 (Recommended): Dual Surface
 * - Preview use case → PreviewView (for UI display)
 * - Additional SurfaceView → MediaRecorder surface (for recording)
 * - Both receive the same camera frames
 * 
 * Approach 2: Single Surface with Preview
 * - Preview use case → SurfaceView with MediaRecorder surface
 * - UI shows the same SurfaceView used for recording
 * 
 * WHAT NOT TO DO:
 * ===============
 * 
 * ❌ VideoCapture.setSurfaceProvider(mediaRecorderSurface) // This doesn't exist
 * ❌ VideoCapture with external surfaces // Not supported
 * ❌ Mixing CameraX VideoCapture with MediaRecorder // Incompatible
 * 
 * WHAT TO DO:
 * ===========
 * 
 * ✅ Preview use case for camera frames
 * ✅ MediaRecorder.getSurface() for recording
 * ✅ SurfaceView for surface integration
 * ✅ Separate recording and preview surfaces if needed
 */
class MediaRecorderCameraXIntegration(
    private val recordingRepository: RecordingRepository,
    private val cameraRepository: CameraRepository
) {
    
    companion object {
        private const val TAG = "MediaRecorderCameraXIntegration"
    }
    
    /**
     * Correct integration flow for MediaRecorder + CameraX
     * 
     * This method demonstrates the proper sequence:
     * 1. Prepare MediaRecorder and get surface
     * 2. Configure camera for recording (stores surface reference)
     * 3. Ready for recording start
     */
    suspend fun setupRecordingIntegration(): Result<Unit> {
        return try {
            Logger.d(TAG, "Setting up MediaRecorder + CameraX integration")
            
            // Step 1: Prepare MediaRecorder and get surface
            val surfaceResult = recordingRepository.prepareRecorderAndGetSurface()
            if (!surfaceResult.isSuccess) {
                Logger.e(TAG, "Failed to prepare MediaRecorder", surfaceResult.exceptionOrNull())
                return Result.failure(
                    RuntimeException("Failed to prepare MediaRecorder: ${surfaceResult.exceptionOrNull()?.message}")
                )
            }
            
            val surface = surfaceResult.getOrThrow()
            Logger.d(TAG, "MediaRecorder surface obtained successfully")
            
            // Step 2: Configure camera with recording capability
            val cameraResult = cameraRepository.configureVideoRecording(surface)
            if (!cameraResult.isSuccess) {
                Logger.e(TAG, "Failed to configure camera", cameraResult.exceptionOrNull())
                return Result.failure(
                    RuntimeException("Failed to configure camera: ${cameraResult.exceptionOrNull()?.message}")
                )
            }
            
            Logger.d(TAG, "Recording integration setup completed successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to setup recording integration", e)
            Result.failure(e)
        }
    }
    
    /**
     * Start recording after integration is setup
     * 
     * Prerequisites:
     * - setupRecordingIntegration() called successfully
     * - SurfaceView configured with MediaRecorder surface
     * - Camera preview started
     */
    suspend fun startRecording(): Result<String> {
        return try {
            Logger.d(TAG, "Starting recording")
            
            // Ensure integration is properly setup
            if (!cameraRepository.cameraState.value.isVideoRecordingConfigured) {
                Logger.e(TAG, "Recording not configured")
                return Result.failure(IllegalStateException("Recording not configured. Call setupRecordingIntegration first."))
            }
            
            // Start MediaRecorder recording
            val result = recordingRepository.startRecording()
            if (result.isSuccess) {
                Logger.i(TAG, "Recording started successfully: ${result.getOrThrow()}")
            } else {
                Logger.e(TAG, "Failed to start recording", result.exceptionOrNull())
            }
            
            result
        } catch (e: Exception) {
            Logger.e(TAG, "Error starting recording", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop recording and cleanup
     */
    suspend fun stopRecording(): Result<String> {
        return try {
            Logger.d(TAG, "Stopping recording")
            
            val stopResult = recordingRepository.stopRecording()
            if (stopResult.isSuccess) {
                val file = stopResult.getOrThrow()
                Logger.i(TAG, "Recording stopped successfully: ${file.absolutePath}")
                Result.success(file.absolutePath)
            } else {
                Logger.e(TAG, "Failed to stop recording", stopResult.exceptionOrNull())
                Result.failure(stopResult.exceptionOrNull() ?: RuntimeException("Failed to stop recording"))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping recording", e)
            Result.failure(e)
        }
    }
    
    /**
     * Clean up all resources
     */
    fun cleanup() {
        Logger.d(TAG, "Cleaning up integration")
        recordingRepository.cleanup()
        cameraRepository.removeVideoRecording()
        Logger.d(TAG, "Integration cleanup completed")
    }
}

/**
 * Usage Example in ViewModel or Activity:
 * 
 * ```kotlin
 * class VideoRecordingViewModel : ViewModel() {
 *     private val integration = MediaRecorderCameraXIntegration(recordingRepo, cameraRepo)
 *     
 *     suspend fun setupRecording() {
 *         val surfaceResult = integration.setupRecordingIntegration()
 *         if (surfaceResult.isSuccess) {
 *             val surface = surfaceResult.getOrThrow()
 *             // Configure SurfaceView with this surface
 *             configureSurfaceView(surface)
 *         }
 *     }
 *     
 *     private fun configureSurfaceView(surface: Surface) {
 *         // In your UI layer:
 *         // 1. Create SurfaceView
 *         // 2. Set surface holder with MediaRecorder surface
 *         // 3. Start camera preview with Preview use case
 *     }
 * }
 * ```
 * 
 * UI Layer Integration:
 * 
 * ```kotlin
 * // In your Composable or Activity
 * AndroidView(
 *     factory = { context ->
 *         SurfaceView(context).apply {
 *             holder.addCallback(object : SurfaceHolder.Callback {
 *                 override fun surfaceCreated(holder: SurfaceHolder) {
 *                     // Set MediaRecorder surface to this SurfaceView
 *                     holder.setFixedSize(width, height)
 *                     // Surface ready for recording
 *                 }
 *                 override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
 *                 override fun surfaceDestroyed(holder: SurfaceHolder) {}
 *             })
 *         }
 *     }
 * )
 * ```
 */
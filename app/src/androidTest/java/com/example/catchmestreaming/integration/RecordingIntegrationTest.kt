package com.example.catchmestreaming.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.data.RecordingState
import com.example.catchmestreaming.data.VideoQuality
import com.example.catchmestreaming.integration.MediaRecorderCameraXIntegration
import com.example.catchmestreaming.repository.CameraRepository
import com.example.catchmestreaming.repository.RecordingRepository
import com.example.catchmestreaming.util.FileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end integration tests for the complete recording workflow
 * Tests camera → MediaRecorder → CameraX → MP4 file creation
 */
@RunWith(AndroidJUnit4::class)
class RecordingIntegrationTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var context: Context
    private lateinit var recordingRepository: RecordingRepository
    private lateinit var cameraRepository: CameraRepository
    private lateinit var fileManager: FileManager
    private lateinit var integration: MediaRecorderCameraXIntegration

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        recordingRepository = RecordingRepository(context)
        cameraRepository = CameraRepository(context)
        fileManager = FileManager(context)
        integration = MediaRecorderCameraXIntegration(
            recordingRepository = recordingRepository,
            cameraRepository = cameraRepository
        )
    }

    @After
    fun tearDown() {
        runTest {
            recordingRepository.stopRecording()
            recordingRepository.cleanup()
            cameraRepository.release()
            integration.cleanup()
        }
    }

    @Test
    fun completeRecordingWorkflow_shouldCreateMP4File() = runTest {
        // Step 1: Configure recording settings
        val recordingConfig = RecordingConfig(
            quality = VideoQuality.HD_720P,
            maxDurationMs = 10000, // 10 seconds
            maxFileSizeBytes = 50 * 1024 * 1024, // 50MB
            includeAudio = true
        )

        val configResult = recordingRepository.updateConfiguration(recordingConfig)
        assertTrue("Recording configuration should succeed", configResult.isSuccess)

        // Step 2: Initialize camera
        val cameraResult = cameraRepository.initializeCamera()
        assertTrue("Camera initialization should succeed", cameraResult.isSuccess)
        delay(1000)

        // Step 3: Setup MediaRecorder + CameraX integration
        val integrationResult = integration.setupForRecording()
        assertTrue("Integration setup should succeed", integrationResult.isSuccess)

        // Step 4: Start recording
        val recordingResult = recordingRepository.startRecording(VideoQuality.HD_720P)
        assertTrue("Recording should start successfully", recordingResult.isSuccess)

        // Step 5: Verify recording state
        val recordingState = recordingRepository.recordingState.first()
        assertTrue("Should be in recording state", recordingState is RecordingState.Recording)

        if (recordingState is RecordingState.Recording) {
            assertNotNull("Recording file path should not be null", recordingState.filePath)
            assertTrue("Recording should have start time", recordingState.startTime > 0)
        }

        // Step 6: Let recording run for a few seconds
        delay(3000)

        // Step 7: Stop recording
        val stopResult = recordingRepository.stopRecording()
        assertTrue("Recording should stop successfully", stopResult.isSuccess)

        // Step 8: Verify final state and file creation
        val finalState = recordingRepository.recordingState.first()
        assertTrue("Should be in stopped state", 
            finalState is RecordingState.Idle || finalState is RecordingState.Completed)

        // Step 9: Verify MP4 file was created
        val recordings = recordingRepository.getRecordings().first()
        assertTrue("Should have at least one recording", recordings.isNotEmpty())

        val latestRecording = recordings.maxByOrNull { it.timestamp }
        assertNotNull("Latest recording should exist", latestRecording)

        latestRecording?.let { recording ->
            val file = File(recording.filePath)
            assertTrue("MP4 file should exist", file.exists())
            assertTrue("File should have content", file.length() > 0)
            assertTrue("File should be MP4", recording.filePath.endsWith(".mp4"))
            
            // Verify file is in secure location
            assertTrue("File should be in app directory", 
                recording.filePath.contains(context.getExternalFilesDir(null)?.absolutePath ?: ""))
        }
    }

    @Test
    fun recordingWithDifferentQualitySettings_shouldCreateAppropriateFiles() = runTest {
        cameraRepository.initializeCamera()
        delay(500)
        integration.setupForRecording()

        val qualitySettings = listOf(
            VideoQuality.HD_720P,
            VideoQuality.HD_1080P
        )

        qualitySettings.forEach { quality ->
            val config = RecordingConfig(
                quality = quality,
                maxDurationMs = 5000, // 5 seconds
                includeAudio = true
            )

            recordingRepository.updateConfiguration(config)
            
            val startResult = recordingRepository.startRecording(quality)
            assertTrue("Recording with $quality should start", startResult.isSuccess)

            delay(2000) // Record for 2 seconds

            val stopResult = recordingRepository.stopRecording()
            assertTrue("Recording with $quality should stop", stopResult.isSuccess)

            delay(500) // Allow state to settle
        }

        // Verify multiple recordings were created
        val recordings = recordingRepository.getRecordings().first()
        assertTrue("Should have multiple recordings", recordings.size >= 2)

        recordings.forEach { recording ->
            val file = File(recording.filePath)
            assertTrue("Recording ${recording.id} file should exist", file.exists())
            assertTrue("Recording ${recording.id} should have content", file.length() > 0)
        }
    }

    @Test
    fun concurrentRecordingAndPreview_shouldWork() = runTest {
        // Initialize camera and start preview
        cameraRepository.initializeCamera()
        delay(500)

        val previewResult = cameraRepository.startPreview()
        assertTrue("Preview should start", previewResult.isSuccess)

        // Setup recording while preview is active
        integration.setupForRecording()

        val config = RecordingConfig.createDefault(VideoQuality.HD_720P)
        recordingRepository.updateConfiguration(config)

        // Start recording while preview is active
        val recordingResult = recordingRepository.startRecording(VideoQuality.HD_720P)
        assertTrue("Recording should work with active preview", recordingResult.isSuccess)

        // Both should be active
        val cameraState = cameraRepository.cameraState.first()
        val recordingState = recordingRepository.recordingState.first()

        assertTrue("Camera should be initialized", cameraState.isInitialized)
        assertTrue("Preview should be active", cameraState.isPreviewActive)
        assertTrue("Should be recording", recordingState is RecordingState.Recording)

        delay(2000)

        // Stop recording, preview should continue
        recordingRepository.stopRecording()
        delay(500)

        val finalCameraState = cameraRepository.cameraState.first()
        val finalRecordingState = recordingRepository.recordingState.first()

        assertTrue("Preview should still be active", finalCameraState.isPreviewActive)
        assertTrue("Recording should be stopped", 
            finalRecordingState is RecordingState.Idle || finalRecordingState is RecordingState.Completed)
    }

    @Test
    fun recordingFailureRecovery_shouldHandleGracefully() = runTest {
        // Try to start recording without proper setup
        val config = RecordingConfig.createDefault(VideoQuality.HD_720P)
        recordingRepository.updateConfiguration(config)

        val recordingResult = recordingRepository.startRecording(VideoQuality.HD_720P)
        
        // Should handle the error gracefully
        if (recordingResult.isFailure) {
            val recordingState = recordingRepository.recordingState.first()
            assertTrue("Should handle recording failure", 
                recordingState is RecordingState.Error || recordingState is RecordingState.Idle)
            
            // Now properly setup and retry
            cameraRepository.initializeCamera()
            delay(500)
            integration.setupForRecording()
            
            val retryResult = recordingRepository.startRecording(VideoQuality.HD_720P)
            if (retryResult.isSuccess) {
                delay(1000)
                recordingRepository.stopRecording()
            }
        }
    }

    @Test
    fun storageSpaceValidation_shouldPreventRecordingWhenInsufficient() = runTest {
        // Configure a recording that would require enormous storage
        val massiveConfig = RecordingConfig(
            quality = VideoQuality.UHD_4K,
            maxFileSizeBytes = Long.MAX_VALUE, // Unrealistic size
            includeAudio = true
        )

        val configResult = recordingRepository.updateConfiguration(massiveConfig)
        if (configResult.isSuccess) {
            cameraRepository.initializeCamera()
            delay(500)
            integration.setupForRecording()

            // This should either fail or be limited by available storage
            val recordingResult = recordingRepository.startRecording(VideoQuality.UHD_4K)
            
            if (recordingResult.isFailure) {
                val recordingState = recordingRepository.recordingState.first()
                assertTrue("Should handle storage limitation", 
                    recordingState is RecordingState.Error)
            } else {
                // If it starts, it should respect actual available storage
                delay(1000)
                recordingRepository.stopRecording()
            }
        }
    }

    @Test
    fun maxDurationLimit_shouldStopRecordingAutomatically() = runTest {
        // Configure recording with very short max duration
        val shortConfig = RecordingConfig(
            quality = VideoQuality.HD_720P,
            maxDurationMs = 2000, // 2 seconds
            includeAudio = true
        )

        recordingRepository.updateConfiguration(shortConfig)
        cameraRepository.initializeCamera()
        delay(500)
        integration.setupForRecording()

        val recordingResult = recordingRepository.startRecording(VideoQuality.HD_720P)
        assertTrue("Recording should start", recordingResult.isSuccess)

        // Wait longer than max duration
        delay(4000)

        // Recording should have stopped automatically
        val finalState = recordingRepository.recordingState.first()
        assertTrue("Recording should have stopped automatically", 
            finalState is RecordingState.Completed || finalState is RecordingState.Idle)

        // Verify file was created despite automatic stop
        val recordings = recordingRepository.getRecordings().first()
        if (recordings.isNotEmpty()) {
            val latestRecording = recordings.maxByOrNull { it.timestamp }
            latestRecording?.let { recording ->
                val file = File(recording.filePath)
                assertTrue("Auto-stopped recording file should exist", file.exists())
            }
        }
    }

    @Test
    fun fileManagement_shouldHandleMultipleRecordings() = runTest {
        cameraRepository.initializeCamera()
        delay(500)
        integration.setupForRecording()

        val config = RecordingConfig(
            quality = VideoQuality.HD_720P,
            maxDurationMs = 3000,
            includeAudio = true
        )
        recordingRepository.updateConfiguration(config)

        // Create multiple recordings
        repeat(3) { index ->
            val recordingResult = recordingRepository.startRecording(VideoQuality.HD_720P)
            assertTrue("Recording $index should start", recordingResult.isSuccess)

            delay(1500) // Record for 1.5 seconds

            val stopResult = recordingRepository.stopRecording()
            assertTrue("Recording $index should stop", stopResult.isSuccess)

            delay(500) // Brief pause between recordings
        }

        // Verify all recordings exist
        val recordings = recordingRepository.getRecordings().first()
        assertTrue("Should have 3 recordings", recordings.size >= 3)

        // Test deletion functionality
        val firstRecording = recordings.first()
        val deleteResult = recordingRepository.deleteRecording(firstRecording.id)
        assertTrue("Should be able to delete recording", deleteResult.isSuccess)

        // Verify file was actually deleted
        val file = File(firstRecording.filePath)
        assertFalse("Deleted recording file should not exist", file.exists())

        // Verify remaining recordings still exist
        val remainingRecordings = recordingRepository.getRecordings().first()
        assertEquals("Should have one less recording", recordings.size - 1, remainingRecordings.size)
    }
}
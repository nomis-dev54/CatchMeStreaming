package com.example.catchmestreaming.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.catchmestreaming.data.RecordingConfig
import com.example.catchmestreaming.data.RecordingState
import com.example.catchmestreaming.security.InputValidator
import com.example.catchmestreaming.security.SecureStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RecordingRepositoryInstrumentedTest {

    private lateinit var context: Context
    private lateinit var repository: RecordingRepository
    private lateinit var inputValidator: InputValidator
    private lateinit var secureStorage: SecureStorage
    
    private val testOutputDirectory = "test_recordings/"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        inputValidator = InputValidator()
        secureStorage = SecureStorage(context)
        
        repository = RecordingRepository(
            context = context,
            inputValidator = inputValidator,
            secureStorage = secureStorage
        )
        
        // Clean up any existing test files
        cleanupTestFiles()
    }

    @After
    fun teardown() {
        repository.cleanup()
        cleanupTestFiles()
    }

    @Test
    fun repository_should_initialize_with_idle_state() = runTest {
        val initialState = repository.recordingState.first()
        assertTrue("Repository should initialize with idle state", initialState.isIdle)
    }

    @Test
    fun repository_should_accept_valid_configuration() = runTest {
        val config = RecordingConfig.createDefault().copy(
            outputDirectory = testOutputDirectory
        )
        
        val result = repository.updateConfiguration(config)
        
        assertTrue("Should accept valid configuration", result.isSuccess)
        assertEquals("Configuration should be stored", config, repository.getCurrentConfig())
    }

    @Test
    fun repository_should_reject_invalid_configuration() = runTest {
        val invalidConfig = RecordingConfig.createDefault().copy(
            outputDirectory = "../../../etc/passwd", // Path traversal attack
            filenamePrefix = "<script>alert('xss')</script>" // XSS attempt
        )
        
        val result = repository.updateConfiguration(invalidConfig)
        
        assertTrue("Should reject malicious configuration", result.isFailure)
        assertNull("Configuration should not be stored", repository.getCurrentConfig())
    }

    @Test
    fun repository_should_create_output_directory() = runTest {
        val config = RecordingConfig.createDefault().copy(
            outputDirectory = testOutputDirectory
        )
        
        val result = repository.createOutputDirectory(config)
        
        assertTrue("Should create output directory", result.isSuccess)
        
        // Verify directory exists
        val outputDir = File("${context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)}/$testOutputDirectory")
        assertTrue("Output directory should exist", outputDir.exists())
        assertTrue("Output directory should be a directory", outputDir.isDirectory)
    }

    @Test
    fun repository_should_validate_required_permissions() = runTest {
        val config = RecordingConfig.createDefault().copy(
            outputDirectory = testOutputDirectory
        )
        repository.updateConfiguration(config)
        
        // Check if we have required permissions
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        val result = repository.startRecording()
        
        if (!hasCameraPermission || !hasAudioPermission) {
            assertTrue("Should fail without required permissions", result.isFailure)
            val state = repository.recordingState.first()
            assertTrue("Should be in error state", state.isError)
            assertEquals("Should have permission error", 
                RecordingState.ErrorCode.PERMISSION_DENIED, (state as RecordingState.Error).code)
        }
    }

    @Test
    fun repository_should_validate_storage_space() = runTest {
        val config = RecordingConfig.createDefault().copy(
            outputDirectory = testOutputDirectory,
            minStorageRequired = Long.MAX_VALUE // Impossible storage requirement
        )
        repository.updateConfiguration(config)
        
        val result = repository.startRecording()
        
        assertTrue("Should fail with insufficient storage", result.isFailure)
        val state = repository.recordingState.first()
        if (state.isError) {
            assertEquals("Should have storage error", 
                RecordingState.ErrorCode.INSUFFICIENT_STORAGE, (state as RecordingState.Error).code)
        }
    }

    @Test
    fun repository_should_generate_unique_filenames() = runTest {
        val config = RecordingConfig.createDefault().copy(
            filenamePrefix = "test_recording"
        )
        
        val filename1 = repository.generateUniqueFilename(config)
        Thread.sleep(1) // Ensure different timestamp
        val filename2 = repository.generateUniqueFilename(config)
        
        assertNotEquals("Filenames should be unique", filename1, filename2)
        assertTrue("Filename should contain prefix", filename1.contains("test_recording"))
        assertTrue("Filename should have .mp4 extension", filename1.endsWith(".mp4"))
    }

    @Test
    fun repository_should_sanitize_malicious_filenames() = runTest {
        val config = RecordingConfig.createDefault().copy(
            filenamePrefix = "../../../malicious<>:\"|?*"
        )
        
        val sanitizedFilename = repository.generateUniqueFilename(config)
        
        assertFalse("Sanitized filename should not contain path traversal", 
            sanitizedFilename.contains("../"))
        assertFalse("Sanitized filename should not contain dangerous characters", 
            sanitizedFilename.contains("<"))
        assertFalse("Sanitized filename should not contain dangerous characters", 
            sanitizedFilename.contains(">"))
        assertTrue("Sanitized filename should have .mp4 extension", 
            sanitizedFilename.endsWith(".mp4"))
    }

    @Test
    fun repository_should_validate_output_path_security() = runTest {
        val maliciousPath = "../../../etc/passwd"
        
        val result = repository.validateOutputPath(maliciousPath)
        
        assertTrue("Should reject malicious path", result.isFailure)
        assertTrue("Should contain security error", 
            result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun repository_should_validate_recording_capabilities() = runTest {
        val config = RecordingConfig.createDefault()
        
        val result = repository.validateRecordingCapabilities(config)
        
        assertTrue("Should validate basic recording capabilities", result.isSuccess)
    }

    @Test
    fun repository_should_handle_unsupported_api_features() = runTest {
        // Test with features that require newer API levels
        val config = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            RecordingConfig.createDefault().copy(
                videoCodec = RecordingConfig.VideoCodec.H265 // Requires API 21+
            )
        } else {
            RecordingConfig.createDefault()
        }
        
        val result = repository.updateConfiguration(config)
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && 
            config.videoCodec == RecordingConfig.VideoCodec.H265) {
            assertTrue("Should reject unsupported API features", result.isFailure)
        } else {
            assertTrue("Should accept supported configuration", result.isSuccess)
        }
    }

    @Test
    fun repository_should_handle_pause_resume_on_supported_api() = runTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Test that pause/resume is not supported on older APIs
            val pauseResult = repository.pauseRecording()
            assertTrue("Pause should not be supported on API < 24", pauseResult.isFailure)
            assertTrue("Should be UnsupportedOperationException", 
                pauseResult.exceptionOrNull() is UnsupportedOperationException)
            
            val resumeResult = repository.resumeRecording()
            assertTrue("Resume should not be supported on API < 24", resumeResult.isFailure)
            assertTrue("Should be UnsupportedOperationException", 
                resumeResult.exceptionOrNull() is UnsupportedOperationException)
        }
    }

    @Test
    fun repository_should_clear_error_state() = runTest {
        // Force an error state by trying to record without permissions
        val config = RecordingConfig.createDefault().copy(
            outputDirectory = testOutputDirectory
        )
        repository.updateConfiguration(config)
        
        // This might put us in error state if permissions are missing
        repository.startRecording()
        
        // Clear any error state
        val clearResult = repository.clearError()
        
        assertTrue("Should successfully clear error", clearResult.isSuccess)
        val state = repository.recordingState.first()
        if (!state.isError) {
            assertTrue("State should be idle after clearing error", state.isIdle)
        }
    }

    @Test
    fun repository_should_list_recorded_files() = runTest {
        val config = RecordingConfig.createDefault().copy(
            outputDirectory = testOutputDirectory
        )
        repository.updateConfiguration(config)
        
        // Create output directory
        repository.createOutputDirectory(config)
        
        // Create a test file to simulate a recording
        val outputDir = File("${context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)}/$testOutputDirectory")
        val testFile = File(outputDir, "test_recording_20231201_120000.mp4")
        testFile.createNewFile()
        testFile.writeText("test content")
        
        val recordings = repository.getRecordings().first()
        
        assertTrue("Should find at least one recording", recordings.isNotEmpty())
        val testRecording = recordings.find { it.fileName == "test_recording_20231201_120000.mp4" }
        assertNotNull("Should find our test recording", testRecording)
        
        // Clean up test file
        testFile.delete()
    }

    @Test
    fun repository_should_delete_recordings_securely() = runTest {
        val config = RecordingConfig.createDefault().copy(
            outputDirectory = testOutputDirectory
        )
        repository.updateConfiguration(config)
        repository.createOutputDirectory(config)
        
        // Create a test file
        val outputDir = File("${context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)}/$testOutputDirectory")
        val testFile = File(outputDir, "test_recording_to_delete.mp4")
        testFile.createNewFile()
        testFile.writeText("test content")
        
        val deleteResult = repository.deleteRecording(testFile.absolutePath)
        
        assertTrue("Should successfully delete recording", deleteResult.isSuccess)
        assertFalse("File should no longer exist", testFile.exists())
    }

    @Test
    fun repository_should_prevent_deleting_files_outside_app_directory() = runTest {
        val maliciousPath = "/system/etc/hosts"
        
        val deleteResult = repository.deleteRecording(maliciousPath)
        
        assertTrue("Should reject deletion of system files", deleteResult.isFailure)
        assertTrue("Should be security exception", 
            deleteResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun repository_should_handle_concurrent_operations_safely() = runTest {
        val config = RecordingConfig.createDefault().copy(
            outputDirectory = testOutputDirectory
        )
        repository.updateConfiguration(config)
        
        // Try to start multiple recordings concurrently
        val result1 = repository.startRecording()
        val result2 = repository.startRecording()
        
        // At least one should succeed or both should fail gracefully
        val bothFailed = result1.isFailure && result2.isFailure
        val onlyOneSucceeded = result1.isSuccess != result2.isSuccess
        
        assertTrue("Concurrent operations should be handled safely", 
            bothFailed || onlyOneSucceeded)
    }

    private fun cleanupTestFiles() {
        try {
            val outputDir = File("${context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)}/$testOutputDirectory")
            if (outputDir.exists()) {
                outputDir.listFiles()?.forEach { file ->
                    file.delete()
                }
                outputDir.delete()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors in tests
        }
    }
}
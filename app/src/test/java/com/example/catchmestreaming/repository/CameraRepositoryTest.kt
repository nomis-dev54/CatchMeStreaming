package com.example.catchmestreaming.repository

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CameraRepositoryTest {
    
    private lateinit var context: Context
    private lateinit var cameraRepository: CameraRepository
    
    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        context = RuntimeEnvironment.getApplication()
        cameraRepository = CameraRepository(context)
    }
    
    @After
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `camera state should have correct default values`() {
        // Given
        val cameraState = CameraState()
        
        // Then
        assertFalse("Should not be initialized by default", cameraState.isInitialized)
        assertFalse("Should not be previewing by default", cameraState.isPreviewStarted)
        assertEquals("Should have back camera by default", CameraSelector.DEFAULT_BACK_CAMERA, cameraState.currentCameraSelector)
        assertNull("Should not have error by default", cameraState.error)
        assertTrue("Should have empty camera list by default", cameraState.availableCameras.isEmpty())
    }
    
    @Test
    fun `camera state should update correctly`() {
        // Given
        val initialState = CameraState()
        
        // When
        val updatedState = initialState.copy(
            isInitialized = true,
            isPreviewStarted = true,
            error = "Test error",
            currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        )
        
        // Then
        assertTrue("Should be initialized", updatedState.isInitialized)
        assertTrue("Should be previewing", updatedState.isPreviewStarted)
        assertEquals("Should have error", "Test error", updatedState.error)
        assertEquals("Should have front camera", CameraSelector.DEFAULT_FRONT_CAMERA, updatedState.currentCameraSelector)
        assertFalse("Original state should be unchanged", initialState.isInitialized)
    }
    
    @Test
    fun `repository should initialize with default state`() = runTest {
        // Given - repository created in setup
        
        // When
        val initialState = cameraRepository.cameraState.first()
        
        // Then
        assertFalse("Should not be initialized", initialState.isInitialized)
        assertFalse("Should not be previewing", initialState.isPreviewStarted)
        assertNull("Should not have error", initialState.error)
        assertTrue("Should have empty camera list", initialState.availableCameras.isEmpty())
    }
    
}
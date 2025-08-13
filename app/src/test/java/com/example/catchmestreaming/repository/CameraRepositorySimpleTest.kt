package com.example.catchmestreaming.repository

import org.junit.Test
import org.junit.Assert.*

class CameraRepositorySimpleTest {
    
    @Test
    fun `camera state should have correct default values`() {
        // Given
        val cameraState = CameraState()
        
        // Then
        assertFalse("Should not be initialized by default", cameraState.isInitialized)
        assertFalse("Should not be previewing by default", cameraState.isPreviewStarted)
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
            error = "Test error"
        )
        
        // Then
        assertTrue("Should be initialized", updatedState.isInitialized)
        assertTrue("Should be previewing", updatedState.isPreviewStarted)
        assertEquals("Should have error message", "Test error", updatedState.error)
        assertFalse("Original state should be unchanged", initialState.isInitialized)
    }
}
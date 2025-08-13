package com.example.catchmestreaming.viewmodel

import org.junit.Test
import org.junit.Assert.*

class MainViewModelTest {
    
    @Test
    fun `main ui state should have correct default values`() {
        // Given
        val uiState = MainUiState()
        
        // Then
        assertFalse("Should not be streaming by default", uiState.isStreaming)
        assertFalse("Should not be recording by default", uiState.isRecording)
        assertFalse("Should not be initialized by default", uiState.isCameraInitialized)
        assertFalse("Should not be previewing by default", uiState.isCameraPreviewStarted)
        assertFalse("Should not have camera permission by default", uiState.hasCameraPermission)
        assertFalse("Should not have audio permission by default", uiState.hasAudioPermission)
        assertNull("Should not have error by default", uiState.error)
        assertNull("Should not have RTSP URL by default", uiState.rtspUrl)
        assertFalse("Should not be able to switch camera by default", uiState.canSwitchCamera)
    }
    
    @Test
    fun `main ui state should update correctly`() {
        // Given
        val initialState = MainUiState()
        
        // When
        val updatedState = initialState.copy(
            isStreaming = true,
            isRecording = true,
            hasCameraPermission = true,
            rtspUrl = "rtsp://test.com"
        )
        
        // Then
        assertTrue("Should be streaming", updatedState.isStreaming)
        assertTrue("Should be recording", updatedState.isRecording)
        assertTrue("Should have camera permission", updatedState.hasCameraPermission)
        assertEquals("Should have RTSP URL", "rtsp://test.com", updatedState.rtspUrl)
        assertFalse("Original state should be unchanged", initialState.isStreaming)
    }
}
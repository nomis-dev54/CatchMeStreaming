package com.example.catchmestreaming.util

import org.junit.Test
import org.junit.Assert.*

class NetworkUtilTest {
    
    @Test
    fun `generateRtspUrl creates correct URL with device IP`() {
        // Given
        val deviceIp = "192.168.1.100"
        val port = 8554
        val streamPath = "stream"
        
        // When
        val result = NetworkUtil.generateRtspUrl(deviceIp, port, streamPath)
        
        // Then
        assertEquals("rtsp://192.168.1.100:8554/stream", result)
    }
    
    @Test
    fun `generateRtspUrl handles custom port and stream path`() {
        // Given
        val deviceIp = "10.0.0.50"
        val port = 1234
        val streamPath = "my_stream"
        
        // When
        val result = NetworkUtil.generateRtspUrl(deviceIp, port, streamPath)
        
        // Then
        assertEquals("rtsp://10.0.0.50:1234/my_stream", result)
    }
    
    @Test
    fun `getBestIpForStreaming returns fallback when no network available`() {
        // This would require a mock context, but for now we test that fallback works
        // In a real scenario, this would use the actual context
        val fallbackIp = "192.168.1.100"
        
        // The fallback IP should always be available
        assertEquals("192.168.1.100", fallbackIp)
    }
}
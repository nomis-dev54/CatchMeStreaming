package com.example.catchmestreaming.data

/**
 * Enum representing different streaming quality levels
 */
enum class StreamQuality(
    val displayName: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int
) {
    LOW("Low (480p)", 640, 480, 15, 500_000),
    MEDIUM("Medium (720p)", 1280, 720, 30, 2_000_000),
    HIGH("High (1080p)", 1920, 1080, 30, 4_000_000),
    ULTRA("Ultra (1080p 60fps)", 1920, 1080, 60, 8_000_000);

    companion object {
        fun fromDisplayName(displayName: String): StreamQuality? {
            return values().find { it.displayName == displayName }
        }
    }
}
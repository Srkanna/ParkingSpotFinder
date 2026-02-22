package com.sal.parkingfinder

/**
 * Data class representing a parking spot
 */
data class ParkingSpot(
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val addedBy: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val description: String = "",
    val imageBase64: String = "" // Base64 encoded image (optional)
) {
    // No-argument constructor for Firebase
    constructor() : this("", 0.0, 0.0, "", 0L, "", "")
    
    /**
     * Get how long ago the spot was added
     */
    fun getTimeAgo(): String {
        val diff = System.currentTimeMillis() - addedAt
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hours ago"
            else -> "$days days ago"
        }
    }
    
    /**
     * Check if spot has an image
     */
    fun hasImage(): Boolean = imageBase64.isNotEmpty()
}

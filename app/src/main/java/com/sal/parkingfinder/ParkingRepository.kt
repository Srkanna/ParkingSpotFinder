package com.sal.parkingfinder

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Repository for managing parking spots in Firebase Realtime Database
 */
class ParkingRepository {
    
    private val database = FirebaseDatabase.getInstance()
    private val spotsRef = database.getReference("parking_spots")
    
    companion object {
        const val MAX_SPOTS_PER_USER = 3
        const val SPOT_EXPIRY_MS = 60 * 60 * 1000L // 1 hour in milliseconds
    }
    
    /**
     * Check if user can add more spots (max 3 within 1 hour)
     */
    fun canUserAddSpot(userId: String, onResult: (Boolean, Int) -> Unit) {
        val oneHourAgo = System.currentTimeMillis() - SPOT_EXPIRY_MS
        
        // Fetch all spots and filter locally (avoids needing Firebase index)
        spotsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var activeSpots = 0
                for (child in snapshot.children) {
                    val spot = child.getValue(ParkingSpot::class.java)
                    if (spot != null && spot.addedBy == userId && spot.addedAt > oneHourAgo) {
                        activeSpots++
                    }
                }
                val canAdd = activeSpots < MAX_SPOTS_PER_USER
                val remaining = MAX_SPOTS_PER_USER - activeSpots
                onResult(canAdd, remaining)
            }

            override fun onCancelled(error: DatabaseError) {
                // On error, allow adding (fail open for better UX)
                onResult(true, MAX_SPOTS_PER_USER)
            }
        })
    }
    
    /**
     * Add a new parking spot
     */
    fun addSpot(spot: ParkingSpot, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val key = spotsRef.push().key ?: return
        val spotWithId = spot.copy(id = key)
        
        spotsRef.child(key).setValue(spotWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
    
    /**
     * Remove a parking spot (mark as taken)
     */
    fun removeSpot(spotId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        spotsRef.child(spotId).removeValue()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
    
    /**
     * Clean up expired spots (older than 1 hour)
     */
    fun cleanupExpiredSpots() {
        val oneHourAgo = System.currentTimeMillis() - SPOT_EXPIRY_MS
        
        spotsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val spot = child.getValue(ParkingSpot::class.java)
                    if (spot != null && spot.addedAt < oneHourAgo) {
                        // Remove expired spot
                        child.ref.removeValue()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Ignore cleanup errors
            }
        })
    }
    
    /**
     * Listen to all spots in real-time (filters expired spots)
     */
    fun observeSpots(
        onSpotAdded: (ParkingSpot) -> Unit,
        onSpotRemoved: (String) -> Unit,
        onError: (DatabaseError) -> Unit
    ): ChildEventListener {
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.getValue(ParkingSpot::class.java)?.let { spot ->
                    // Only add if not expired
                    if (!isSpotExpired(spot)) {
                        onSpotAdded(spot)
                    } else {
                        // Remove expired spot from database
                        snapshot.ref.removeValue()
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // Spots don't change, they're either added or removed
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.key?.let { key ->
                    onSpotRemoved(key)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // Not used
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error)
            }
        }
        
        spotsRef.addChildEventListener(listener)
        return listener
    }
    
    /**
     * Check if a spot is expired (older than 1 hour)
     */
    private fun isSpotExpired(spot: ParkingSpot): Boolean {
        val oneHourAgo = System.currentTimeMillis() - SPOT_EXPIRY_MS
        return spot.addedAt < oneHourAgo
    }
    
    /**
     * Get all spots once (for initial load)
     */
    fun getAllSpots(onSuccess: (List<ParkingSpot>) -> Unit, onError: (DatabaseError) -> Unit) {
        spotsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val spots = mutableListOf<ParkingSpot>()
                val oneHourAgo = System.currentTimeMillis() - SPOT_EXPIRY_MS
                
                for (child in snapshot.children) {
                    val spot = child.getValue(ParkingSpot::class.java)
                    if (spot != null) {
                        if (spot.addedAt >= oneHourAgo) {
                            spots.add(spot)
                        } else {
                            // Remove expired spot
                            child.ref.removeValue()
                        }
                    }
                }
                onSuccess(spots)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error)
            }
        })
    }
    
    /**
     * Remove listener
     */
    fun removeListener(listener: ChildEventListener) {
        spotsRef.removeEventListener(listener)
    }
}

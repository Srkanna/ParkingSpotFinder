package com.sal.parkingfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.content.res.ColorStateList
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: ParkingRepository
    
    private lateinit var fabAddSpot: ExtendedFloatingActionButton
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var fabAddPhoto: FloatingActionButton
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var tvSpotCount: TextView
    private lateinit var tvSpotTitle: TextView
    private lateinit var tvSpotTime: TextView
    private lateinit var ivSpotPhoto: ImageView
    private lateinit var btnMarkTaken: MaterialButton
    private lateinit var btnNavigate: MaterialButton
    
    private var currentLocation: LatLng? = null
    private var selectedSpot: ParkingSpot? = null
    private val markers = mutableMapOf<String, Marker>()
    private val spots = mutableMapOf<String, ParkingSpot>()
    
    // Add spot mode
    private var isAddingSpot = false
    private var placementMarker: Marker? = null
    private var radiusCircle: Circle? = null
    private var capturedPhotoBase64: String? = null
    private var photoFile: File? = null
    private lateinit var deviceId: String
    
    companion object {
        const val MAX_DISTANCE_METERS = 100.0 // Maximum distance from current location
        const val PREF_DEVICE_ID = "device_id"
    }
    
    // Permission request
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                enableMyLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                enableMyLocation()
            }
            else -> {
                Toast.makeText(this, getString(R.string.location_permission_needed), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Camera permission request
    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission needed to take photos", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Camera result
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoFile?.let { file ->
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        processAndBlurPhoto(bitmap)
                    } else {
                        Toast.makeText(this, "Failed to capture photo", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error processing photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Gallery result
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                bytes?.let { data ->
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bitmap != null) {
                        processAndBlurPhoto(bitmap)
                    } else {
                        Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun processAndBlurPhoto(bitmap: Bitmap) {
        var scaledBitmap = bitmap
        val maxDim = 800
        val scale = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        if (scale < 1f) {
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }
        
        // Copy to mutable bitmap so we can draw on it
        val mutableBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val image = InputImage.fromBitmap(mutableBitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        Toast.makeText(this, "Scanning for vehicle plates...", Toast.LENGTH_SHORT).show()
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val canvas = Canvas(mutableBitmap)
                val paint = Paint()
                paint.color = Color.BLACK
                paint.style = Paint.Style.FILL
                
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        line.boundingBox?.let { box ->
                            canvas.drawRect(box, paint)
                        }
                    }
                }
                
                val stream = ByteArrayOutputStream()
                mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                capturedPhotoBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
                
                Toast.makeText(this, "Photo added (Text scrambled)!", Toast.LENGTH_SHORT).show()
                fabAddPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
                if (isAddingSpot) confirmAndSaveSpot()
            }
            .addOnFailureListener { e ->
                // Fast fallback if ML fails
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                capturedPhotoBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
                Toast.makeText(this, "Photo added (No filter applied)", Toast.LENGTH_SHORT).show()
                fabAddPhoto.setImageResource(android.R.drawable.ic_menu_gallery)
                if (isAddingSpot) confirmAndSaveSpot()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupMap()
        setupClickListeners()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = ParkingRepository()
        
        // Get or create device ID for tracking user's spots
        deviceId = getOrCreateDeviceId()
        
        // Cleanup expired spots on startup
        repository.cleanupExpiredSpots()
    }
    
    private fun getOrCreateDeviceId(): String {
        val prefs = getSharedPreferences("ParkHere", MODE_PRIVATE)
        var id = prefs.getString(PREF_DEVICE_ID, null)
        if (id == null) {
            // Try to use Android ID first, fallback to UUID
            id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: UUID.randomUUID().toString()
            prefs.edit().putString(PREF_DEVICE_ID, id).apply()
        }
        return id
    }
    
    private fun initViews() {
        fabAddSpot = findViewById(R.id.fabAddSpot)
        fabMyLocation = findViewById(R.id.fabMyLocation)
        fabAddPhoto = findViewById(R.id.fabAddPhoto)
        bottomSheet = findViewById(R.id.bottomSheet)
        tvSpotCount = findViewById(R.id.tvSpotCount)
        tvSpotTitle = findViewById(R.id.tvSpotTitle)
        tvSpotTime = findViewById(R.id.tvSpotTime)
        ivSpotPhoto = findViewById(R.id.ivSpotPhoto)
        btnMarkTaken = findViewById(R.id.btnMarkTaken)
        btnNavigate = findViewById(R.id.btnNavigate)
        
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }
    
    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    
    private fun setupClickListeners() {
        fabAddSpot.setOnClickListener {
            if (isAddingSpot) {
                // Confirm and save the spot
                confirmAndSaveSpot()
            } else {
                // Start add spot mode
                startAddSpotMode()
            }
        }
        
        fabMyLocation.setOnClickListener {
            if (isAddingSpot) {
                // Cancel add mode
                cancelAddSpotMode()
            } else {
                moveToCurrentLocation()
            }
        }
        
        fabAddPhoto.setOnClickListener {
            showPhotoOptions()
        }
        
        btnMarkTaken.setOnClickListener {
            selectedSpot?.let { spot ->
                markSpotAsTaken(spot)
            }
        }
        
        btnNavigate.setOnClickListener {
            selectedSpot?.let { spot ->
                navigateToSpot(spot)
            }
        }
    }
    
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        
        // Map settings
        map.mapType = GoogleMap.MAP_TYPE_SATELLITE
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = true
            isMapToolbarEnabled = false
        }
        
        // Marker click listener
        map.setOnMarkerClickListener { marker ->
            if (isAddingSpot && marker == placementMarker) {
                // Show photo options when clicking placement marker
                showPhotoOptions()
                return@setOnMarkerClickListener true
            }
            
            val spotId = marker.tag as? String
            spotId?.let { id ->
                spots[id]?.let { spot ->
                    showSpotDetails(spot)
                }
            }
            true
        }
        
        // Marker drag listener for placement
        map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {}
            
            override fun onMarkerDrag(marker: Marker) {
                // Do nothing while dragging, moving it here interrupts the user's drag gesture
            }
            
            override fun onMarkerDragEnd(marker: Marker) {
                if (marker == placementMarker) {
                    val distance = calculateDistance(currentLocation!!, marker.position)
                    if (distance > MAX_DISTANCE_METERS) {
                        val constrainedPos = constrainToRadius(currentLocation!!, marker.position)
                        marker.position = constrainedPos
                        Toast.makeText(this@MainActivity, "Spot must be within 100m of your location", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
        
        // Map long click to drop a pin
        map.setOnMapLongClickListener { latLng ->
            if (!isAddingSpot) {
                startAddSpotMode(latLng)
            } else {
                currentLocation?.let { current ->
                    val distance = calculateDistance(current, latLng)
                    if (distance <= MAX_DISTANCE_METERS) {
                        placementMarker?.position = latLng
                    } else {
                        Toast.makeText(this@MainActivity, "Spot must be within 100m of your location", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        // Map click to hide bottom sheet or handle add mode
        map.setOnMapClickListener { latLng ->
            if (isAddingSpot) {
                // Move placement marker to clicked location if within radius
                currentLocation?.let { current ->
                    val distance = calculateDistance(current, latLng)
                    if (distance <= MAX_DISTANCE_METERS) {
                        placementMarker?.position = latLng
                    } else {
                        Toast.makeText(this, "Spot must be within 100m of your location", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
        
        // Request location permission
        checkLocationPermission()
        
        // Load parking spots
        loadParkingSpots()
    }
    
    private fun startAddSpotMode(initialLatLng: LatLng? = null) {
        if (currentLocation == null) {
            Toast.makeText(this, "Finding current location... Please wait.", Toast.LENGTH_SHORT).show()
            moveToCurrentLocation()
            return
        }
        
        Toast.makeText(this, "Checking limits...", Toast.LENGTH_SHORT).show()
        
        // Fast local check avoids network hanging
        val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        val activeSpots = spots.values.count { it.addedBy == deviceId && it.addedAt > oneHourAgo }
        val canAdd = activeSpots < 3
        val remaining = 3 - activeSpots

        if (!canAdd) {
            Toast.makeText(
                this,
                "You can only mark 3 spots per hour. Please wait.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Show remaining spots info
        if (remaining < 3) {
            Toast.makeText(
                this,
                "You can mark $remaining more spot(s) this hour",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        enterAddSpotMode(initialLatLng)
    }
    
    private fun enterAddSpotMode(initialLatLng: LatLng? = null) {
        isAddingSpot = true
        capturedPhotoBase64 = null
        
        // Change FAB text, icon, and color to green for Save mode
        fabAddSpot.setIconResource(android.R.drawable.ic_menu_save)
        fabAddSpot.text = "Save"
        fabAddSpot.contentDescription = "Save"
        fabAddSpot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        
        fabMyLocation.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        fabAddPhoto.visibility = View.VISIBLE
        fabAddPhoto.setImageResource(android.R.drawable.ic_menu_camera)
        
        // Show info
        tvSpotCount.text = "Tap anywhere on the map to place the marker (within 100m)"
        
        // Draw radius circle
        radiusCircle = map.addCircle(
            CircleOptions()
                .center(currentLocation!!)
                .radius(MAX_DISTANCE_METERS)
                .strokeColor(Color.argb(150, 76, 175, 80))
                .fillColor(Color.argb(50, 76, 175, 80))
                .strokeWidth(3f)
        )
        
        // Add draggable placement marker
        val targetPos = if (initialLatLng != null) {
            val dist = calculateDistance(currentLocation!!, initialLatLng)
            if (dist <= MAX_DISTANCE_METERS) initialLatLng else constrainToRadius(currentLocation!!, initialLatLng)
        } else {
            currentLocation!!
        }
        
        placementMarker = map.addMarker(
            MarkerOptions()
                .position(targetPos)
                .title("New Parking Spot")
                .snippet("Drag to position, tap to add photo")
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )
        placementMarker?.showInfoWindow()
        
        Toast.makeText(this, "Drag the blue marker to the parking spot location", Toast.LENGTH_LONG).show()
    }
    
    private fun cancelAddSpotMode() {
        isAddingSpot = false
        capturedPhotoBase64 = null
        
        // Restore FAB text, icon, and original accent color
        fabAddSpot.setIconResource(R.drawable.ic_add_location)
        fabAddSpot.text = "Park"
        fabAddSpot.contentDescription = getString(R.string.add_spot)
        fabAddSpot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent)
        
        fabMyLocation.setImageResource(R.drawable.ic_my_location)
        fabAddPhoto.visibility = View.GONE
        
        // Remove placement marker and circle
        placementMarker?.remove()
        placementMarker = null
        radiusCircle?.remove()
        radiusCircle = null
        
        updateSpotCount()
    }
    
    private fun confirmAndSaveSpot() {
        val markerPosition = placementMarker?.position ?: return
        
        // Show dialog to confirm and optionally add photo
        AlertDialog.Builder(this)
            .setTitle("Add Parking Spot")
            .setMessage("Save this parking spot location?${if (capturedPhotoBase64 != null) "\n\n📷 Photo attached" else "\n\nNo photo attached"}")
            .setPositiveButton("Save") { _, _ ->
                saveSpot(markerPosition)
            }
            .setNeutralButton("Add Photo") { _, _ ->
                showPhotoOptions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPhotoOptions() {
        AlertDialog.Builder(this)
            .setTitle("Add Photo")
            .setItems(arrayOf("📷 Take Photo", "🖼️ Choose from Gallery")) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }
    
    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionRequest.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun launchCamera() {
        photoFile = File.createTempFile("parking_", ".jpg", cacheDir)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile!!)
        cameraLauncher.launch(uri)
    }
    
    private fun saveSpot(position: LatLng) {
        // Enforce 5m gap between pins
        val isTooCloseToExistingSpot = spots.values.any { spot ->
            val existingPos = LatLng(spot.latitude, spot.longitude)
            val dist = calculateDistance(position, existingPos)
            dist < 5.0
        }
        
        if (isTooCloseToExistingSpot) {
            Toast.makeText(this, "Spot is already marked. Must be at least 5m away from existing spots.", Toast.LENGTH_LONG).show()
            return
        }

        val spot = ParkingSpot(
            latitude = position.latitude,
            longitude = position.longitude,
            addedBy = deviceId, // Use device ID to track user
            addedAt = System.currentTimeMillis(),
            imageBase64 = capturedPhotoBase64 ?: ""
        )
        
        Toast.makeText(this, getString(R.string.spot_added) + " (expires in 1 hour)", Toast.LENGTH_LONG).show()
        cancelAddSpotMode()
        
        repository.addSpot(
            spot,
            onSuccess = {
                // Background sync successful
            },
            onError = { e ->
                Toast.makeText(this, "Sync Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun calculateDistance(from: LatLng, to: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0].toDouble()
    }
    
    private fun constrainToRadius(center: LatLng, point: LatLng): LatLng {
        val distance = calculateDistance(center, point)
        if (distance <= MAX_DISTANCE_METERS) return point
        
        // Calculate unit vector and scale to max distance
        val ratio = MAX_DISTANCE_METERS / distance
        val lat = center.latitude + (point.latitude - center.latitude) * ratio
        val lng = center.longitude + (point.longitude - center.longitude) * ratio
        return LatLng(lat, lng)
    }
    
    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                enableMyLocation()
            }
            else -> {
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }
    
    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false
            
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 20f))
                }
            }
        }
    }
    
    private fun moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation!!, 20f))
                }
            }
        } else {
            checkLocationPermission()
        }
    }
    
    private fun loadParkingSpots() {
        repository.observeSpots(
            onSpotAdded = { spot ->
                runOnUiThread {
                    addSpotMarker(spot)
                    updateSpotCount()
                }
            },
            onSpotRemoved = { spotId ->
                runOnUiThread {
                    removeSpotMarker(spotId)
                    updateSpotCount()
                    if (selectedSpot?.id == spotId) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, "Error loading spots: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun addSpotMarker(spot: ParkingSpot) {
        spots[spot.id] = spot
        
        val position = LatLng(spot.latitude, spot.longitude)
        val marker = map.addMarker(
            MarkerOptions()
                .position(position)
                .title(getString(R.string.spot_available))
                .icon(getGreenMarkerIcon())
        )
        marker?.tag = spot.id
        marker?.let { markers[spot.id] = it }
    }
    
    private fun removeSpotMarker(spotId: String) {
        markers[spotId]?.remove()
        markers.remove(spotId)
        spots.remove(spotId)
    }
    
    private fun updateSpotCount() {
        val count = spots.size
        tvSpotCount.text = when (count) {
            0 -> "No spots available nearby"
            1 -> "1 spot available"
            else -> "$count spots available"
        }
    }
    
    private fun showSpotDetails(spot: ParkingSpot) {
        selectedSpot = spot
        tvSpotTitle.text = getString(R.string.spot_available)
        tvSpotTime.text = "Added ${spot.getTimeAgo()}"
        
        if (spot.hasImage()) {
            try {
                val bytes = Base64.decode(spot.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ivSpotPhoto.setImageBitmap(bitmap)
                ivSpotPhoto.visibility = View.VISIBLE
            } catch (e: Exception) {
                ivSpotPhoto.visibility = View.GONE
            }
        } else {
            ivSpotPhoto.visibility = View.GONE
        }
        
        bottomSheet.visibility = View.VISIBLE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
    
    private fun markSpotAsTaken(spot: ParkingSpot) {
        Toast.makeText(this, getString(R.string.spot_removed), Toast.LENGTH_SHORT).show()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        repository.removeSpot(
            spot.id,
            onSuccess = {
                // Background sync successful
            },
            onError = { e ->
                Toast.makeText(this, "Sync Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun navigateToSpot(spot: ParkingSpot) {
        val uri = Uri.parse("google.navigation:q=${spot.latitude},${spot.longitude}&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${spot.latitude},${spot.longitude}")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }
    
    private fun getGreenMarkerIcon(): BitmapDescriptor {
        return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
    }
    
    override fun onBackPressed() {
        if (isAddingSpot) {
            cancelAddSpotMode()
        } else {
            super.onBackPressed()
        }
    }
}

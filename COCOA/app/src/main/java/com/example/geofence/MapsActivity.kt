package com.example.geofence


import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import java.io.IOException

class MapsActivity : AppCompatActivity(), OnMapReadyCallback,GoogleMap.OnMarkerClickListener {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var locationUpdateState = false

    lateinit var geofencingClient: GeofencingClient
    private var radius = 10000
    var permissionStatus = false

    //private lateinit var mainViewModel: ContainmentZones

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val PATTERN_GAP_LENGTH_PX = 20
        private const val REQUEST_CHECK_SETTINGS = 2
        private const val GEOFENCE_EXPIRATION_IN_MILLISECONDS:Long = 600000
        const val TAG = "Error"
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        title = "Covid Containment Alert"
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
            //Toast.makeText(this,"Oncreate",Toast.LENGTH_LONG).show()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        var prev_location: LatLng? = null
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                lastLocation = p0.lastLocation

                if(prev_location==null || prev_location != LatLng(lastLocation.latitude, lastLocation.longitude)){
                    var current_coordinates = LatLng(lastLocation.latitude,lastLocation.longitude)
                    prev_location = LatLng(current_coordinates.latitude, current_coordinates.longitude)
                    var mainViewModel = ContainmentZones()
                    var nearby_zones_address = ArrayList<String>()
                    mainViewModel.RequestZones(current_coordinates)


                    mainViewModel.liveData.observe(this@MapsActivity, Observer { liveData->
                        liveData?.let {
                            //nearby_zones_address.addAll(it.containmentZoneNames)
                            if(it.containmentZoneNames.size>0){
                                nearby_zones_address.addAll(mainViewModel.ZoneAddresses)
                                if(nearby_zones_address.size>0){
                                    val nearby_zones_coordinates: ArrayList<LatLng>? = GetCoordinates(nearby_zones_address)
                                    geofencingClient = LocationServices.getGeofencingClient(this@MapsActivity)
                                    if (nearby_zones_coordinates != null) {
                                        CreateGeofence(nearby_zones_coordinates)
                                    }
                                }

                                //Toast.makeText(this@MapsActivity,"Containment Zones Nearby!!",Toast.LENGTH_LONG).show()
                            }
                            else{
                                Toast.makeText(this@MapsActivity,"No Containment Zones Nearby",Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                }




            }
        }
        createLocationRequest()

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.getUiSettings().setZoomControlsEnabled(true)
        map.setOnMarkerClickListener(this)
        while(permissionStatus==false){
            setUpMap()
        }

    }

    private fun setUpMap() {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        permissionStatus=true
        map.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            // Got last known location. In some rare situations this can be null.
            // 3
            if (location != null) {
                lastLocation = location
                var coordinates = LatLng(location.latitude, location.longitude)


                placeMarkerOnMap(coordinates)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinates, 12f))

            }

        }

    }

    private fun placeMarkerOnMap(location: LatLng) {

        // 1
        val markerOptions = MarkerOptions().position(location)
        // 2
        val titleStr = getAddress(location)
        markerOptions.title(titleStr)

        map.addMarker(markerOptions)

    }

    private fun getAddress(latLng: LatLng): String {
        // 1
        val geocoder = Geocoder(this)
        val addresses: List<Address>?
        val address: Address?
        var addressText = ""

        try {
            // 2
            addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            // 3
            if (null != addresses && !addresses.isEmpty()) {
                address = addresses[0]
                for (i in 0 until address.maxAddressLineIndex) {
                    addressText += if (i == 0) address.getAddressLine(i) else "\n" + address.getAddressLine(i)
                }
            }
        } catch (e: IOException) {
            Log.e("MapsActivity", e.localizedMessage)
        }

        return addressText
    }

    private fun startLocationUpdates() {
        //1

        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        //2
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null /* Looper */)
    }

    private fun createLocationRequest() {
        // 1
        locationRequest = LocationRequest()
        // 2
        locationRequest.interval = 10000
        // 3
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        // 4
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        // 5
        task.addOnSuccessListener {
            locationUpdateState = true
            startLocationUpdates()
        }
        task.addOnFailureListener { e ->
            // 6
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(this@MapsActivity,
                        REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    // 1
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                locationUpdateState = true
                startLocationUpdates()
            }
        }
    }

    // 2
    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // 3
    public override fun onResume() {
        super.onResume()
        if (!locationUpdateState) {
            startLocationUpdates()
        }
    }

    override fun onMarkerClick(p0: Marker?)=false


    fun GetCoordinates(zones_address: ArrayList<String>): ArrayList<LatLng>? {
        if(zones_address.size==0)
            return null
        val geocoder = Geocoder(this)
        val latLngList: ArrayList<LatLng> = ArrayList<LatLng>()
        val zone_length  = zones_address.size-1
        for (i in 0..zone_length){
            try{
                val coordinates = geocoder.getFromLocationName(zones_address.get(i),1)
                val lat = coordinates.get(0).getLatitude()
                val lng = coordinates.get(0).getLongitude()
                val curr_zone = LatLng(lat,lng)
                latLngList.add(curr_zone)
            }catch (e:Exception){
                Toast.makeText(this,"Geocoding Error",Toast.LENGTH_LONG)
            }

        }
        return latLngList
    }


    // Create a Geofence object to define the circular geofence
    @SuppressLint("ShowToast")
    private fun CreateGeofence(nearbyZonesCoordinates:ArrayList<LatLng>){
        val geofenceList = ArrayList<Geofence>()  //Geofence list
        val numberOfZones = nearbyZonesCoordinates.size-1
        for(i in 0..numberOfZones){
            val geofence = Geofence.Builder()
                .setRequestId("geofenceId")
                .setCircularRegion(
                    nearbyZonesCoordinates.get(i).latitude,
                    nearbyZonesCoordinates.get(i).longitude,
                    radius.toFloat()
                ).setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
            geofenceList.add(geofence)
        }

        // Creating a GeofenceRequest instance that contains a list of geofences (in our case, only one geofence)
        val geofencingRequest = GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()

        // Creating the pending intent which will handle GeofenceEvents when users enter/leave in the geofence area
        val intent = Intent(this, GeofenceTransitionsIntentService::class.java)
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)


        geofencingClient?.addGeofences(geofencingRequest, pendingIntent)?.addOnSuccessListener {
            val DOT: PatternItem = Dot()
            val patternItem = ArrayList<PatternItem>()
            val GAP: PatternItem = Gap(PATTERN_GAP_LENGTH_PX.toFloat())
            patternItem.add(DOT)
            patternItem.add(GAP)
            for(i in 0..numberOfZones){
                val circle = map.addCircle(
                    CircleOptions()
                        .center(nearbyZonesCoordinates.get(i))
                        .radius(1000.0)
                        .strokeColor(Color.BLUE)
                        .strokePattern(patternItem)
                        .fillColor(0x30ff0000)
                )
            }
            Toast.makeText(this,"Geofences made",Toast.LENGTH_SHORT).show()
            Toast.makeText(this,"Containment Zones Nearby!!",Toast.LENGTH_LONG).show()
        }
            ?.addOnFailureListener {
                Toast.makeText(this,"Geofences failed",Toast.LENGTH_SHORT).show()

            }
    }

    class GeofenceTransitionsIntentService : IntentService(TAG) {

        override fun onHandleIntent(intent: Intent?) {
            // Extract the geofence event from the intent
            val geofencingEvent = GeofencingEvent.fromIntent(intent)

            // Check for any error before using it
            if (geofencingEvent.hasError()) {
                val error = GooglePlayServicesUtil.getErrorString(geofencingEvent.errorCode)
                return
            }

            // Getting the transition
            val geofenceTransition = geofencingEvent.geofenceTransition

            when (geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_EXIT -> {

                    // Get the geofence, ...
                    val triggeringGeofences = geofencingEvent.triggeringGeofences
                    Toast.makeText(this, "Red Zone Entry Trigger",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

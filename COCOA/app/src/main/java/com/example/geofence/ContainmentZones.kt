package com.example.geofence

import android.app.PendingIntent.getActivity
import android.location.Geocoder
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.itkacher.okhttpprofiler.OkHttpProfilerInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*
import java.net.URL


class ContainmentZones: ViewModel() {
    companion object {
        // BASE URL FOR GEOIQ API REQUEST
        private const val BASE_URL = "https://data.geoiq.io/dataapis/v1.0/covid/"
    }

    var ZoneAddresses = ArrayList<String>()

    //Data class for API response json object
    data class geoIQLocationResponse(
        val containmentZoneNames: ArrayList<String>,
        val containmentsAvailability: Boolean,
        val numberOfNearbyZones: Int,
        val status: Int
    )

    // Class for API Request
    class geoIQAPIRequest(longitude: Double, latitude: Double) {
        // API key ( MAX 200 Request per day)
        val key: String =
            "INSERT YOUR API KEY HERE"
        var lng: Double
        var lat: Double
        val radius: Int = 5000

        init {
            this.lat = latitude
            this.lng = longitude
        }
    }

    // Interface for API call
    interface geoIQAPIService {
        @Headers("Content-Type: application/json")
        @POST("nearbyzones")
        suspend fun GetNearbyZones(@Body body: geoIQAPIRequest):
                geoIQLocationResponse
    }
    //LiveData for API Response values
    val liveData = MutableLiveData<geoIQLocationResponse>()

    //Main API Request function
    public fun RequestZones(target: LatLng) {

        val builder = OkHttpClient.Builder()
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(OkHttpProfilerInterceptor())
        }
        val client = builder.build()

        val retrofit = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create())
            .baseUrl(BASE_URL)
            .client(client)
            .build()

        // Request Coroutine - Synchronous Thread Job
        val coroutine_job = CoroutineScope(Dispatchers.IO).launch {
            val temp = geoIQAPIRequest(target.longitude, target.latitude)
            val geoIQAPI: geoIQAPIService = retrofit.create(geoIQAPIService::class.java)
            var API_response: geoIQLocationResponse? = null
            API_response = geoIQAPI?.GetNearbyZones(temp)
            //Thread.sleep(1000)
            withContext(Dispatchers.Main) {
                if (API_response!!.status == 200 && API_response!!.containmentZoneNames.size > 0) {
                    //Got nearby containment zones
                    ZoneAddresses = API_response!!.containmentZoneNames
                    liveData.value = API_response
                }
            }
        }

    }
}

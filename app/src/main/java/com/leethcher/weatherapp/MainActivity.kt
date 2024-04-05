package com.leethcher.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.leethcher.weatherapp.databinding.ActivityMainBinding
import com.leethcher.weatherapp.models.WeatherResponse
import com.leethcher.weatherapp.network.WeatherService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var nowLatLng: LatLng? = null

    private var customProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if(!isLocationEnabled()){
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()
            
            // 설정 화면에서 사용자가 직접 설정을 바꾸도록 함
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object: MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }

                        if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?
                    ) {
                        showRationalDialogForPermission()
                    }
                }).onSameThread()
                .check()
        }

        val swipe = binding.swipe
        swipe.setOnRefreshListener {
            requestLocationData()
            swipe.isRefreshing = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        mFusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                nowLatLng = LatLng(loc.latitude, loc.longitude)
                getLocationWeatherDetials(loc.latitude, loc.longitude)
            }
    }

    private fun isLocationEnabled(): Boolean {
        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun getLocationWeatherDetials(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
            // retrofit 객체 생성
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)


            // 데이터 요청
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showProgressDialg()

            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    cancelProgressDialg()
                    if(response.isSuccessful){
                        val weatherList: WeatherResponse = response.body()!!
                        Log.i("Response Result", "$weatherList")
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI(weatherList)
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    cancelProgressDialg()
                    Log.e("Error", t.message.toString())
                }
            })
        }else{
            Toast.makeText(
                this@MainActivity,
                "No internet connection available.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRationalDialogForPermission(){
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton("Go TO SETTINGS"){_, _ ->
                try{
                    // 앱의 자세한 설정 화면으로 이동
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    // 해당 앱의 경로 설정
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: Exception){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel"){ dialog, _ ->
                dialog.dismiss()
            }
    }

    private fun showProgressDialg(){
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.activity_custom_progress_dialog)
        customProgressDialog?.setCanceledOnTouchOutside(false)
        customProgressDialog?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        customProgressDialog?.show()
    }

    private fun cancelProgressDialg(){
        if(customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "default")
        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for(z in weatherList.weather.indices){
                Log.i("NAMEEEEEEEE", weatherList.weather[z].main)

                binding.tvMain.text = weatherList.weather[z].main
                binding.tvMainDescription.text = weatherList.weather[z].description
                binding.tvTemp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
                binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
                binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
                binding.tvSpeed.text = weatherList.wind.speed.toString()
                binding.tvName.text = weatherList.name
                binding.tvCountry.text = weatherList.sys.country
                binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise.toLong())
                binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset.toLong())

                // Here we update the main icon
                when (weatherList.weather[z].icon) {
                    "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                    "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                    "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                }
            }

        }
    }

    private fun getUnit(value: String): String?{
        var value = "˚C"
        if("US" == value || "LR" == value || "MM" == value){
            value = "˚F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val sdf =
            SimpleDateFormat("HH:mm:ss")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}
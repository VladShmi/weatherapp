package com.vladshmi.weatherapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vladshmi.weatherapp.ui.theme.WeatherAppTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.compose.ui.res.stringResource
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                getLocation()
            } else {
                showErrorAndExit("Permission denied, closing the app.")
            }
        }

        if (checkPermission()) {
            getLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            WeatherAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WeatherScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getLocation() {
        try {
            if (checkPermission()) {
                fusedLocationClient.lastLocation.addOnCompleteListener(this) { task ->
                    val location: Location? = task.result
                    if (location != null) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        val city = "Lat: $latitude, Lon: $longitude"
                        showLocation(city)
                    } else {
                        showErrorAndExit("Unable to get location, closing the app.")
                    }
                }
            } else {
                showErrorAndExit("Permission not granted, closing the app.")
            }
        } catch (e: SecurityException) {
            showErrorAndExit("SecurityException: ${e.message}, closing the app.")
        }
    }

    private fun showLocation(location: String) {
        Toast.makeText(this, "Location: $location", Toast.LENGTH_LONG).show()
    }

    private fun showErrorAndExit(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}

@Composable
fun WeatherScreen(modifier: Modifier = Modifier) {
    var city by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val cityName = stringResource(id = R.string.city_name)
    city = cityName

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "City: $city", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = R.string.temperature_label, temperature),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = R.string.description_label, description),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            getWeather(city, onSuccess = { weather ->
                temperature = "${weather.main.temp}°C"
                description = weather.weather.first().description
                errorMessage = ""
            }, onError = { message ->
                errorMessage = message
            })
        }) {
            Text(stringResource(id = R.string.get_weather_button))
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

fun getWeather(city: String, onSuccess: (WeatherResponse) -> Unit, onError: (String) -> Unit) {
    val apiKey = "TU_API_KEY"
    val urlString = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$apiKey&units=metric"

    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            val weatherResponse = parseWeatherResponse(response)
            onSuccess(weatherResponse)
        } else {
            onError("Error al obtener los datos: $responseCode")
        }
    } catch (e: Exception) {
        onError("Error: ${e.message}")
    }
}

fun parseWeatherResponse(response: String): WeatherResponse {
    val jsonObject = JSONObject(response)
    val main = jsonObject.getJSONObject("main")
    val weatherArray = jsonObject.getJSONArray("weather")
    val weather = weatherArray.getJSONObject(0)

    return WeatherResponse(
        main = Main(temp = main.getDouble("temp").toFloat()),
        weather = listOf(Weather(description = weather.getString("description"))),
        name = jsonObject.getString("name")
    )
}

@Preview(showBackground = true)
@Composable
fun WeatherScreenPreview() {
    WeatherAppTheme {
        WeatherScreen()
    }
}

data class WeatherResponse(
    val main: Main,
    val weather: List<Weather>,
    val name: String
)

data class Main(
    val temp: Float
)

data class Weather(
    val description: String
)
package com.vladshmi.weatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vladshmi.weatherapp.ui.theme.WeatherAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherAppTheme {
                WeatherApp()
            }
        }
    }
}

@Composable
fun WeatherApp() {
    var weatherResponse by remember { mutableStateOf<WeatherResponse?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    WeatherScreen(
        weatherResponse = weatherResponse,
        errorMessage = errorMessage,
        isLoading = isLoading,
        onFetchWeather = { cityName ->
            coroutineScope.launch {
                isLoading = true
                errorMessage = ""
                val result = fetchWeather(cityName)
                isLoading = false

                result.onSuccess { weather ->
                    weatherResponse = weather
                }.onFailure { error ->
                    errorMessage = error.message ?: "An error occurred."
                }
            }
        }
    )
}

@Composable
fun WeatherScreen(
    weatherResponse: WeatherResponse?,
    errorMessage: String,
    isLoading: Boolean,
    onFetchWeather: (String) -> Unit
) {
    var city by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("Enter City") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onFetchWeather(city) }, modifier = Modifier.fillMaxWidth()) {
            Text("Get Weather")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        }

        weatherResponse?.let { weather ->
            Text("City: ${weather.name}", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Temperature: ${weather.main.temp}°C", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Description: ${weather.weather.first().description}", style = MaterialTheme.typography.bodyMedium)
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

suspend fun fetchWeather(city: String): Result<WeatherResponse> = withContext(Dispatchers.IO) {
    val apiKey = "5326cefb303cfc2ed07fe00f63d7c7b0"
    val urlString = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$apiKey&units=metric"

    return@withContext try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val weatherResponse = parseWeatherResponse(response)
            Result.success(weatherResponse)
        } else {
            Result.failure(Exception("Error fetching weather: $responseCode"))
        }
    } catch (e: Exception) {
        Result.failure(e)
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
        WeatherScreen(
            weatherResponse = null,
            errorMessage = "",
            isLoading = false,
            onFetchWeather = {}
        )
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
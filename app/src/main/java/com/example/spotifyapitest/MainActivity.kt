package com.example.spotifyapitest


import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.spotifyapitest.googleapi.AnnotateImageRequest
import com.example.spotifyapitest.googleapi.Feature
import com.example.spotifyapitest.googleapi.GoogleApiService
import com.example.spotifyapitest.googleapi.ImageRequest
import com.example.spotifyapitest.googleapi.ImageSource
import com.example.spotifyapitest.googleapi.JsonRequest
import com.example.spotifyapitest.googleapi.WebDetectionResponse
import com.example.spotifyapitest.spotifyapi.BaseResponse
import com.example.spotifyapitest.spotifyapi.SpotifyApiService
import com.example.spotifyapitest.spotifyapi.Token
import com.example.spotifyapitest.spotifyapi.UserResponse
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class MainActivity : AppCompatActivity() {
    private lateinit var spotifyService: SpotifyApiService
    private lateinit var resulttext: TextView
    private lateinit var artisttext: TextView
    private lateinit var datetext: TextView
    private lateinit var edittext: EditText
    var doneSearchingForAlbum = false
    private val TOKEN_URL = "https://accounts.spotify.com/"
    private val SEARCH_URL = "https://api.spotify.com/"
    var ACCESS_TOKEN = ""


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        artisttext = findViewById(R.id.artistTextView)
        datetext = findViewById(R.id.dateTextView)
        resulttext = findViewById(R.id.textView)
        edittext = findViewById(R.id.input)


        val submitbutton: Button = findViewById(R.id.button1)


        // get access key - post request
        val retrofitForKey: Retrofit = Retrofit.Builder().baseUrl(TOKEN_URL)
            .addConverterFactory(GsonConverterFactory.create()).build()
        val service: SpotifyApiService = retrofitForKey.create(SpotifyApiService::class.java)

        val listCall: Call<Token> =
            service.getToken(
                "client_credentials",
                "2efa2b94335a4eebb74a9a8447d424a2", "e4bfd9cacf994057b5a0367d89252442"
            )

        listCall.enqueue(object : Callback<Token> {
            override fun onResponse(call: Call<Token>, response: Response<Token>) {
                if (response?.body() != null) {
                    ACCESS_TOKEN = response.body()!!.access_token.toString()
                }
                if (response?.body() == null) {
                }
            }

            override fun onFailure(call: Call<Token>, t: Throwable) {
                Log.e("Error", t.message.toString())
            }
        })


        // submit button
        submitbutton.setOnClickListener {
            executeApiChain()
        }


    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun executeApiChain() {
        lifecycleScope.launch {
            // Use async to start getAlbumGuesses coroutine
            val albumGuessesResult = getAlbumGuesses()

            // Now that getAlbumGuesses is done, call getSpotifyData with the result
            val resultDataList = getSpotifyData(albumGuessesResult, ACCESS_TOKEN, SEARCH_URL)
            resulttext.text = resultDataList[0]
            artisttext.text = resultDataList[1]
            datetext.text = resultDataList[2]


            // Now you can use resultDataList as needed
            Log.d("album info answer", resultDataList.toString())
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getAlbumGuesses(): MutableList<String> = suspendCoroutine { continuation ->
        var albumGuessList = mutableListOf<String>()

        var inputtest = "https://i.ebayimg.com/images/g/B6IAAOSwvEpkC7N~/s-l1600.jpg"


        var googleJsonOutput: Response<WebDetectionResponse>
        var GOOGLE_VISION_API_KEY = "AIzaSyAfg_EWVJUOWTERklWMn5yVMR90teKsO_o"
        var googleURL = "https://vision.googleapis.com/"




        var encodedString = ""


        var imageSource = ImageSource(inputtest)
        val jsonRequest = JsonRequest(
            requests = listOf(
                AnnotateImageRequest(
                    features = listOf(Feature(type = "WEB_DETECTION", maxResults = 4)),
                    image = ImageRequest(content = encodedString)
                )
            )
        )
        val json = Gson().toJson(jsonRequest)

        // Define the media type for JSON
        val mediaType = "application/json".toMediaTypeOrNull()

        // Create a RequestBody using the JSON string and media type
        val requestBody = json.toRequestBody(mediaType)

        // Make the POST request
        val retrofitForGoogle: Retrofit = Retrofit.Builder().baseUrl(googleURL)
            .addConverterFactory(GsonConverterFactory.create()).build()
        val googleService: GoogleApiService = retrofitForGoogle.create(GoogleApiService::class.java)

        val googleListCall: Call<WebDetectionResponse> =
            googleService.getGuess(requestBody, GOOGLE_VISION_API_KEY)

        googleListCall.enqueue(object : Callback<WebDetectionResponse> {
            override fun onResponse(
                call: Call<WebDetectionResponse>,
                response: Response<WebDetectionResponse>
            ) {
                if (response?.body() != null) {
                    Log.d("google JSON repsone", response.body().toString())
                    var entities = response.body()!!.responses[0].webDetection.webEntities
                    for (entity in entities) {
                        albumGuessList.add(entity.description)
                    }
                    Log.d("album guesses", albumGuessList.toString())
                    continuation.resume(albumGuessList)
                }
                if (response?.body() == null) {
                    Log.i("Response!", response.body().toString())
                    continuation.resume(albumGuessList) // Resume with an empty list if there's no response
                }
            }

            override fun onFailure(call: Call<WebDetectionResponse>, t: Throwable) {
                Log.e("Error", t.message.toString())
                continuation.resume(albumGuessList) // Resume with an empty list in case of failure
            }
        })
    }

    suspend fun getSpotifyData(
        albumGuesses: MutableList<String>,
        ACCESS_TOKEN: String,
        SEARCH_URL: String
    ): MutableList<String> = suspendCoroutine { continuation ->
        Log.d("album guesses for spot", albumGuesses.toString())
        var queryString = ""
        var resultDataList = mutableListOf<String>()
        var censoredWords = listOf(
            "Album", "Cover", "Vinyl", "USA", "Import",
            "LP", "CD", "Soundtrack", "Phonograph record", "German Import", "Studio album", "Rock", "Indie Rock", "Hip hop music"
            ,"Record Producer"
        )

        var counter = 0
        for (guess in albumGuesses) {
            if (guess !in censoredWords && counter < 2) {
                queryString += ("$guess ")
                counter++
            }
        }

        var accessInfo = StringBuilder()
        accessInfo.append("Bearer ")
        accessInfo.append(ACCESS_TOKEN)

        val retrofitForAlbum: Retrofit = Retrofit.Builder().baseUrl(SEARCH_URL)
            .addConverterFactory(GsonConverterFactory.create()).build()
        val albumService: SpotifyApiService = retrofitForAlbum.create(SpotifyApiService::class.java)


        if (queryString.length > 40){
            queryString = queryString.subSequence(0,40).toString()
        }
        Log.d("spotify query", queryString)
        val albumListCall: Call<BaseResponse<UserResponse>> =
            albumService.getAlbum(queryString, arrayOf("album"), "US", accessInfo.toString())

        albumListCall.enqueue(
            object : Callback<BaseResponse<UserResponse>> {
                override fun onResponse(
                    call: Call<BaseResponse<UserResponse>>,
                    response: Response<BaseResponse<UserResponse>>
                ) {
                    if (response?.body() != null) {
                            var i = 0
                            Log.d("spotify query URL", albumListCall.request().url.toString())
                            Log.d("spotify result", response.body().toString())
                            Log.d("index items[0]", response.body()!!.albums!!.items[i].toString())
                            resultDataList.add(response.body()!!.albums!!.items[i].name)
                            resultDataList.add(response.body()!!.albums!!.items[i].artists[i].name)
                            resultDataList.add(
                                response.body()!!.albums!!.items[i].release_date.subSequence(
                                    0,
                                    4
                                ).toString()
                            )
                            Log.d("album result!!", resultDataList.toString())
                            continuation.resume(resultDataList)
                        }

                    if (response?.body() == null) {
                        Log.i("Response!", "null response body")
                    }
                }

                override fun onFailure(call: Call<BaseResponse<UserResponse>>, t: Throwable) {
                    Log.e("Error", t.message.toString())
                }
            }
        )
    }
}



package com.vasilisneo.trackstar.data.api

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Single Retrofit instance for the whole app. Points at the production Spring Boot API —
// the same backend the iOS app uses. No auth-token interceptor yet (only the public
// /api/auth endpoints are wired so far); add one here when protected endpoints (profile,
// plans, sessions) get wired.
object NetworkClient {

    // Trailing slash required so relative @POST paths resolve under /api/.
    private const val BASE_URL = "https://api.trackstar.fitness/api/"

    val gson: Gson = Gson()

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
}

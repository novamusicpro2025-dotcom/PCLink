package com.pcmaster.mobile.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    
    private const val DEFAULT_TIMEOUT = 10_000L
    private const val UPLOAD_TIMEOUT = 30_000L
    
    private var retrofit: Retrofit? = null
    private var service: PcApiService? = null
    private var authToken: String = ""
    private var baseUrl: String = "http://192.168.1.100:8099"
    
    fun initialize(context: Context, baseUrl: String, authToken: String) {
        this.baseUrl = if (baseUrl.startsWith("http")) baseUrl else "http://$baseUrl"
        this.authToken = authToken
        
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(UPLOAD_TIMEOUT, TimeUnit.MILLISECONDS)
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor())
            .retryOnConnectionFailure(true)
            .build()
        
        val gson = GsonBuilder()
            .setLenient()
            .create()
        
        retrofit = Retrofit.Builder()
            .baseUrl(this.baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        service = retrofit!!.create(PcApiService::class.java)
    }
    
    fun getService(): PcApiService? = service
    
    fun updateAuthToken(newToken: String) {
        authToken = newToken
    }
    
    fun updateBaseUrl(newBaseUrl: String) {
        baseUrl = if (newBaseUrl.startsWith("http")) newBaseUrl else "http://$newBaseUrl"
        // Recreate retrofit with new base URL
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        
        val client = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(UPLOAD_TIMEOUT, TimeUnit.MILLISECONDS)
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor())
            .retryOnConnectionFailure(true)
            .build()
        
        val gson = GsonBuilder().setLenient().create()
        
        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        service = retrofit!!.create(PcApiService::class.java)
    }
    
    fun getBaseUrl(): String = baseUrl
    fun getAuthToken(): String = authToken
    
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val builder = original.newBuilder()
            
            if (authToken.isNotEmpty()) {
                builder.header("Authorization", "Bearer $authToken")
                builder.header("X-Auth-Token", authToken)
            }
            
            builder.header("User-Agent", "PC-Master-Android-App/2.1")
            
            return chain.proceed(builder.build())
        }
    }
}
package com.hoshco.abrikchat.Api

import com.hoshco.abrikchat.DataClasses.LoginRequest
import com.hoshco.abrikchat.DataClasses.LoginResponse
import com.hoshco.lawyers.Data.FcmResponse
import com.hoshco.lawyers.Data.FcmTokenRequest
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AbrikChatApi {
    @POST("AbrikChatAccount/AbrikChatLogin")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("Fcm/addFcmInfoAbrikChat")
    suspend fun addFcmInfo(
        @Header("Authorization") authHeader: String,
        @Body request: FcmTokenRequest
    ): Response<FcmResponse>
}

object ApiClient {
    private const val BASE_URL = "http://rdvs.abrik.cloud/api/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: AbrikChatApi by lazy {
        retrofit.create(AbrikChatApi::class.java)
    }
}
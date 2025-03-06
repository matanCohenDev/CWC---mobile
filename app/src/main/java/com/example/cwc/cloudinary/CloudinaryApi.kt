package com.example.cwc.cloudinary

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

// This interface defines the Cloudinary upload endpoint.
interface CloudinaryApi {

    // v1_1/{cloudName}/image/upload is the standard endpoint for image uploads.
    @Multipart
    @POST("v1_1/{cloudName}/image/upload")
    fun uploadImage(
        @Path("cloudName") cloudName: String,
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody
    ): Call<CloudinaryUploadResponse>
}

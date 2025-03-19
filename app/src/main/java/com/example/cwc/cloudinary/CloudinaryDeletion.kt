package com.example.cwc.cloudinary

import java.security.MessageDigest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun sha1(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val hashBytes = digest.digest(input.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) }
}


fun deleteImageFromCloudinary(
    publicId: String,
    apiKey: String,
    apiSecret: String,
    cloudName: String,
    onComplete: (Boolean) -> Unit
) {
    val timestamp = System.currentTimeMillis() / 1000

    val paramsToSign = "public_id=$publicId&timestamp=$timestamp"
    val toHash = paramsToSign + apiSecret
    val signature = sha1(toHash)

    CloudinaryService.api.deleteImage(
        cloudName = cloudName,
        publicId = publicId,
        timestamp = timestamp,
        signature = signature,
        apiKey = apiKey
    ).enqueue(object : Callback<CloudinaryUploadResponse> {
        override fun onResponse(call: Call<CloudinaryUploadResponse>, response: Response<CloudinaryUploadResponse>) {
            onComplete(response.isSuccessful)
        }
        override fun onFailure(call: Call<CloudinaryUploadResponse>, t: Throwable) {
            onComplete(false)
        }
    })
}

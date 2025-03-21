package com.example.cwc

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.cwc.R
import com.example.cwc.data.models.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.example.cwc.cloudinary.CloudinaryService
import com.example.cwc.cloudinary.CloudinaryUploadResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class UpdateImageActivity : AppCompatActivity() {

  private lateinit var ivPostImage: ImageView
  private lateinit var etDescription: EditText
  private lateinit var btnChooseImage: Button
  private lateinit var btnSave: Button
  private lateinit var btnCancel: Button

  private lateinit var post: Post
  private var selectedImageUri: Uri? = null

  private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      selectedImageUri = it
      ivPostImage.setImageURI(it)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.fragment_update_image)

    ivPostImage = findViewById(R.id.ivPostImage)
    etDescription = findViewById(R.id.etDescription)
    btnChooseImage = findViewById(R.id.btnChooseImage)
    btnSave = findViewById(R.id.btnSave)
    btnCancel = findViewById(R.id.btnCancel)

    post = intent.getSerializableExtra("post") as? Post ?: run {
      Toast.makeText(this, "No post data found", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    Glide.with(this)
      .load(post.image_path)
      .into(ivPostImage)

    etDescription.setText(post.description)

    btnChooseImage.setOnClickListener {
      pickImageLauncher.launch("image/*")
    }

    btnSave.setOnClickListener {
      val updatedDescription = etDescription.text.toString().trim()
      if (updatedDescription.isEmpty()) {
        etDescription.error = "Description cannot be empty"
        return@setOnClickListener
      }
      if (selectedImageUri != null) {
        uploadImageToCloudinary(selectedImageUri!!, onSuccess = { secureUrl ->
          updatePost(post.id, updatedDescription, secureUrl)
        }, onFailure = { errorMsg ->
          Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        })
      } else {
        updatePost(post.id, updatedDescription, null)
      }
    }

    btnCancel.setOnClickListener {
      finish()
    }
  }

  private fun saveImageLocally(uri: Uri): String? {
    return try {
      val inputStream = contentResolver.openInputStream(uri)
      inputStream?.let {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "CWCImages")
        if (!appDir.exists()) {
          appDir.mkdirs()
        }
        val file = File(appDir, "post_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        it.copyTo(outputStream)
        it.close()
        outputStream.close()
        file.absolutePath
      }
    } catch (e: Exception) {
      Log.e("UpdateImageActivity", "Failed to save image locally: ${e.message}")
      null
    }
  }

  private fun updatePost(postId: String, newDescription: String, newImagePath: String?) {
    val updates = mutableMapOf<String, Any>(
      "description" to newDescription
    )
    if (newImagePath != null) {
      updates["image_path"] = newImagePath
    }
    FirebaseFirestore.getInstance().collection("posts")
      .document(postId)
      .update(updates)
      .addOnSuccessListener {
        Toast.makeText(this, "Post updated", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK)
        finish()
      }
      .addOnFailureListener { e ->
        Toast.makeText(this, "Failed to update post: ${e.message}", Toast.LENGTH_SHORT).show()
      }
  }

  private fun uploadImageToCloudinary(
    imageUri: Uri,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit
  ) {
    val localImagePath = saveImageLocally(imageUri)
    if (localImagePath == null) {
      onFailure("Failed to save image locally")
      return
    }
    val file = File(localImagePath)
    val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
    val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

    val preset = "CWC - Content With Coffee"
    val presetRequestBody = RequestBody.create("text/plain".toMediaTypeOrNull(), preset)
    val call = CloudinaryService.api.uploadImage("dtdw1bmq4", filePart, presetRequestBody)
    call.enqueue(object : Callback<CloudinaryUploadResponse> {
      override fun onResponse(call: Call<CloudinaryUploadResponse>, response: Response<CloudinaryUploadResponse>) {
        if (response.isSuccessful) {
          val uploadResponse = response.body()
          if (uploadResponse?.secure_url != null) {
            onSuccess(uploadResponse.secure_url)
          } else {
            onFailure("Upload succeeded but no URL returned")
          }
        } else {
          onFailure("Upload failed: ${response.message()}")
        }
      }
      override fun onFailure(call: Call<CloudinaryUploadResponse>, t: Throwable) {
        onFailure("Upload failed: ${t.message}")
      }
    })
  }
}

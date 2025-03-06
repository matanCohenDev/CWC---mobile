package com.example.cwc

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
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
import java.io.InputStream

class EditProfileActivity : AppCompatActivity() {

  private lateinit var profileImageView: ImageView
  private lateinit var etFirstName: EditText
  private lateinit var etLastName: EditText
  private lateinit var etEmail: EditText
  private lateinit var etLocation: EditText
  private lateinit var btnSave: Button

  private val auth = FirebaseAuth.getInstance()
  private val db = FirebaseFirestore.getInstance()

  private var selectedImageUri: Uri? = null

  private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      selectedImageUri = it
      profileImageView.setImageURI(it) // Preview the selected image
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.fragment_edit_profile)
    supportActionBar?.apply {
      title = "Edit Profile"
      setDisplayHomeAsUpEnabled(true)
    }

    profileImageView = findViewById(R.id.edit_profile_image)
    etFirstName = findViewById(R.id.edit_et_firstname)
    etLastName = findViewById(R.id.edit_et_lastname)
    etEmail = findViewById(R.id.edit_et_email)
    etLocation = findViewById(R.id.edit_et_location)
    btnSave = findViewById(R.id.edit_btn_save)

    loadCurrentProfile()

    // Open gallery when profile image is clicked
    profileImageView.setOnClickListener {
      pickImageLauncher.launch("image/*")
    }

    // Save profile when button is clicked
    btnSave.setOnClickListener {
      saveProfile()
    }
  }

  private fun loadCurrentProfile() {
    val userId = auth.currentUser?.uid ?: return
    db.collection("users").document(userId).get()
      .addOnSuccessListener { document ->
        if (document.exists()) {
          etFirstName.setText(document.getString("firstname") ?: "")
          etLastName.setText(document.getString("lastname") ?: "")
          etEmail.setText(document.getString("email") ?: "")
          val city = document.getString("city") ?: ""
          val country = document.getString("country") ?: ""
          etLocation.setText(if (city.isNotEmpty() && country.isNotEmpty()) "$city, $country" else country)

          // Check for existing profile image
          val profileImageUrl = document.getString("profileImageUrl")
          if (!profileImageUrl.isNullOrEmpty()) {
            if (profileImageUrl.startsWith("http://") || profileImageUrl.startsWith("https://")) {
              // If it's a URL, load it with Picasso
              Picasso.get()
                .load(profileImageUrl)
                .placeholder(R.drawable.profile_foreground)
                .error(R.drawable.profile_foreground)
                .into(profileImageView)
            } else {
              // Otherwise, load from local file
              val bitmap = BitmapFactory.decodeFile(profileImageUrl)
              if (bitmap != null) {
                profileImageView.setImageBitmap(bitmap)
              } else {
                profileImageView.setImageResource(R.drawable.profile_foreground)
              }
            }
          } else {
            profileImageView.setImageResource(R.drawable.profile_foreground)
          }
        }
      }
      .addOnFailureListener { e ->
        Toast.makeText(this, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
      }
  }

  private fun saveProfile() {
    val userId = auth.currentUser?.uid ?: return
    val updatedFirstName = etFirstName.text.toString().trim()
    val updatedLastName = etLastName.text.toString().trim()
    val updatedEmail = etEmail.text.toString().trim()
    val updatedLocation = etLocation.text.toString().trim()
    val locationParts = updatedLocation.split(",").map { it.trim() }
    val updatedCity: String
    val updatedCountry: String
    if (locationParts.size == 1) {
      updatedCity = ""
      updatedCountry = locationParts[0]
    } else if (locationParts.size >= 2) {
      updatedCity = locationParts[0]
      updatedCountry = locationParts[1]
    } else {
      updatedCity = ""
      updatedCountry = ""
    }

    // Prepare the fields for updating
    val updates = hashMapOf(
      "firstname" to updatedFirstName,
      "lastname" to updatedLastName,
      "email" to updatedEmail,
      "city" to updatedCity,
      "country" to updatedCountry
    )

    // If an image is selected, upload it to Cloudinary
    if (selectedImageUri != null) {
      uploadImageToCloudinary(selectedImageUri!!, onSuccess = { secureUrl ->
        updates["profileImageUrl"] = secureUrl
        updateProfileInFirestore(userId, updates)
      }, onFailure = { errorMsg ->
        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
      })
    } else {
      updateProfileInFirestore(userId, updates)
    }
  }

  private fun updateProfileInFirestore(userId: String, updates: Map<String, Any>) {
    db.collection("users").document(userId)
      .update(updates)
      .addOnSuccessListener {
        Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
        finish()
      }
      .addOnFailureListener { e ->
        Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
      }
  }

  /**
   * Uploads the selected image to Cloudinary.
   * Uses the local copy of the image (via saveImageLocally) to create a File,
   * then sends it to Cloudinary using Retrofit.
   */
  private fun uploadImageToCloudinary(
    imageUri: Uri,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit
  ) {
    // Save image locally temporarily to get a File reference
    val localImagePath = saveImageLocally(imageUri)
    if (localImagePath == null) {
      onFailure("Failed to save image locally")
      return
    }
    val file = File(localImagePath)
    val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
    val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

    // Prepare the upload preset. Replace "your_upload_preset" with your actual preset.
    val preset = "CWC - Content With Coffee"
    val presetRequestBody = RequestBody.create("text/plain".toMediaTypeOrNull(), preset)

    // Replace "your_cloud_name" with your actual Cloudinary cloud name.
    val call = CloudinaryService.api.uploadImage("dtdw1bmq4", filePart, presetRequestBody)
    call.enqueue(object : Callback<CloudinaryUploadResponse> {
      override fun onResponse(
        call: Call<CloudinaryUploadResponse>,
        response: Response<CloudinaryUploadResponse>
      ) {
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

  // This method remains unchanged – it saves the image locally temporarily.
  private fun saveImageLocally(uri: Uri): String? {
    return try {
      val inputStream = contentResolver.openInputStream(uri)
      inputStream?.let {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, "CWCImages")
        if (!appDir.exists()) {
          appDir.mkdirs()
        }
        val file = File(appDir, "profile_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        it.copyTo(outputStream)
        it.close()
        outputStream.close()
        file.absolutePath
      }
    } catch (e: Exception) {
      Log.e("EditProfileActivity", "Failed to save image locally: ${e.message}")
      null
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when(item.itemId) {
      android.R.id.home -> {
        finish()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }
}

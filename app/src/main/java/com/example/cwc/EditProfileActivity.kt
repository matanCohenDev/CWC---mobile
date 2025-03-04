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
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso
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
      profileImageView.setImageURI(it) // תצוגה מקדימה של התמונה שנבחרה
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

    // לחיצה על תמונת הפרופיל לפתיחת הגלריה
    profileImageView.setOnClickListener {
      pickImageLauncher.launch("image/*")
    }

    // לחיצה על כפתור שמירה – מעדכן את הפרופיל
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

          // בדיקה והצגת תמונת פרופיל קיימת אם יש נתיב במסד הנתונים
          val profileImageUrl = document.getString("profileImageUrl")
          if (!profileImageUrl.isNullOrEmpty()) {
            if (profileImageUrl.startsWith("http://") || profileImageUrl.startsWith("https://")) {
              // אם זה URL מהאינטרנט – משתמשים ב-Picasso
              Picasso.get()
                .load(profileImageUrl)
                .placeholder(R.drawable.profile_foreground)
                .error(R.drawable.profile_foreground)
                .into(profileImageView)
            } else {
              // אם זה נתיב מקומי – טוענים את התמונה דרך BitmapFactory
              val bitmap = BitmapFactory.decodeFile(profileImageUrl)
              if (bitmap != null) {
                profileImageView.setImageBitmap(bitmap)
              } else {
                profileImageView.setImageResource(R.drawable.profile_foreground)
              }
            }
          } else {
            // במידה ואין נתיב, מציגים תמונת ברירת מחדל
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

    // הכנת השדות לעדכון
    val updates = hashMapOf(
      "firstname" to updatedFirstName,
      "lastname" to updatedLastName,
      "email" to updatedEmail,
      "city" to updatedCity,
      "country" to updatedCountry
    )

    // אם נבחרה תמונה, נשמור אותה לוקאלית ונוסיף את הנתיב לעדכון במסמך המשתמש
    if (selectedImageUri != null) {
      val localImagePath = saveImageLocally(selectedImageUri!!)
      if (localImagePath != null) {
        updates["profileImageUrl"] = localImagePath
      } else {
        Toast.makeText(this, "Failed to save image locally", Toast.LENGTH_SHORT).show()
        return
      }
    }

    updateProfileInFirestore(userId, updates)
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

  private fun uploadProfilePicture(imageUri: Uri, onComplete: () -> Unit) {
    val localImagePath = saveImageLocally(imageUri)
    if (localImagePath == null) {
      Toast.makeText(this, "Failed to save image locally", Toast.LENGTH_SHORT).show()
      return
    }
    // יצירת URI מהקובץ ששמור לוקאלית
    val localFileUri = Uri.fromFile(File(localImagePath))
    val storageRef = FirebaseStorage.getInstance().reference
    val userId = auth.currentUser?.uid ?: return
    val profileImageRef = storageRef.child("profile_pictures/$userId.jpg")
    profileImageRef.putFile(localFileUri)
      .addOnSuccessListener {
        profileImageRef.downloadUrl.addOnSuccessListener { downloadUri ->
          saveImageUrlToFirestore(downloadUri.toString(), onComplete)
        }
      }
      .addOnFailureListener {
        Toast.makeText(this, "Image upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
      }
  }


  private fun saveImageUrlToFirestore(imageUrl: String, onComplete: () -> Unit) {
    val userId = auth.currentUser?.uid ?: return
    db.collection("users").document(userId)
      .update("profileImageUrl", imageUrl)
      .addOnSuccessListener {
        Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
        onComplete()
      }
      .addOnFailureListener {
        Toast.makeText(this, "Failed to update profile picture: ${it.message}", Toast.LENGTH_SHORT).show()
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

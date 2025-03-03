package com.example.cwc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import java.io.File

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

    // קבלת נתוני הפוסט מהאינטנט
    post = intent.getSerializableExtra("post") as? Post ?: run {
      Toast.makeText(this, "No post data found", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    // טעינת תמונת הפוסט הנוכחית מהקובץ המקומי
    Glide.with(this)
      .load(File(post.image_path))
      .into(ivPostImage)

    // הצגת התיאור הקיים
    etDescription.setText(post.description)

    btnChooseImage.setOnClickListener {
      // פתיחת הגלריה לבחירת תמונה חדשה
      pickImageLauncher.launch("image/*")
    }

    btnSave.setOnClickListener {
      val updatedDescription = etDescription.text.toString().trim()
      if (updatedDescription.isEmpty()) {
        etDescription.error = "Description cannot be empty"
        return@setOnClickListener
      }
      if (selectedImageUri != null) {
        // אם נבחרה תמונה חדשה – שמירה לוקאלית ועדכון הנתיב במסד הנתונים
        val newImagePath = saveImageLocally(selectedImageUri!!)
        if (newImagePath != null) {
          updatePost(post.id, updatedDescription, newImagePath)
        } else {
          Toast.makeText(this, "Failed to save new image", Toast.LENGTH_SHORT).show()
        }
      } else {
        // עדכון רק התיאור
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
        val file = File(filesDir, "post_${System.currentTimeMillis()}.jpg")
        val outputStream = file.outputStream()
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
}

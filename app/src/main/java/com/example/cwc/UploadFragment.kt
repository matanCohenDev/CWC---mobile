package com.example.cwc

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class UploadFragment : Fragment() {
  private var selectedImageUri: Uri? = null
  private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
  private val auth: FirebaseAuth = FirebaseAuth.getInstance()

  private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      selectedImageUri = it
      Toast.makeText(requireContext(), "Image selected", Toast.LENGTH_SHORT).show()
    }
  }

  private fun openGallery() {
    pickImageLauncher.launch("image/*")
  }

  private fun saveImageLocally(uri: Uri): String? {
    try {
      val contentResolver: ContentResolver = requireContext().contentResolver
      val inputStream: InputStream? = contentResolver.openInputStream(uri)

      if (inputStream != null) {
        val file = File(requireContext().filesDir, "post_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        return file.absolutePath
      }
    } catch (e: Exception) {
      Log.e("UploadFragment", "Failed to save image locally: ${e.message}")
    }
    return null
  }

  private fun uploadPost(imageUri: Uri, description: String) {
    val userId = auth.currentUser?.uid ?: return

    val localImagePath = saveImageLocally(imageUri)
    if (localImagePath == null) {
      Toast.makeText(requireContext(), "Failed to save image locally", Toast.LENGTH_SHORT).show()
      return
    }

    savePostToFirestore(localImagePath, description)
  }

  private fun savePostToFirestore(imagePath: String, description: String) {
    val userId = auth.currentUser?.uid ?: return

    val post = hashMapOf(
      "user_id" to userId,
      "image_path" to imagePath,
      "description" to description,
      "timestamp" to System.currentTimeMillis(),
      "likes" to 0,
      "likedUsers" to emptyList<String>(),
      "comments" to emptyList<String>()
    )

    db.collection("posts").add(post)
      .addOnSuccessListener {
        Toast.makeText(requireContext(), "Post Shared!", Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
      }
      .addOnFailureListener {
        Log.e("UploadFragment", "Failed to share post: ${it.message}", it)
        Toast.makeText(requireContext(), "Failed to share post: ${it.message}", Toast.LENGTH_SHORT).show()
      }
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = inflater.inflate(R.layout.fragment_upload, container, false)

    setHasOptionsMenu(true)
    (activity as AppCompatActivity).supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      title = "Upload Post"
    }

    view.findViewById<Button>(R.id.upload_button).setOnClickListener {
      openGallery()
    }

    view.findViewById<Button>(R.id.share).setOnClickListener {
      val descriptionInput = view.findViewById<TextInputLayout>(R.id.description).editText?.text.toString()
      if (selectedImageUri == null) {
        Toast.makeText(requireContext(), "Please select an image first", Toast.LENGTH_SHORT).show()
      } else {
        uploadPost(selectedImageUri!!, descriptionInput)
      }
    }

    return view
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        findNavController().navigateUp()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(false)
  }
}

package com.example.cwc

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.cwc.adapters.ProfilePostAdapter
import com.example.cwc.data.local.AppDatabase
import com.example.cwc.data.models.Post
import com.example.cwc.data.repository.UserRepository
import com.example.cwc.viewmodel.UserViewModel
import com.example.cwc.viewmodel.UserViewModelFactory
import com.example.cwc.cloudinary.CloudinaryService
import com.example.cwc.cloudinary.CloudinaryUploadResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.squareup.picasso.Picasso
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment() {

  private lateinit var profilePostAdapter: ProfilePostAdapter
  private lateinit var recyclerView: RecyclerView
  private lateinit var swipeRefreshLayout: SwipeRefreshLayout
  private var postList = mutableListOf<Post>()

  private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
  private val auth: FirebaseAuth = FirebaseAuth.getInstance()

  // Registration for picking an image from the gallery
  private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      uploadProfilePicture(it)
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_profile, container, false)

    // Load user details and update UI
    loadUserDetails(view)

    // Setup bottom navigation fragment
    val childFragment = BottomNavFragment()
    val bundle = Bundle()
    bundle.putString("current_page", "profile")
    childFragment.arguments = bundle

    // Load profile picture using Picasso from the URL stored in Firestore
    val profileImage = view.findViewById<ImageView>(R.id.profile_picture)
    loadProfilePicture(profileImage)

    // When clicking the "edit profile picture" text, navigate to EditProfileActivity
    view.findViewById<View>(R.id.edit_profile_picture_text).setOnClickListener {
      val intent = android.content.Intent(requireContext(), EditProfileActivity::class.java)
      startActivity(intent)
    }

    // Initialize SwipeRefreshLayout
    swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout_profile)
    swipeRefreshLayout.setOnRefreshListener {
      fetchPosts()
      loadUserDetails(view)
    }

    // Initialize RecyclerView to display the user's posts
    recyclerView = view.findViewById(R.id.recycler_view_profile)
    recyclerView.layoutManager = LinearLayoutManager(requireContext())
    profilePostAdapter = ProfilePostAdapter(postList, requireContext())
    recyclerView.adapter = profilePostAdapter

    // Display bottom navigation fragment
    childFragmentManager.beginTransaction()
      .replace(R.id.navbar_container, childFragment)
      .commit()

    // Fetch the user's posts
    fetchPosts()

    return view
  }

  // Load user details from Firestore and update UI components
  private fun loadUserDetails(rootView: View) {
    val userId = auth.currentUser?.uid
    if (userId == null) {
      Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
      return
    }
    db.collection("users").document(userId)
      .get()
      .addOnSuccessListener { document ->
        if (document.exists()) {
          val firstName = document.getString("firstname") ?: "undefined"
          val lastName = document.getString("lastname") ?: "undefined"
          val city = document.getString("city") ?: "undefined"
          val country = document.getString("country") ?: "undefined"
          val email = document.getString("email") ?: "undefined"
          Log.d("ProfileFragment", "Fetched user details: firstname=$firstName, lastname=$lastName, city=$city, country=$country, email=$email")
          rootView.findViewById<TextView>(R.id.fullname_text)?.text = "$firstName $lastName"
          rootView.findViewById<TextView>(R.id.location_text)?.text = country
          rootView.findViewById<TextView>(R.id.email_text)?.text = email
        } else {
          Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_SHORT).show()
        }
      }
      .addOnFailureListener { e ->
        Toast.makeText(requireContext(), "Failed to load user details", Toast.LENGTH_SHORT).show()
        Log.e("ProfileFragment", "Error fetching user details", e)
      }
  }

  // Load profile picture using Picasso from the URL stored in Firestore
  private fun loadProfilePicture(profileImage: ImageView) {
    val userId = auth.currentUser?.uid ?: return
    FirebaseFirestore.getInstance().collection("users").document(userId)
      .get()
      .addOnSuccessListener { document ->
        if (document.exists()) {
          val profileImageUrl = document.getString("profileImageUrl")
          if (!profileImageUrl.isNullOrEmpty()) {
            if (profileImageUrl.startsWith("http://") || profileImageUrl.startsWith("https://")) {
              // Load image from URL with Picasso
              Picasso.get()
                .load(profileImageUrl)
                .placeholder(R.drawable.profile_foreground)
                .error(R.drawable.profile_foreground)
                .into(profileImage)
            } else {
              // Load image from local file path
              val bitmap = BitmapFactory.decodeFile(profileImageUrl)
              if (bitmap != null) {
                profileImage.setImageBitmap(bitmap)
              } else {
                profileImage.setImageResource(R.drawable.profile_foreground)
              }
            }
          }
        }
      }
      .addOnFailureListener {
        Log.e("ProfileFragment", "Failed to load profile image: ${it.message}")
      }
  }

  // Fetch the user's posts from Firestore
  private fun fetchPosts() {
    swipeRefreshLayout.isRefreshing = true

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    if (currentUserId == null) {
      Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
      swipeRefreshLayout.isRefreshing = false
      return
    }

    FirebaseFirestore.getInstance().collection("posts")
      .whereEqualTo("user_id", currentUserId)
      .orderBy("timestamp", Query.Direction.DESCENDING)
      .get()
      .addOnSuccessListener { documents ->
        Log.d("ProfileFragment", "Fetched posts count: ${documents.size()}")
        postList.clear()
        for (document in documents) {
          val post = document.toObject(Post::class.java)
          if (post.user_id == currentUserId) {
            postList.add(post)
          }
        }
        profilePostAdapter.notifyDataSetChanged()
        swipeRefreshLayout.isRefreshing = false
      }
      .addOnFailureListener { exception ->
        Log.e("ProfileFragment", "Error fetching posts", exception)
        Toast.makeText(requireContext(), "Failed to load posts", Toast.LENGTH_SHORT).show()
        swipeRefreshLayout.isRefreshing = false
      }
  }

  // Uploads the profile picture to Cloudinary and updates the user's Firestore document with the image URL
  fun uploadProfilePicture(imageUri: Uri) {
    uploadImageToCloudinary(imageUri, onSuccess = { secureUrl ->
      saveImageUrlToFirestore(secureUrl)
      Toast.makeText(requireContext(), "Profile Picture Uploaded!", Toast.LENGTH_SHORT).show()
    }, onFailure = { errorMsg ->
      Toast.makeText(requireContext(), "Upload Failed: $errorMsg", Toast.LENGTH_SHORT).show()
    })
  }

  // Updates Firestore with the new profile image URL
  fun saveImageUrlToFirestore(imageUrl: String) {
    val userId = auth.currentUser?.uid
    FirebaseFirestore.getInstance().collection("users").document(userId!!)
      .update("profileImageUrl", imageUrl)
      .addOnSuccessListener {
        Toast.makeText(requireContext(), "Profile Updated!", Toast.LENGTH_SHORT).show()
      }
      .addOnFailureListener {
        Toast.makeText(requireContext(), "Failed to update profile: ${it.message}", Toast.LENGTH_SHORT).show()
      }
  }

  // Uploads the selected image to Cloudinary using Retrofit.
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
    // Use your Cloudinary preset and cloud name (adjust these if necessary)
    val preset = "CWC - Content With Coffee"
    val presetRequestBody = RequestBody.create("text/plain".toMediaTypeOrNull(), preset)
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

  // Saves the selected image locally (temporary) to get a File reference.
  private fun saveImageLocally(uri: Uri): String? {
    return try {
      val inputStream = requireContext().contentResolver.openInputStream(uri)
      inputStream?.let {
        val picturesDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
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
      Log.e("ProfileFragment", "Failed to save image locally: ${e.message}")
      null
    }
  }
}

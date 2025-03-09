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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.cwc.adapters.ProfilePostAdapter
import com.example.cwc.cloudinary.CloudinaryService
import com.example.cwc.cloudinary.CloudinaryUploadResponse
import com.example.cwc.data.models.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.NetworkPolicy
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

  private lateinit var recyclerView: RecyclerView
  private lateinit var swipeRefreshLayout: SwipeRefreshLayout
  private lateinit var profilePostAdapter: ProfilePostAdapter

  private var postList = mutableListOf<Post>()
  private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
  private val auth: FirebaseAuth = FirebaseAuth.getInstance()

  // Real-time listener registration for the user doc
  private var userDocListener: ListenerRegistration? = null

  // Registration for picking an image from the gallery
  private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      uploadProfilePicture(it)
    }
  }
  override fun onResume() {
    super.onResume()
    fetchPosts() // Refresh posts when the fragment resumes
    attachUserSnapshotListener()
  }

  override fun onPause() {
    super.onPause()
    detachUserSnapshotListener()
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_profile, container, false)

    // Set up bottom navigation fragment
    val childFragment = BottomNavFragment()
    val bundle = Bundle()
    bundle.putString("current_page", "profile")
    childFragment.arguments = bundle
    childFragmentManager.beginTransaction()
      .replace(R.id.navbar_container, childFragment)
      .commit()

    // Click to edit profile picture -> Launch EditProfileActivity
    view.findViewById<View>(R.id.edit_profile_picture_text).setOnClickListener {
      val intent = android.content.Intent(requireContext(), EditProfileActivity::class.java)
      startActivity(intent)
    }

    // Initialize SwipeRefreshLayout
    swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout_profile)
    swipeRefreshLayout.setOnRefreshListener {
      fetchPosts() // Refresh the user's posts
    }

    // Initialize RecyclerView for user's posts
    recyclerView = view.findViewById(R.id.recycler_view_profile)
    recyclerView.layoutManager = LinearLayoutManager(requireContext())
    profilePostAdapter = ProfilePostAdapter(postList, requireContext())
    recyclerView.adapter = profilePostAdapter

    // Fetch the user's posts once on creation
    fetchPosts()

    return view
  }

  override fun onStart() {
    super.onStart()
    attachUserSnapshotListener()
  }

  override fun onStop() {
    super.onStop()
    detachUserSnapshotListener()
  }

  /**
   * Attaches a real-time listener to the current user's Firestore document.
   * Whenever the document changes (e.g., new profileImageUrl), the UI is updated immediately.
   */
  private fun attachUserSnapshotListener() {
    val userId = auth.currentUser?.uid ?: return
    userDocListener = db.collection("users").document(userId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Toast.makeText(requireContext(), "Error loading profile: ${error.message}", Toast.LENGTH_SHORT).show()
          return@addSnapshotListener
        }
        if (snapshot != null && snapshot.exists()) {
          val firstName = snapshot.getString("firstname") ?: "undefined"
          val lastName = snapshot.getString("lastname") ?: "undefined"
          val city = snapshot.getString("city") ?: "undefined"
          val country = snapshot.getString("country") ?: "undefined"
          val email = snapshot.getString("email") ?: "undefined"
          val profileImageUrl = snapshot.getString("profileImageUrl")

          Log.d("ProfileFragment", "Fetched user details: firstname=$firstName, lastname=$lastName, city=$city, country=$country, email=$email")

          // Update text fields
          view?.findViewById<TextView>(R.id.fullname_text)?.text = "$firstName $lastName"
          view?.findViewById<TextView>(R.id.location_text)?.text = country
          view?.findViewById<TextView>(R.id.email_text)?.text = email

          // Update profile image (with cache-busting if URL is available)
          val profileImage = view?.findViewById<ImageView>(R.id.profile_picture)
          if (!profileImageUrl.isNullOrEmpty()) {
            if (profileImageUrl.startsWith("http://") || profileImageUrl.startsWith("https://")) {
              // Append timestamp to bust the cache
              val finalUrl = "$profileImageUrl?ts=${System.currentTimeMillis()}"
              Picasso.get()
                .load(finalUrl)
                .placeholder(R.drawable.profile_foreground)
                .error(R.drawable.profile_foreground)
                .networkPolicy(NetworkPolicy.NO_CACHE, NetworkPolicy.NO_STORE)
                .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                .into(profileImage)
            } else {
              // If it's a local path, decode from file
              val bitmap = BitmapFactory.decodeFile(profileImageUrl)
              if (bitmap != null) {
                profileImage?.setImageBitmap(bitmap)
              } else {
                profileImage?.setImageResource(R.drawable.profile_foreground)
              }
            }
          } else {
            // No profile image set
            profileImage?.setImageResource(R.drawable.profile_foreground)
          }
        }
      }
  }

  /**
   * Detaches the snapshot listener when the fragment is not visible.
   */
  private fun detachUserSnapshotListener() {
    userDocListener?.remove()
    userDocListener = null
  }

  /**
   * Fetch the user's posts from Firestore (one-time).
   * If you want real-time posts, you can attach a snapshot listener here as well.
   */
  private fun fetchPosts() {
    swipeRefreshLayout.isRefreshing = true
    val currentUserId = auth.currentUser?.uid
    if (currentUserId == null) {
      Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
      swipeRefreshLayout.isRefreshing = false
      return
    }

    db.collection("posts")
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

  /**
   * Called when the user selects an image from the gallery. We upload it to Cloudinary
   * and then update Firestore with the new URL.
   */
  private fun uploadProfilePicture(imageUri: Uri) {
    uploadImageToCloudinary(imageUri, onSuccess = { secureUrl ->
      saveImageUrlToFirestore(secureUrl)
      Toast.makeText(requireContext(), "Profile Picture Uploaded!", Toast.LENGTH_SHORT).show()
    }, onFailure = { errorMsg ->
      Toast.makeText(requireContext(), "Upload Failed: $errorMsg", Toast.LENGTH_SHORT).show()
    })
  }

  /**
   * Update Firestore with the new profile image URL.
   */
  private fun saveImageUrlToFirestore(imageUrl: String) {
    val userId = auth.currentUser?.uid ?: return
    db.collection("users").document(userId)
      .update("profileImageUrl", imageUrl)
      .addOnSuccessListener {
        Toast.makeText(requireContext(), "Profile Updated!", Toast.LENGTH_SHORT).show()
      }
      .addOnFailureListener {
        Toast.makeText(requireContext(), "Failed to update profile: ${it.message}", Toast.LENGTH_SHORT).show()
      }
  }

  /**
   * Uploads the selected image to Cloudinary using Retrofit.
   */
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

    // Cloudinary preset and cloud name (adjust if needed)
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

  /**
   * Saves the selected image locally so we can get a File reference to upload.
   */
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

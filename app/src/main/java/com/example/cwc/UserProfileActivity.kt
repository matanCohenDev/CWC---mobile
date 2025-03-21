package com.example.cwc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.cwc.adapters.UserPostsAdapter
import com.example.cwc.data.models.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import de.hdodenhof.circleimageview.CircleImageView
import android.widget.TextView
import com.google.firebase.firestore.Query


class UserProfileActivity : AppCompatActivity() {

  private lateinit var ivProfile: CircleImageView
  private lateinit var tvUserName: TextView
  private lateinit var tvEmail: TextView
  private lateinit var tvLocation: TextView
  private lateinit var rvUserPosts: RecyclerView

  private val EDIT_PROFILE_REQUEST = 1001
  private var currentUserId: String = ""

  private var userDocListener: ListenerRegistration? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.fragment_user_profile)

    ivProfile = findViewById(R.id.ivProfile)
    tvUserName = findViewById(R.id.tvUserName)
    tvEmail = findViewById(R.id.tvEmail)
    tvLocation = findViewById(R.id.tvLocation)
    rvUserPosts = findViewById(R.id.rvUserPosts)

    currentUserId = intent.getStringExtra("userId") ?: ""
    if (currentUserId.isEmpty()) {
      Toast.makeText(this, "User ID not provided", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    loadUserPosts(currentUserId)
  }

  override fun onStart() {
    super.onStart()
    attachUserDocumentListener()
  }

  override fun onStop() {
    super.onStop()
    detachUserDocumentListener()
  }

  private fun attachUserDocumentListener() {
    userDocListener = FirebaseFirestore.getInstance()
      .collection("users")
      .document(currentUserId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          Toast.makeText(this, "Error loading profile: ${error.message}", Toast.LENGTH_SHORT).show()
          return@addSnapshotListener
        }
        if (snapshot != null && snapshot.exists()) {
          val firstName = snapshot.getString("firstname") ?: ""
          val lastName = snapshot.getString("lastname") ?: ""
          val email = snapshot.getString("email") ?: "No email"
          val city = snapshot.getString("city") ?: ""
          val country = snapshot.getString("country") ?: ""
          val profileImageUrl = snapshot.getString("profileImageUrl") ?: ""
          updateProfileUI(firstName, lastName, email, city, country, profileImageUrl)
        }
      }
  }

  private fun detachUserDocumentListener() {
    userDocListener?.remove()
    userDocListener = null
  }

  private fun updateProfileUI(
    firstName: String,
    lastName: String,
    email: String,
    city: String,
    country: String,
    profileImageUrl: String
  ) {
    tvUserName.text = "$firstName $lastName"
    tvEmail.text = email
    tvLocation.text = if (city.isNotEmpty() && country.isNotEmpty()) "$city, $country" else country

    if (profileImageUrl.isNotEmpty()) {
      updateProfileImage(profileImageUrl)
    } else {
      ivProfile.setImageResource(R.drawable.profile_foreground)
    }
  }

  private fun updateProfileImage(imageUrl: String) {
    val separator = if (imageUrl.contains("?")) "&" else "?"
    Glide.with(this)
      .load(imageUrl + separator + "ts=" + System.currentTimeMillis())
      .signature(ObjectKey(imageUrl + System.currentTimeMillis()))
      .diskCacheStrategy(DiskCacheStrategy.NONE)
      .skipMemoryCache(true)
      .placeholder(R.drawable.profile_foreground)
      .error(R.drawable.profile_foreground)
      .into(ivProfile)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == EDIT_PROFILE_REQUEST && resultCode == RESULT_OK) {
      val newImageUrl = data?.getStringExtra("profileImageUrl")
      Log.d("UserProfileActivity", "Received image URL from edit: $newImageUrl")
      if (!newImageUrl.isNullOrEmpty()) {
        Glide.with(this).clear(ivProfile)
        updateProfileImage(newImageUrl)
      }
    }
  }

  private fun loadUserPosts(userId: String) {
    FirebaseFirestore.getInstance().collection("posts")
      .whereEqualTo("user_id", userId)
      .orderBy("timestamp", Query.Direction.DESCENDING)
      .get()
      .addOnSuccessListener { documents ->
        val posts = mutableListOf<Post>()
        for (document in documents) {
          val post = document.toObject(Post::class.java)
          posts.add(post)
        }
        rvUserPosts.layoutManager = LinearLayoutManager(this)
        rvUserPosts.adapter = UserPostsAdapter(posts, this)
      }
      .addOnFailureListener { e ->
        Toast.makeText(this, "Failed to load user's posts: ${e.message}", Toast.LENGTH_SHORT).show()
      }
  }
}

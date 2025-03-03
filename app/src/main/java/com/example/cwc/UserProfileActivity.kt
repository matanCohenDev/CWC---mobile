package com.example.cwc

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cwc.R
import com.example.cwc.adapters.UserPostsAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import android.widget.TextView
import com.example.cwc.data.models.Post
import com.google.firebase.firestore.Query

class UserProfileActivity : AppCompatActivity() {

  private lateinit var ivProfile: CircleImageView
  private lateinit var tvUserName: TextView
  private lateinit var tvEmail: TextView
  private lateinit var tvLocation: TextView
  private lateinit var rvUserPosts: RecyclerView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.fragment_user_profile)

    ivProfile = findViewById(R.id.ivProfile)
    tvUserName = findViewById(R.id.tvUserName)
    tvEmail = findViewById(R.id.tvEmail)
    tvLocation = findViewById(R.id.tvLocation)
    rvUserPosts = findViewById(R.id.rvUserPosts)

    val userId = intent.getStringExtra("userId") ?: ""
    if (userId.isEmpty()) {
      Toast.makeText(this, "User ID not provided", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    loadUserProfile(userId)
    loadUserPosts(userId)
  }

  private fun loadUserProfile(userId: String) {
    FirebaseFirestore.getInstance().collection("users")
      .document(userId)
      .get()
      .addOnSuccessListener { document ->
        if (document.exists()) {
          val firstName = document.getString("firstname") ?: ""
          val lastName = document.getString("lastname") ?: ""
          val email = document.getString("email") ?: "No email"
          val city = document.getString("city") ?: ""
          val country = document.getString("country") ?: ""
          val profileImageUrl = document.getString("profileImageUrl") ?: ""

          tvUserName.text = "$firstName $lastName"
          tvEmail.text = email
          tvLocation.text = if (city.isNotEmpty() && country.isNotEmpty()) "$city, $country" else country

          if (profileImageUrl.isNotEmpty()) {
            val loadUrl = when {
              profileImageUrl.startsWith("http://") || profileImageUrl.startsWith("https://") -> profileImageUrl
              profileImageUrl.startsWith("file://") -> profileImageUrl
              else -> "file://$profileImageUrl"
            }
            Picasso.get()
              .load(loadUrl)
              .placeholder(R.drawable.profile_foreground)
              .error(R.drawable.profile_foreground)
              .into(ivProfile)
          } else {
            ivProfile.setImageResource(R.drawable.profile_foreground)
          }
        } else {
          Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
          finish()
        }
      }
      .addOnFailureListener { e ->
        Toast.makeText(this, "Failed to load user profile: ${e.message}", Toast.LENGTH_SHORT).show()
        finish()
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
        if (posts.isEmpty()) {
          Log.d("UserProfileActivity", "No posts found for user: $userId")
        }
        rvUserPosts.layoutManager = LinearLayoutManager(this)
        rvUserPosts.adapter = UserPostsAdapter(posts, this)
      }
      .addOnFailureListener { e ->
        Toast.makeText(this, "Failed to load user's posts: ${e.message}", Toast.LENGTH_SHORT).show()
      }
  }

}

package com.example.cwc

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.cwc.adapters.PostAdapter
import com.example.cwc.data.local.AppDatabase
import com.example.cwc.data.local.User
import com.example.cwc.data.models.Post
import com.example.cwc.data.repository.UserRepository
import com.example.cwc.viewmodel.UserViewModel
import com.example.cwc.viewmodel.UserViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

  private lateinit var userViewModel: UserViewModel
  private lateinit var recyclerView: RecyclerView
  private var postList = mutableListOf<Post>()
  private lateinit var postAdapter: PostAdapter
  private lateinit var swipeRefreshLayout: SwipeRefreshLayout


  fun renderNav(user: User) {
    Log.d("HomeFragment", "Rendering BottomNavFragment for user: ${user.firstname} ${user.lastname}")
    val childFragment = BottomNavFragment()
    val bundle = Bundle()
    bundle.putString("current_page", "home")
    bundle.putString("firstname", user.firstname)
    bundle.putString("lastname", user.lastname)
    childFragment.arguments = bundle
    parentFragmentManager.beginTransaction()
      .replace(R.id.navbar_container, childFragment)
      .commit()
  }


  private fun fetchPosts() {
    swipeRefreshLayout.isRefreshing = true
    FirebaseFirestore.getInstance().collection("posts")
      .orderBy("timestamp", Query.Direction.DESCENDING)
      .get()
      .addOnSuccessListener { documents ->
        postList.clear()
        for (document in documents) {
          val post = document.toObject(Post::class.java)
          postList.add(post)
        }
        postAdapter.notifyDataSetChanged()
        swipeRefreshLayout.isRefreshing = false
      }
      .addOnFailureListener { exception ->
        Log.e("HomeFragment", "Error fetching posts", exception)
        Toast.makeText(requireContext(), "Failed to load posts", Toast.LENGTH_SHORT).show()
        swipeRefreshLayout.isRefreshing = false
      }
  }


  private fun fetchCurrentUserAndRenderNav() {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    if (currentUserId == null) {
      Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
      findNavController().navigate(R.id.action_homeFragment_to_logoutFragment)
      return
    }
    FirebaseFirestore.getInstance().collection("users")
      .document(currentUserId)
      .get()
      .addOnSuccessListener { document ->
        if (document.exists()) {
          val firstName = document.getString("firstname") ?: ""
          val lastName = document.getString("lastname") ?: ""
          val email = document.getString("email") ?: ""
          val profileImageUrl = document.getString("profileImageUrl") ?: ""
          val user = User(
            id = currentUserId,
            firstname = firstName,
            lastname = lastName,
            email = email,
            profileImageUrl = profileImageUrl,
            imageBlob = null
          )

          renderNav(user)
        } else {
          Toast.makeText(requireContext(), "User data not found", Toast.LENGTH_SHORT).show()
        }
      }
      .addOnFailureListener { e ->
        Log.e("HomeFragment", "Error fetching user data", e)
        Toast.makeText(requireContext(), "Failed to fetch user data", Toast.LENGTH_SHORT).show()
      }
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = inflater.inflate(R.layout.fragment_home, container, false)

    // אתחול SwipeRefreshLayout
    swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
    swipeRefreshLayout.setOnRefreshListener { fetchPosts() }

    // אתחול RecyclerView והגדרת האדפטור
    recyclerView = view.findViewById(R.id.recycler_view)
    recyclerView.layoutManager = LinearLayoutManager(requireContext())
    postAdapter = PostAdapter(postList, requireContext())
    recyclerView.adapter = postAdapter

    // יצירת ViewModel למשתמשים (למרות ששיטה זו יכולה להחזיר נתונים, נעדיף לשלוף ישירות את המשתמש מ-Firestore)
    val userDao = AppDatabase.getDatabase(requireContext()).userDao()
    val repository = UserRepository(userDao)
    val factory = UserViewModelFactory(repository)
    userViewModel = ViewModelProvider(this, factory).get(UserViewModel::class.java)

    // קריאה לטעינת המשתמשים מה-ViewModel (לצורך עדכון לוקאלי, אם נדרש)
    viewLifecycleOwner.lifecycleScope.launch {
      delay(1000) // השהייה קטנה כדי לוודא שהנתונים נטענים
      userViewModel.getUsers()
    }
    userViewModel.users.observe(viewLifecycleOwner) { users ->
      Log.d("HomeFragment", "Users list from ViewModel: $users")
      if (users.isEmpty()) {
        Toast.makeText(requireActivity(), "Not logged in", Toast.LENGTH_SHORT).show()
        findNavController().navigate(R.id.action_homeFragment_to_logoutFragment)
      } else {
        // ניתן להשתמש בנתונים מה-ViewModel, אך אם רוצים להיות בטוחים – עדיף לשלוף ישירות
        renderNav(users[0])
      }
    }

    // טעינת פוסטים מהשרת
    fetchPosts()
    // שליפת נתוני המשתמש מה-Firestore לפי userId והצגת הנתונים בתפריט התחתון
    fetchCurrentUserAndRenderNav()

    return view
  }

  override fun onResume() {
    super.onResume()
    Log.d("HomeFragment", "onResume")
    fetchPosts()
  }
}

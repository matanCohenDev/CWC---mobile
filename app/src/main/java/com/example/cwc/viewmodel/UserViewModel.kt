package com.example.cwc.viewmodel

import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cwc.data.local.User
import com.example.cwc.data.repository.UserRepository
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class UserViewModel(private val repository: UserRepository) : ViewModel() {

  private val _users = MutableLiveData<List<User>>()
  val users: LiveData<List<User>> get() = _users

  private val _user = MutableLiveData<User?>()
  val user: LiveData<User?> get() = _user

  private val _logoutStatus = MutableLiveData<Boolean>()

  fun addUser(user: User) {
    viewModelScope.launch {
      repository.insertUser(user)
    }
  }

  fun getUsers() {
    viewModelScope.launch {
      val userList = repository.getAllUsers()
      _users.value = userList
    }
  }

  fun logout() {
    viewModelScope.launch {
      repository.logout()
      _logoutStatus.postValue(true)
    }
  }
}

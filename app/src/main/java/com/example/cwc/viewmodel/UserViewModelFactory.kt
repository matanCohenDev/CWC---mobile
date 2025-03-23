package com.example.cwc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cwc.data.repository.UserRepository

class UserViewModelFactory(private val repository: UserRepository) : ViewModelProvider.Factory {

  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
      return UserViewModel(repository) as T
    }

    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}

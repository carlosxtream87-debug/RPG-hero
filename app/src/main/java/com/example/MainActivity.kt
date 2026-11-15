package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.authStateFlow
import com.example.ui.auth.AuthScreen
import com.example.ui.game.RpgGameScreen
import com.example.ui.theme.EldoriaTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      EldoriaTheme {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(
      onAuthSuccess = { /* AuthStateListener updates currentUser automatically */ }
    )
  } else {
    key(user.uid) {
      RpgGameScreen(
        currentUserId = user.uid,
        userDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "Herói de Eldoria",
        userEmail = user.email ?: "",
      )
    }
  }
}

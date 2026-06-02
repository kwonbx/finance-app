package com.example.we_spend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.we_spend.ui.theme.WespendTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val auth = FirebaseAuth.getInstance()
        val userRepository = UserRepository()

        setContent {
            WespendTheme {
                MyApp(auth, userRepository)
            }
        }
    }
}

@Composable
fun MyApp(auth: FirebaseAuth, userRepository: UserRepository) {
    val navController = rememberNavController()

    val vmLogin: LoginViewModel = viewModel(factory = LoginViewModel.Factory(auth))
    val vmRegister: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory(auth, userRepository))

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(navController)
        }

        composable("login") {
            LoginScreen(navController, vmLogin)
        }

        composable("register") {
            RegisterScreen(navController, vmRegister)
        }
    }
}
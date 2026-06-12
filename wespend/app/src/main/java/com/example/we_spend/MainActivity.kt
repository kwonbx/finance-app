package com.example.we_spend

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        val expenseRepository = ExpenseRepository()
        val revenueRepository = RevenueRepository()

        val sharedPrefs = getSharedPreferences("WeSpendPrefs", Context.MODE_PRIVATE)

        var startDestination = "login"
        val isRemembered = sharedPrefs.getBoolean("REMEMBER_ME", false)

        if (auth.currentUser != null) {
            if (isRemembered) {
                startDestination = "home"
            } else {
                auth.signOut()
            }
        }

        setContent {
            val isDarkMode = remember { mutableStateOf(sharedPrefs.getBoolean("DARK_MODE", false)) }
            
            DisposableEffect(sharedPrefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == "DARK_MODE") {
                        isDarkMode.value = prefs.getBoolean("DARK_MODE", false)
                    }
                }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            WespendTheme(darkTheme = isDarkMode.value) {
                MyApp(auth, userRepository, expenseRepository, revenueRepository, sharedPrefs, startDestination)
            }
        }
    }
}

@Composable
fun MyApp(auth: FirebaseAuth, userRepository: UserRepository, expenseRepository: ExpenseRepository, revenueRepository: RevenueRepository, sharedPrefs: SharedPreferences, startDestination: String) {
    val navController = rememberNavController()

    val vmLogin: LoginViewModel = viewModel(factory = LoginViewModel.Factory(auth, sharedPrefs))
    val vmRegister: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory(auth, userRepository))
    val vmHome: HomeViewModel = viewModel(factory = HomeViewModel.Factory(expenseRepository, revenueRepository, userRepository))
    val vmAddExpense: AddExpenseViewModel = viewModel(factory = AddExpenseViewModel.Factory(expenseRepository, userRepository))
    val vmAddRevenue: AddRevenueViewModel = viewModel(factory = AddRevenueViewModel.Factory(revenueRepository, userRepository))
    val vmSettings: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(auth, userRepository, sharedPrefs))
    val vmFamily: FamilyViewModel = viewModel(factory = FamilyViewModel.Factory(userRepository))
    val vmExpenses: ExpensesViewModel = viewModel(factory = ExpensesViewModel.Factory(expenseRepository, userRepository))
    val vmRevenues: RevenuesViewModel = viewModel(factory = RevenuesViewModel.Factory(revenueRepository, userRepository))
    val editViewModel: EditRecurringViewModel = viewModel(factory = EditRecurringViewModel.Factory(expenseRepository))
    val editRevenueViewModel: EditRecurringRevenueViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = EditRecurringRevenueViewModel.Factory(revenueRepository))

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

        composable("home") {
            HomeScreen(navController = navController, vmHome, auth, sharedPrefs)
        }

        composable("add_expense") {
            AddExpenseScreen(navController = navController, viewModel = vmAddExpense)
        }

        composable("settings") {
            SettingsScreen(navController = navController, viewModel = vmSettings)
        }

        composable("family_management") {
            FamilyScreen(navController = navController, vmFamily)
        }

        composable("expenses") {
            ExpensesScreen(navController, vmExpenses, auth, sharedPrefs)
        }

        composable("revenues") {
            RevenuesScreen(navController, vmRevenues, auth, sharedPrefs)
        }

        composable("add_revenue") {
            AddRevenueScreen(navController = navController, viewModel = vmAddRevenue)
        }

        composable("edit_recurring/{recurringId}") { backStackEntry ->
            val recurringId = backStackEntry.arguments?.getString("recurringId") ?: ""
            EditRecurringScreen(navController, editViewModel, recurringId)
        }

        composable("edit_recurring_revenue/{recurringId}") { backStackEntry ->
            val recurringId = backStackEntry.arguments?.getString("recurringId") ?: ""
            EditRecurringRevenueScreen(navController, editRevenueViewModel, recurringId)
        }
    }
}
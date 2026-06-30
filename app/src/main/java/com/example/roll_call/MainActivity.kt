package com.example.roll_call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.example.roll_call.ui.navigation.AppNavHost
import com.example.roll_call.ui.navigation.Screen
import com.example.roll_call.ui.theme.Roll_callTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Roll_callTheme {
                val navController = rememberNavController()
                val startDestination = remember {
                    if (FirebaseAuth.getInstance().currentUser != null)
                        Screen.ClassList.route
                    else
                        Screen.Login.route
                }
                AppNavHost(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}

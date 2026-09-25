package com.losk.lemacdatalab

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.losk.lemacdatalab.ui.screens.LoginScreen
import com.losk.lemacdatalab.ui.theme.LemacDataLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LemacDataLabTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoginScreen(
                        onLoginSuccess = {
                            Toast.makeText(this, "¡Bienvenido a LemacDataLab!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
package com.losk.lemacdatalab

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.losk.lemacdatalab.ui.screens.LoginScreen
import com.losk.lemacdatalab.ui.screens.PantallaRegistroAnimo
import com.losk.lemacdatalab.ui.theme.LemacDataLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LemacDataLabTheme {

                var loginCorrecto by rememberSaveable { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (loginCorrecto) {
                        PantallaRegistroAnimo()
                    } else {
                        LoginScreen(
                            onLoginSuccess = {
                                loginCorrecto = true
                                Toast.makeText(this@MainActivity, "Inicio de sesion exitoso", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}
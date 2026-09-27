package com.kyanro.ibiki_logger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kyanro.ibiki_logger.theme.IbikiLoggerTheme
import com.kyanro.ibiki_logger.ui.LoggerApp
import com.kyanro.ibiki_logger.audio.RecordingService
import com.kyanro.ibiki_logger.data.SessionStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    lifecycleScope.launch(Dispatchers.IO) {
      SessionStore.get(this@MainActivity).recoverInterrupted { RecordingService.state.value.running }
    }
    setContent {
      IbikiLoggerTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { LoggerApp() } }
    }
  }
}

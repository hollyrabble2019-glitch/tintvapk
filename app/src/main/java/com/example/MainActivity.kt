package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ConnectivityBanner
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val defaultClipboardManager = LocalClipboardManager.current
        val wrappedClipboardManager = remember(defaultClipboardManager) {
          object : ClipboardManager {
            override fun getText(): AnnotatedString? {
              return if (this@MainActivity.hasWindowFocus()) {
                defaultClipboardManager.getText()
              } else {
                null
              }
            }

            override fun setText(annotatedString: AnnotatedString) {
              if (this@MainActivity.hasWindowFocus()) {
                defaultClipboardManager.setText(annotatedString)
              }
            }
          }
        }

        CompositionLocalProvider(LocalClipboardManager provides wrappedClipboardManager) {
          val mainViewModel: com.example.viewmodel.MainViewModel = viewModel()
          val state = mainViewModel.state.collectAsState()

          // Handle physical/remote back press to navigate back inside the application
          BackHandler(enabled = state.value.backstack.size > 1) {
            mainViewModel.navigateBack()
          }

          // Collect remote update config and state
          val updateConfig by UpdateManager.config.collectAsState()
          val showUpdateDialog = UpdateManager.showDialog

          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              MainAppScreen(viewModel = mainViewModel)
              
              // Non-intrusive connectivity banner at the top of the screen
              ConnectivityBanner(modifier = Modifier.align(Alignment.TopCenter))
              
              // Display the custom remote update dialog on top of the layout if triggered
              if (showUpdateDialog && updateConfig != null) {
                UpdateDialog(
                  config = updateConfig!!,
                  onDismiss = { UpdateManager.dismissDialog() },
                  onUpdate = { UpdateManager.performUpdate(this@MainActivity) }
                )
              }
            }
          }
        }
      }
    }
  }
}

package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Tinghir TV", appName)
  }

  @Test
  fun `test viewModel initialization`() = runTest {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(application)
    // Wait for the coroutine initialization to finish
    kotlinx.coroutines.delay(1000)
    val state = viewModel.state.value
    println("Initialized state channels size: ${state.allChannels.size}")
  }
}

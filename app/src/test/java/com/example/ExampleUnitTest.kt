package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun updateManager_versionComparison_isCorrect() {
    // Current is equal to remote
    assertFalse(UpdateManager.isVersionNewer("1.0", "1.0"))
    assertFalse(UpdateManager.isVersionNewer("1.0", "1.0.0"))
    assertFalse(UpdateManager.isVersionNewer("1.0.0", "1.0"))

    // Remote is newer (needs update)
    assertTrue(UpdateManager.isVersionNewer("1.0", "1.1"))
    assertTrue(UpdateManager.isVersionNewer("1.0.1", "1.0.2"))
    assertTrue(UpdateManager.isVersionNewer("1.0.9", "1.1.0"))
    assertTrue(UpdateManager.isVersionNewer("1.0", "2.0.0"))
    assertTrue(UpdateManager.isVersionNewer("1.0", "2"))

    // Current is newer than remote (no update)
    assertFalse(UpdateManager.isVersionNewer("1.1.0", "1.0.9"))
    assertFalse(UpdateManager.isVersionNewer("2.0.1", "1.9.9"))
    assertFalse(UpdateManager.isVersionNewer("2", "1.0"))
    assertFalse(UpdateManager.isVersionNewer("3.1", "3.0.5"))
  }
}

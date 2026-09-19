package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CookieStorageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    assertEquals("Prompt4u", appName)
  }

  @Test
  fun `verify admin passkey and lockout security`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cookieManager = CookieStorageManager(context)

    // Test default passkey
    val defaultKey = cookieManager.getAdminPasskey()
    assertEquals(CookieStorageManager.DEFAULT_ADMIN_PASSKEY, defaultKey)

    // Set custom passkey
    cookieManager.setAdminPasskey("MySuperSecretKey#99")
    assertEquals("MySuperSecretKey#99", cookieManager.getAdminPasskey())
    assertTrue(cookieManager.isCustomPasskeySet())

    // Test failed attempts & lockout
    cookieManager.resetFailedAttempts()
    assertEquals(0, cookieManager.getFailedAttempts())
    assertEquals(0L, cookieManager.getLockoutRemainingSeconds())

    for (i in 1..5) {
      cookieManager.recordFailedAttempt()
    }
    assertEquals(5, cookieManager.getFailedAttempts())
    assertTrue(cookieManager.getLockoutRemainingSeconds() > 0)

    // Reset unlocks
    cookieManager.resetFailedAttempts()
    assertEquals(0L, cookieManager.getLockoutRemainingSeconds())

    // Test changing to numeric PIN
    cookieManager.setAdminPasskey("8844")
    assertEquals("8844", cookieManager.getAdminPasskey())
  }
}


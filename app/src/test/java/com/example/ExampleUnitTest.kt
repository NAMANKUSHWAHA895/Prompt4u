package com.example

import android.net.Uri
import com.example.data.model.GeminiImageResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun geminiImageResult_propertiesVerification() {
    val dummyUri = Uri.parse("file:///dummy/path/generated.png")
    val result = GeminiImageResult(
      resultImageUri = dummyUri,
      prompt = "Futuristic neon cyberpunk city",
      modelName = "gemini-2.5-flash-image",
      aspectRatio = "16:9",
      style = "Cyberpunk",
      textExplanation = "Generated with glowing neon lights"
    )

    assertEquals(dummyUri, result.resultImageUri)
    assertEquals("Futuristic neon cyberpunk city", result.prompt)
    assertEquals("gemini-2.5-flash-image", result.modelName)
    assertEquals("16:9", result.aspectRatio)
    assertEquals("Cyberpunk", result.style)
    assertNull(result.inputImageUri)
  }
}

pluginManagement {
  val agpVersion = "4.1.0"
  repositories {
    google()
  }
  plugins {
    id("com.android.application") version agpVersion
    id("com.android.library") version agpVersion
  }
}

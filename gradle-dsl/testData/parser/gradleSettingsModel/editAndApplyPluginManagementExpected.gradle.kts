pluginManagement {
  plugins {
    id("com.android.library") version "7.0.0" apply false
  }
  repositories {
    google()
  }
}
rootProject.name = "My Application"
include ":app"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.daniel.masterstudio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.daniel.masterstudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
}

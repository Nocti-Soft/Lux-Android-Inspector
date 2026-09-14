plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.noctisoft.layoutmeasurement"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(libs.androidx.startup)
    implementation(libs.androidx.core.ktx)
    // Compose semantics access only — no @Composable code, so no compose-compiler plugin.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

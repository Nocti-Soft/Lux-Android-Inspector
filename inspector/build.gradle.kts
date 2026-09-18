import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

// Set explicitly during publication.
// JitPack will pass its requested tag or commit through jitpack.yml.
val publicationVersion = providers
    .gradleProperty("publicationVersion")
    .orElse("0.1.0-SNAPSHOT")
    .get()

group = "com.github.Nocti-Soft"
version = publicationVersion

android {
    namespace = "com.noctisoft.layoutmeasurement"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.startup)
    implementation(libs.androidx.core.ktx)

    // Compose APIs are used without @Composable declarations.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.Nocti-Soft"
            artifactId = "Lux-Android-Inspector"
            version = publicationVersion

            pom {
                name.set("Lux Android Inspector")
                description.set(
                    "In-app Android layout measurement and color inspection."
                )
                url.set(
                    "https://github.com/Nocti-Soft/Lux-Android-Inspector"
                )
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
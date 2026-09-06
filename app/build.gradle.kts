import java.util.Properties

plugins {

    id("com.android.application")

    id("org.jetbrains.kotlin.android")

    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing material. Locally it comes from an untracked keystore.properties in
// the project root; in CI it comes from env vars fed by the repository secrets.
val keystoreProperties = Properties().apply {

    val file = rootProject.file("keystore.properties")

    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: System.getenv(envKey)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")

val releaseStorePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")

val releaseKeyAlias = signingValue("keyAlias", "KEY_ALIAS")

val releaseKeyPassword = signingValue("keyPassword", "KEY_PASSWORD")

val hasReleaseSigning = releaseStoreFile != null &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

// Overridden by the release workflow: -PversionName=1.2.3 -PversionCode=10203
val appVersionName = (findProperty("versionName") as String?) ?: "1.0.0"

val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

android {

    namespace = "com.nova.automate"

    compileSdk = 35

    defaultConfig {

        applicationId = "com.nova.automate"

        minSdk = 26

        targetSdk = 35

        versionCode = appVersionCode

        versionName = appVersionName
    }

    signingConfigs {

        create("release") {

            if (hasReleaseSigning) {

                // Resolve against the repo root, not app/ - keystore.properties uses a
                // root-relative path. Absolute paths (as CI passes) are returned as-is.
                storeFile = rootProject.file(releaseStoreFile!!)

                storePassword = releaseStorePassword

                keyAlias = releaseKeyAlias

                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {

        getByName("release") {

            isMinifyEnabled = false

            // Without a keystore (fresh clone, no secrets) fall back to debug signing so the
            // project still builds. Release CI always has the real key.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {

        compose = true
    }

    compileOptions {

        sourceCompatibility = JavaVersion.VERSION_17

        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {

        jvmTarget = "17"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")

    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui:1.6.8")

    implementation("androidx.compose.material3:material3:1.2.1")

    implementation("androidx.compose.material:material-icons-core:1.6.8")

    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    // Apache POI for reading Excel files
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
}

plugins {

    id("com.android.application")

    id("org.jetbrains.kotlin.android")

    id("org.jetbrains.kotlin.plugin.compose")
}

android {

    namespace = "com.nova.automate"

    compileSdk = 35

    defaultConfig {

        applicationId = "com.nova.automate"

        minSdk = 26

        targetSdk = 35

        versionCode = 1

        versionName = "1.0"
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
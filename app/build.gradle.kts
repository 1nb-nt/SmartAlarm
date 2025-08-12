plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.alarmchatapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.alarmchatapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
        compileOptions {
            sourceCompatibility =JavaVersion.VERSION_21
            targetCompatibility =JavaVersion.VERSION_21
        }

}


dependencies {
    // Compose BOM (latest stable as of May 2025)
    implementation(platform("androidx.compose:compose-bom:2025.07.00"))

    // Compose UI libraries
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Core KTX (stable)
    implementation("androidx.core:core-ktx:1.16.0")

    // Lifecycle & Activity
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Location services
    implementation("com.google.android.gms:play-services-location:21.2.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
}

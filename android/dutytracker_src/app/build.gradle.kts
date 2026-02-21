plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.mapv12.dutytracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mapv12.dutytracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.5"

        // For Room schema export (optional)
        kapt {
            arguments {
                arg("room.schemaLocation", "${'$'}projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WorkManager for reliable background uploads
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Room for offline queue
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Secure token storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")          // KSP вместо устаревшего KAPT
}

android {
    namespace = "com.mapv12.dutytracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mapv12.dutytracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "2.0"

        // Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Убираем неиспользуемые ресурсы
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    // ── AndroidX Core ─────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    // ── Material Design 3 ─────────────────────────────────────
    implementation("com.google.android.material:material:1.12.0")

    // ── Layouts ───────────────────────────────────────────────
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // ── Lifecycle / ViewModel / Coroutines ────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // ── Location ──────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // ── Network ───────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── WorkManager ───────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ── Room (KSP) ────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // ── Security ──────────────────────────────────────────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── Splash Screen ─────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:1.0.1")
}

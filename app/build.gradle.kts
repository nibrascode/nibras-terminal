plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val androidSdk = (findProperty("sdk") as String?)?.toIntOrNull() ?: 35

android {
    namespace = "com.nibras.terminal"
    compileSdk = androidSdk

    defaultConfig {
        applicationId = "com.nibras.terminal"
        minSdk = 26
        targetSdk = androidSdk
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            val store = System.getenv("NIBRAS_STORE_FILE").orEmpty()
            if (store.isNotEmpty()) {
                storeFile = file(store)
                storePassword = System.getenv("NIBRAS_STORE_PASSWORD")
                keyAlias = System.getenv("NIBRAS_KEY_ALIAS")
                keyPassword = System.getenv("NIBRAS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (System.getenv("NIBRAS_STORE_FILE").orEmpty().isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

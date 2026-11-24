plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val androidSdk = (findProperty("sdk") as String?)?.toIntOrNull() ?: 35
// Fixed release version. Never bump automatically: Mahir decides when the version changes.
val nibrasVersionName = "1.0.0"
val androidVersion = 10008 // store versionName 1.0.0; versionCode 10008 stays above 1.0.7 (10007)

android {
    namespace = "com.nibras.terminal"
    compileSdk = androidSdk

    defaultConfig {
        applicationId = "com.nibras.terminal"
        minSdk = 26
        targetSdk = androidSdk
        versionCode = androidVersion
        versionName = nibrasVersionName
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val store = System.getenv("NIBRAS_STORE_FILE").orEmpty()
            if (store.isNotEmpty()) {
                storeFile = file(store)
                storeType = "PKCS12"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // proot, its loader and talloc must exist as real files in nativeLibraryDir,
            // because Android 10+ only lets an app exec() binaries from there.
            useLegacyPackaging = true
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
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    // Termux terminal emulator + view (Apache-2.0): real PTY, VT100/xterm, scrollback, pinch zoom, selection.
    implementation("com.github.termux.termux-app:terminal-view:v0.118.1")
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

// Script store: catalog/scripts (index + scripts with sha256) is bundled as assets/scripts for offline browsing.
val scriptStoreAssets = layout.buildDirectory.dir("generated/scriptStore")
val copyScriptStore = tasks.register<Sync>("copyScriptStore") {
    from(rootProject.file("catalog/scripts"))
    into(scriptStoreAssets.map { it.dir("scripts") })
}
android.sourceSets.getByName("main").assets.srcDir(scriptStoreAssets)
tasks.named("preBuild") { dependsOn(copyScriptStore) }

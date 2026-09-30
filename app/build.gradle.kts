plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.subhashrelangi.arctracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.subhashrelangi.arctracker"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            System.getenv("ARCTRACKER_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { path ->
                val keystoreFile = file(path)
                storeFile = if (keystoreFile.exists()) keystoreFile else rootProject.file(path)
            }
            storePassword = System.getenv("ARCTRACKER_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ARCTRACKER_KEY_ALIAS")
            keyPassword = System.getenv("ARCTRACKER_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val releaseSigningTasks = setOf("validateSigningRelease", "signReleaseBundle")
tasks.matching { it.name in releaseSigningTasks }.configureEach {
    doFirst {
        val requiredVariables = listOf(
            "ARCTRACKER_KEYSTORE_PATH",
            "ARCTRACKER_KEYSTORE_PASSWORD",
            "ARCTRACKER_KEY_ALIAS",
            "ARCTRACKER_KEY_PASSWORD"
        )
        val missingVariables = requiredVariables.filter { System.getenv(it).isNullOrBlank() }
        check(missingVariables.isEmpty()) {
            "Release signing requires environment variables: ${missingVariables.joinToString()}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material)
    implementation("androidx.compose.material:material-icons-extended")

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // JSON serialization for Export/Import/Backup (Milestone 15)
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}
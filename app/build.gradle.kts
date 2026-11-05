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
        versionName = "2.4"
        buildConfigField("String", "GITHUB_VERSION", "\"v1.0.6\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val envPath = System.getenv("ARCTRACKER_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
            val envStorePass = System.getenv("ARCTRACKER_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
            val envKeyAlias = System.getenv("ARCTRACKER_KEY_ALIAS")?.takeIf { it.isNotBlank() }
            val envKeyPass = System.getenv("ARCTRACKER_KEY_PASSWORD")?.takeIf { it.isNotBlank() }

            if (envPath != null && envStorePass != null && envKeyAlias != null && envKeyPass != null) {
                val keystoreFile = file(envPath)
                val targetFile = if (keystoreFile.exists()) keystoreFile else rootProject.file(envPath)
                if (targetFile.exists()) {
                    storeFile = targetFile
                    storePassword = envStorePass
                    keyAlias = envKeyAlias
                    keyPassword = envKeyPass
                }
            }
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile?.exists() == true) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
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
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation("androidx.biometric:biometric:1.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}
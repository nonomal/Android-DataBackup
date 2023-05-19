import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.xayah.databackup"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.xayah.databackup"
        minSdk = 28
        targetSdk = 33
        versionCode = 3300098
        versionName = "1.0.1-alpha02"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // __(API)_(feature)_(abi)___(version)
    flavorDimensions += listOf("abi", "feature")
    productFlavors {
        create("arm64-v8a") {
            dimension = "abi"
            versionCode = 1000 + (android.defaultConfig.versionCode ?: 0)
        }
        create("armeabi-v7a") {
            dimension = "abi"
            versionCode = 2000 + (android.defaultConfig.versionCode ?: 0)
        }
        create("x86") {
            dimension = "abi"
            versionCode = 3000 + (android.defaultConfig.versionCode ?: 0)
        }
        create("x86_64") {
            dimension = "abi"
            versionCode = 4000 + (android.defaultConfig.versionCode ?: 0)
        }
        create("foss") {
            dimension = "feature"
            versionCode = 10000 + (android.defaultConfig.versionCode ?: 0)
            applicationIdSuffix = ".foss"
        }
        create("premium") {
            dimension = "feature"
            versionCode = 20000 + (android.defaultConfig.versionCode ?: 0)
            applicationIdSuffix = ".premium"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.all {
        outputs.forEach { output ->
            (output as BaseVariantOutputImpl).outputFileName =
                "DataBackup-${versionName}-${productFlavors[0].name}-${buildType.name}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.7.1")
    implementation(platform("androidx.compose:compose-bom:2023.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // System UI Controller
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.30.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.44")
    kapt("com.google.dagger:hilt-android-compiler:2.44")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.5.3")
    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
}

kapt {
    correctErrorTypes = true
}
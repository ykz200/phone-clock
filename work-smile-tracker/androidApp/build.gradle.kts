plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ykz200.smiletracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ykz200.smiletracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

// v6.0: 直接在运行的 JDK (21+) 编译，无需 toolchain 自动检测
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
    }
}

dependencies {
    // 引用 shared 模块的核心逻辑 + Compose UI
    implementation(project(":shared"))

    // androidx.core — NotificationCompat (SmileyAnalysisService 用)
    implementation(libs.androidx.core.ktx)

    // Compose BOM — androidApp使用AndroidX Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")

    // Compose Android 控件 + Activity 宿主
    implementation(libs.compose.activity)
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // kotlinx-datetime (CameraAnalyzer 编译需要)
    implementation(libs.kotlinx.datetime)

    // SQLDelight Android Driver (MainActivity 编译需要)
    implementation(libs.sqldelight.android.driver)

    // CameraX (CameraAnalyzer 编译需要)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // MediaPipe Face Detection (CameraAnalyzer 编译需要)
    implementation(libs.mediapipe.tasks.vision)

    // WorkManager (SmileyAnalysisService 编译需要)
    implementation(libs.workmanager.runtime)
}

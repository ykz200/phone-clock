plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
}

kotlin {
    // Android 目标
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    // iOS 目标 (需要 XCode 编译，此处声明 skeleton)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    // ── 源集 ──
    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform 共享 UI
            implementation("org.jetbrains.compose.runtime:runtime:1.7.3")
            implementation("org.jetbrains.compose.foundation:foundation:1.7.3")
            implementation("org.jetbrains.compose.material3:material3:1.7.3")
            implementation("org.jetbrains.compose.ui:ui:1.7.3")

            // SQLDelight 共享 + Flow
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.ext)
            implementation(libs.sqldelight.primitive.adapters)
            implementation(libs.coroutines.core)

            // Lifecycle ViewModel 共享
            implementation(libs.compose.lifecycle.runtime)

            // kotlinx-datetime
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            // SQLDelight Android 驱动
            implementation(libs.sqldelight.android.driver)

            // CameraX 三件套
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)

            // MediaPipe 人脸/Android 视觉任务
            implementation(libs.mediapipe.tasks.vision)

            // Android WorkManager 后台任务
            implementation(libs.workmanager.runtime)

            // Compose Android 控件 + Activity 宿主
            implementation(libs.compose.activity)
            implementation(libs.compose.ui.tooling)
            implementation(libs.compose.ui.tooling.preview)

            // AndroidX core-ktx
            implementation(libs.androidx.core.ktx)
        }

        iosMain.dependencies {
            // SQLDelight iOS 驱动
            implementation(libs.sqldelight.native.driver)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Android library 配置
android {
    namespace = "com.ykz200.smiletracker"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("SmileDatabase") {
            packageName.set("com.ykz200.smiletracker.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/schema"))
            generateAsync.set(false)
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

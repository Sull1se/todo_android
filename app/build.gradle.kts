plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
      applicationId = "com.aistudio.todo.xqwdfa"
    minSdk = 24
    targetSdk = 36
    versionCode = 20
    versionName = "3.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    val releaseKeystore = rootProject.file(".signing/todo-release.jks")
    create("release") {
      storeFile = releaseKeystore
      storePassword = System.getenv("STORE_PASSWORD") ?: ""
      keyAlias = "todo-release"
      keyPassword = System.getenv("KEY_PASSWORD") ?: ""
    }
    // 本地化调试签名适配：若根目录下存在自定义 debug.keystore 则加载；否则回退使用系统默认 Android 调试签名
    val localDebugKeystore = file("${rootDir}/debug.keystore")
    if (localDebugKeystore.exists()) {
      create("debugConfig") {
        storeFile = localDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfigs.findByName("debugConfig")?.let {
        signingConfig = it
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.forkEvery = 1
      }
    }
  }
  sourceSets {
    getByName("androidTest").assets.directories += "$projectDir/schemas"
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.reorderable)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  testImplementation(libs.androidx.room.testing)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.room.testing)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

gradle.taskGraph.whenReady {
  val isReleaseScheduled = allTasks.any { task ->
    task.project == project && (
      task.name.contains("Release", ignoreCase = false) &&
      (task.name.startsWith("assemble") || task.name.startsWith("bundle") || task.name.startsWith("package"))
    )
  }
  if (isReleaseScheduled) {
    val releaseKeystore = rootProject.file(".signing/todo-release.jks")
    if (!releaseKeystore.exists()) {
      throw GradleException("Release keystore not found at ${releaseKeystore.absolutePath}. Build aborted.")
    }
    val storePassword = System.getenv("STORE_PASSWORD")
    if (storePassword.isNullOrBlank()) {
      throw GradleException("STORE_PASSWORD environment variable is missing or empty. Build aborted.")
    }
    val keyPassword = System.getenv("KEY_PASSWORD")
    if (keyPassword.isNullOrBlank()) {
      throw GradleException("KEY_PASSWORD environment variable is missing or empty. Build aborted.")
    }
  }
}

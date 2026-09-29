/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
  val localPropertiesFile = rootProject.file("local.properties")
  if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { load(it) }
  }
}

android {
  namespace = "com.example.executorchllamademo"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.example.executorchllamademo"
    minSdk = 26
    targetSdk = 33
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
    externalNativeBuild { cmake { cppFlags += "" } }

    buildConfigField("String", "BENCHMARK_API_URL", "\"${localProperties.getProperty("benchmark.api.url", "")}\"")
    buildConfigField("String", "BENCHMARK_API_KEY", "\"${localProperties.getProperty("benchmark.api.key", "")}\"")
  }

  signingConfigs {
    create("release") {
      storeFile = file(localProperties.getProperty("release.store.file", "../executorch-bench-release.keystore"))
      storePassword = localProperties.getProperty("release.store.password")
      keyAlias = localProperties.getProperty("release.key.alias")
      keyPassword = localProperties.getProperty("release.key.password")
    }
  }

  buildTypes {
    debug {
      isDebuggable = true
      isMinifyEnabled = false
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  composeOptions { kotlinCompilerExtensionVersion = "1.4.3" }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation("androidx.core:core-ktx:1.9.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
  implementation("androidx.activity:activity-compose:1.7.0")
  implementation(platform("androidx.compose:compose-bom:2023.03.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.camera:camera-core:1.3.0-rc02")
  implementation("androidx.constraintlayout:constraintlayout:2.2.0-alpha12")
  implementation("com.facebook.fbjni:fbjni:0.5.1")
  implementation("com.google.code.gson:gson:2.8.6")
  implementation("org.pytorch:executorch-android:1.1.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.activity:activity:1.9.0")
  implementation("org.json:json:20250107")
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
  androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.register("setup") {
  doFirst {
    exec {
      commandLine("sh", "examples/demo-apps/android/LlamaDemo/setup.sh")
      workingDir("../../../../../")
    }
  }
}

tasks.register("setupQnn") {
  doFirst {
    exec {
      commandLine("sh", "examples/demo-apps/android/LlamaDemo/setup-with-qnn.sh")
      workingDir("../../../../../")
    }
  }
}

tasks.register("download_prebuilt_lib") {
  doFirst {
    exec {
      commandLine("sh", "examples/demo-apps/android/LlamaDemo/download_prebuilt_lib.sh")
      workingDir("../../../../../")
    }
  }
}

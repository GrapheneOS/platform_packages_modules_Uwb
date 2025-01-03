plugins {
  alias(libs.plugins.androidApplication)
}

android {
  namespace = "com.android.ranging.rangingtestapp"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.android.ranging.rangingtestapp"
    minSdk = 36
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  buildFeatures {
    viewBinding = true
  }
}

dependencies {

  implementation(libs.appcompat)
  implementation(libs.material)
  implementation(libs.constraintlayout)
  implementation(libs.navigation.fragment)
  implementation(libs.navigation.ui)
  implementation(libs.legacy.support.v4)
  implementation(libs.lifecycle.livedata.ktx)
  implementation(libs.lifecycle.viewmodel.ktx)
  testImplementation(libs.junit)
  androidTestImplementation(libs.ext.junit)
  androidTestImplementation(libs.espresso.core)
}

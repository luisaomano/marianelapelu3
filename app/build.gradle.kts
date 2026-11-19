plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "uy.negocio.offline"; compileSdk = 35
    defaultConfig { applicationId = "uy.negocio.offline"; minSdk = 24; targetSdk = 35; versionCode = 4; versionName = "2.2" }
    compileOptions { isCoreLibraryDesugaringEnabled = true; sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4") }

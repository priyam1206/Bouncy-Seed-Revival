plugins {
    id("com.android.application")
}

android {
    namespace = "com.priyn.bouncyseed.family"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.priyn.bouncyseed.family"
        minSdk = 24
        targetSdk = 35
        versionCode = 175
        versionName = "1.7.5-modern"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Pure zero-dependency modern native Java game engine
}

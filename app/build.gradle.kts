plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ro.florin.bateriexperia"
    compileSdk = 35

    defaultConfig {
        applicationId = "ro.florin.bateriexperia"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Cheie de semnare fixă, comună tuturor versiunilor, ca să poți actualiza
    // aplicația peste versiunea instalată fără să o dezinstalezi.
    signingConfigs {
        create("shared") {
            storeFile = file("../keystore/release.jks")
            storePassword = "battery123"
            keyAlias = "battery"
            keyPassword = "battery123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

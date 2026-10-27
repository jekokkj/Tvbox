plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "fi.qvr.spotlet"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.tvbox.spotify"
        // 24 = Android 7.0. O audio usa AudioTrack (JNI) em vez do AAudio, que exige API 26.
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0"

        // The native core is built for exactly these ABIs (see .github/workflows/build.yml).
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Chave fixa (segredos do GitHub): todas as versoes ficam assinadas igual e atualizam por cima.
    signingConfigs {
        create("fixa") {
            val arq = System.getenv("KS_FILE")
            if (arq != null) {
                storeFile = file(arq)
                storeType = "pkcs12"
                storePassword = System.getenv("KS_PASS")
                keyAlias = System.getenv("KS_ALIAS")
                keyPassword = System.getenv("KS_PASS")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (System.getenv("KS_FILE") != null) signingConfigs.getByName("fixa") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media)
    testImplementation(libs.junit)
}

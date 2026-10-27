plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.tvbox.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.tvbox.launcher"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        // Chave fixa (vem dos segredos do GitHub): todas as versoes ficam assinadas igual e atualizam por cima.
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
            // Menor e mais leve. Assinado com a chave de debug so para poder
            // instalar via adb (uso pessoal, nao serve para loja).
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = if (System.getenv("KS_FILE") != null) signingConfigs.getByName("fixa") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

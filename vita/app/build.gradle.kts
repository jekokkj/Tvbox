plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.tvbox.vita"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.tvbox.vita"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Sem minificacao: a biblioteca de camera USB usa codigo nativo (JNI) e reflexao.
            // Assinado com a chave de debug so para instalar via adb (uso pessoal).
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

dependencies {
    // Acesso a cameras UVC (a tela do Vita aparece como uma webcam USB) sem root.
    implementation("com.herohan:UVCAndroid:1.0.13")
}

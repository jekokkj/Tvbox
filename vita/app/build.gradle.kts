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
        versionCode = 4
        versionName = "1.4"
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
            // Sem minificacao: a biblioteca de camera USB usa codigo nativo (JNI) e reflexao.
            // Assinado com a chave de debug so para instalar via adb (uso pessoal).
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KS_FILE") != null) signingConfigs.getByName("fixa") else signingConfigs.getByName("debug")
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
    implementation(project(":uvc"))
}

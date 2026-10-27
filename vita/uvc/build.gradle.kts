// Copia local da biblioteca UVCAndroid 1.0.13 (Apache 2.0, shiyinghan/UVCAndroid).
// Os fontes (src/) sao baixados e corrigidos na hora da compilacao por vita/tools/preparar_uvc.sh:
// a biblioteca original so entende video YUYV, e o plugin do Vita envia NV12.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.serenegiant.uvccamera"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("cn.hutool:hutool-core:5.8.35")
}
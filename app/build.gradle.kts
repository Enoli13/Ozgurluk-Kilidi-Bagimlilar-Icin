plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ozgurluk.kilidi"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ozgurluk.kilidi.bagimli"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Kalıcı imza anahtarı: her sürüm aynı anahtarla imzalanır, böylece yeni APK telefondaki
    // uygulamanın üstüne güncelleme olarak kurulur (ayarlar ve kilit korunur). Anahtar dosyası
    // parolayla şifrelidir; parola yalnızca GitHub'daki SIGNING_PASSWORD sırrında durur.
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    signingConfigs {
        if (!signingPassword.isNullOrEmpty()) {
            create("kalici") {
                storeFile = file("release-signing.jks")
                storePassword = signingPassword
                keyAlias = "ozgurluk"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("kalici")?.let { signingConfig = it }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
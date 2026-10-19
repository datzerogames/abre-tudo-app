import java.util.Properties

plugins {
    id("com.android.application")
}

// A chave de upload fica fora do projeto (nunca vai pro Git): ~/chaves-android/
val arquivoAssinatura = file("${System.getProperty("user.home")}/chaves-android/abre-tudo-upload.properties")
val assinatura = Properties().apply {
    if (arquivoAssinatura.exists()) arquivoAssinatura.inputStream().use { load(it) }
}

android {
    namespace = "br.com.datzerogames.abretudo"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.datzerogames.abretudo"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"
    }

    signingConfigs {
        create("upload") {
            if (arquivoAssinatura.exists()) {
                storeFile = file(assinatura.getProperty("storeFile"))
                storePassword = assinatura.getProperty("storePassword")
                keyAlias = assinatura.getProperty("keyAlias")
                keyPassword = assinatura.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (arquivoAssinatura.exists()) signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Ferramentas de PDF (juntar, dividir, marca d'água, senha, numerar, organizar).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.waguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ecomacademy.whatsme"
        minSdk = 26
        targetSdk = 34
        // le numéro de version augmente automatiquement à chaque compilation GitHub
        versionCode = 100 + (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "3.0"
    }

    // Clé de signature FIXE : toutes les versions sont signées pareil,
    // donc les mises à jour s'installent par-dessus l'ancienne sans rien perdre.
    // Le fichier app/ecom.keystore N'EST PAS dans le dépôt (voir .gitignore) :
    // sur GitHub Actions il est reconstruit à partir du secret KEYSTORE_BASE64
    // (étape "Decode keystore" du workflow). En local, mets ton propre
    // ecom.keystore dans app/ pour pouvoir compiler sur ta machine.
    signingConfigs {
        create("ecom") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "ecom.keystore")
            storePassword = System.getenv("KEYSTORE_STORE_PASSWORD") ?: "EcomWhatsMe2026"
            keyAlias = System.getenv("KEYSTORE_KEY_ALIAS") ?: "ecom"
            keyPassword = System.getenv("KEYSTORE_KEY_PASSWORD") ?: "EcomWhatsMe2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ecom")
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
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}

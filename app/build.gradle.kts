import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
val hasReleaseSigning = keystorePropertiesFile.exists()
val isReleaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
val requiredSigningKeys = listOf("storeFile", "storePassword", "keyAlias")

if (hasReleaseSigning) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

fun propertyValue(key: String): String? {
    val directValue = keystoreProperties.getProperty(key)?.trim()
    if (!directValue.isNullOrEmpty()) return directValue

    return keystoreProperties.stringPropertyNames()
        .firstOrNull { it.equals(key, ignoreCase = true) }
        ?.let { keystoreProperties.getProperty(it)?.trim() }
        ?.takeIf { it.isNotEmpty() }
}

val missingSigningKeys = if (hasReleaseSigning) {
    requiredSigningKeys.filter { propertyValue(it) == null }
} else {
    emptyList()
}

android {
    namespace = "de.beansys.audiolevels"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.beansys.audiolevels"
        minSdk = 36
        targetSdk = 36
        versionCode = 100
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning && missingSigningKeys.isEmpty()) {
                storeFile = rootProject.file(propertyValue("storeFile")!!)
                storePassword = propertyValue("storePassword")
                keyAlias = propertyValue("keyAlias")
                keyPassword = propertyValue("keyPassword") ?: propertyValue("storePassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning && missingSigningKeys.isEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}

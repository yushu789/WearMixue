import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val signingProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun signingValue(vararg names: String): String {
    for (name in names) {
        val gradleValue = findProperty(name)?.toString()
        if (!gradleValue.isNullOrBlank()) return gradleValue
        val localValue = signingProperties.getProperty(name)
        if (!localValue.isNullOrBlank()) return localValue
        val envValue = System.getenv(name.replace('.', '_').replace('-', '_').uppercase())
        if (!envValue.isNullOrBlank()) return envValue
    }
    return ""
}

val releaseStoreFile = signingValue("release.storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("release.storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("release.keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("release.keyPassword", "RELEASE_KEY_PASSWORD")
val releaseStoreType = signingValue("release.storeType", "RELEASE_STORE_TYPE").ifBlank { "PKCS12" }
val hasReleaseSigning = releaseStoreFile.isNotBlank() &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank() &&
    rootProject.file(releaseStoreFile).exists()

android {
    namespace = "site.unclefish.wearmixue.phone"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "site.unclefish.wearmixue.phone"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storeType = releaseStoreType
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(project(":core"))

    debugImplementation(libs.androidx.compose.ui.tooling)
}

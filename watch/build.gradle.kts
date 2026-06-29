import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val signingProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localSecret(vararg names: String): String {
    for (name in names) {
        val gradleValue = findProperty(name) as String?
        if (!gradleValue.isNullOrBlank()) return gradleValue
        val localValue = localProperties.getProperty(name)
        if (!localValue.isNullOrBlank()) return localValue
        val envValue = System.getenv(name.replace('.', '_').uppercase())
        if (!envValue.isNullOrBlank()) return envValue
    }
    return ""
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

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

val builtInMixuePrivateKey = """
    MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCL36i7a8O4NGQeo7pvIHkXKtxd1hCG
    hnnlPc2QspqV+MDD3iuYg+iOYIJ1ar1NjsvWA2K/D7EuDm0KMhDC5aZvbMrOLnIbbTHF0WKCW9ue4DP1
    L/OERTOrB12/q2aq6yRD3uHMB/IIYmkOAPGr7lYqyPnSlrsWxeK4F8SXInbtrgmKlWiBqDzG8Joy74n2
    QevY7Hjl1kK35TjzdDDWXyZjxIUOxtNL63r/5oTCuw1fZg5fR0LTb6OtJmM0ur/aWZpSW9CT3OeyZffJ
    ldfZaJNmNlB4oxOPFslJdoIAXlEceEveSVA9ywI5QGG7NSSLkjE+kDnN+iXliEhz2BPOQAs1AgMBAAEC
    ggEAWkB7kEtNo1ryyy5cCn5Kg99dB5MrYJH+ryM8s8P6qRAz2W5OdP+QG+Y752VzNksQTUwr+Bo4+f3G
    79A0Ln2d8cGh7n3blMTVW83qITECObZy8B77ovpAB3geTqFbAqfs43o5+buauTw+ixGi3oxPvxWk1PP7
    Tgtradu0Nsy/LkasgSeApi+batfWUtQM2A1ePklNAyUN+5dkqqhzM9z2adIqMKgxPjWtVvvvdNwiqu5+
    E8qwGES6pz+4/cFTjgeu7F/NiITkIl/qA57X75T3A6yG35CTD38WhYvZflshLGnr0DOCdBXsf85IHBaG
    NyUKYMoni2aoJgRySZBrp0WXIQKBgQDDTFNzT+0jpCeEjNzPd6dZc+0WWmbCT5eEgQTEIXy7AhSKhOYS
    mezg3v3W9rxKv3vHoVrCanyuibNCH2NDwHvON3vw6AOYHXCnb6jeEOyFKKNK+UBKQwsScF9ljjtIsBnc
    eNfji//ezFBnukrNRYbyfBwUgGRWjVrupyL0YIrVLQKBgQC3WUOQMYFb2MgMYE/pL/sccvXoqM77gkvs
    aJ8IehBlnRKHkIdhSjg2op+ePVmskF+3Hj+V6d+S1G6tnXFjLNNfcArA+3XzZ27QDGHtU+fjvQ2s9Y4B
    1W5vQdUDxN8OjXb+i5rODJr21ZkZFoSPuM47KJdjqqsUdRfHbE0oiRHjKQKBgGFxJLYFLAG7dsgo7EdL
    oSD9uU6M3naW9bd0FCezuaMo/4y5kH25dTohqK9nvjzhW2YYeMtGDF2dcOZN+N4mHN+gSWPCr4BnN/0i
    tjPOZ+RsiUTwJganes/BZ6epFqVG0DBzzuvGv2yvrnKCva27wgAcsnn8MZQJxVQL6mHaBYslAoGBALJ0
    o5o5VRs8iJnjNGRXWyZ5jngBtlR+ob9cpU3u4P7GPz7LoblEMCqdZpbyR9H6Q+9L9b/Ift++/Grnj3Fk
    M+f0ecMT/d4HkofVRYtl25KCdEkgSDPotTB89wRQHntfna5r2yUqh7owdG9Cx4vL6I8UFyXe+91Riu+D
    riYCI/LhAoGBAME2QZ3DwegOJYtP1/cHhtXqSjMEQ6SSjuNNvITMZ0hGyNedu0cNtp7RMGHLzyZnziv6
    1DxcRLRvt9JYZFSyJ7j29I1xclPfwOGHmFS84+q2OAdUmu9Eq71eSGGQHd6lmpb3Xoiiv3VpAfKLEvkk
    XMwYAwHxzIPy/QsMU4+Qn8Ls
""".trimIndent()

android {
    namespace = "site.unclefish.wearmixue"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "site.unclefish.wearmixue"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "MIXUE_PRIVATE_KEY",
            localSecret("mixue.privateKey", "mixuePrivateKey", "MIXUE_PRIVATE_KEY")
                .ifBlank { builtInMixuePrivateKey }
                .asBuildConfigString()
        )
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.expressive)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(project(":core"))
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

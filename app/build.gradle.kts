import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.allinonereminder"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.allinonereminder"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing. Two sources, checked in this order:
    //  1. keystore.properties in the repo root (local builds; gitignored, see keystore.properties.example)
    //  2. KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD env vars (CI)
    // With neither, the release build falls back to the debug key (app-<version>-debugsigned.apk).
    val keystoreProps = java.util.Properties().also { props ->
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { props.load(it) }
    }
    val propsStoreFile = keystoreProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
    val propsReady = propsStoreFile != null && propsStoreFile.exists() &&
            !keystoreProps.getProperty("storePassword").isNullOrBlank() &&
            !keystoreProps.getProperty("keyAlias").isNullOrBlank() &&
            !keystoreProps.getProperty("keyPassword").isNullOrBlank()
    val envReady = !System.getenv("KEYSTORE_BASE64").isNullOrBlank() &&
            !System.getenv("KEYSTORE_PASSWORD").isNullOrBlank() &&
            !System.getenv("KEY_ALIAS").isNullOrBlank() &&
            !System.getenv("KEY_PASSWORD").isNullOrBlank()

    signingConfigs {
        create("release") {
            if (propsReady) {
                storeFile = propsStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            } else if (envReady) {
                val keystoreFile = File(layout.buildDirectory.asFile.get(), "release.keystore")
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(Base64.getDecoder().decode(System.getenv("KEYSTORE_BASE64")))
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    val isReleaseSigned = propsReady || envReady

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (isReleaseSigned) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (output != null && buildType.name == "release") {
                output.outputFileName = if (isReleaseSigned) {
                    "app-${versionName}.apk"
                } else {
                    "app-${versionName}-debugsigned.apk"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        checkTestSources = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:schedule"))
    implementation(project(":core:data"))
    implementation(project(":core:gamify"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}


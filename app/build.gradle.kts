import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// ── Auto-incrementing version ────────────────────────────────────────────────
// version.properties holds BUILD_INDEX, a running build counter starting at 0.
// versionCode = BUILD_INDEX + 1.  versionName is derived as major.minor where
// minor rolls 0-9 then bumps major: 1.0, 1.1 … 1.9, 2.0, 2.1 …
// Each APK build (debug or release) bumps BUILD_INDEX (see the bumpVersion task below).
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}
val buildIndex = (versionProps.getProperty("BUILD_INDEX") ?: "0").toInt()
val computedVersionCode = buildIndex + 1
val computedVersionName = "${1 + buildIndex / 10}.${buildIndex % 10}"

android {
    namespace = "com.example.myhrms"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myhrms"
        minSdk = 24
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

    // Name the built APK "JuteErp.apk" (per build type folder)
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "JuteErp.apk"
        }
    }
}

// Persist the next BUILD_INDEX after a release build so the version auto-advances.
tasks.register("bumpVersion") {
    doLast {
        versionProps.setProperty("BUILD_INDEX", (buildIndex + 1).toString())
        versionPropsFile.outputStream().use {
            versionProps.store(it, "Auto-incrementing build version")
        }
        println("Version bumped to code=${buildIndex + 2}, next name will roll from $computedVersionName")
    }
}

tasks.whenTaskAdded {
    if (name == "packageRelease" || name == "packageDebug") finalizedBy("bumpVersion")
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.15.0")
        force("androidx.core:core-ktx:1.15.0")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.cardview)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
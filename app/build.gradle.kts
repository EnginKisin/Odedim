plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseVersionName = "1.0.1"
val releaseKeystore = rootProject.layout.projectDirectory.file(".signing/odedim-release.p12")
val releasePassword = providers.fileContents(
    rootProject.layout.projectDirectory.file(".signing/keystore-password.txt")
).asText.orNull?.trim()

android {
    namespace = "com.example.odedim"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.odedim"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore.asFile
            storeType = "PKCS12"
            storePassword = releasePassword
            keyAlias = "odedim"
            keyPassword = releasePassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Keep release behavior aligned with the working unoptimized build
            // while the device-specific startup crash is being investigated.
            optimization {
                enable = false
            }
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Keep the APK that will be shared separate from local signing credentials.
tasks.register<Copy>("exportReleaseApk") {
    group = "distribution"
    description = "Builds the signed release APK and copies it into the releases folder."
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("app-release.apk")
        rename("app-release.apk", "Odedim-$releaseVersionName.apk")
    }
    into(rootProject.layout.projectDirectory.dir("releases"))
}

dependencies {
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
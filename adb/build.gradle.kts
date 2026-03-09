import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "me.binhmod.adb"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a") // can remove this
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none" // 
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
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

    buildFeatures {
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildToolsVersion = "36.0.0"
    ndkVersion = "29.0.14033849"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation.jvm)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")

    implementation(libs.boringssl)
    implementation(libs.libcxx)

    implementation(libs.rikka.hidden.compat)
    compileOnly(libs.rikka.hidden.stub)
    implementation("dev.rikka.rikkax.core:core-ktx:1.4.1")
}
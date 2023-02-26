import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
}

kotlin {
    val coreVersion = "1.9.0"
    android()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "14.1"
        framework {
            baseName = "shared"
        }
        pod("WebRTC-SDK",moduleName = "WebRTC", version = "~> 104.5112.11")
    }

//    val packForXcode by tasks.creating(Sync::class) {
//        group = "build"
//        val mode = System.getenv("CONFIGURATION") ?: "DEBUG"
//        val sdkName = System.getenv("SDK_NAME") ?: "iphonesimulator"
//        val targetName = "ios" + if (sdkName.startsWith("iphoneos")) "Arm64" else "X64"
//        val framework = kotlin.targets.getByName<KotlinNativeTarget>(targetName).binaries.getFramework(mode)
//        inputs.property("mode", mode)
//        dependsOn(framework.linkTask)
//        val targetDir = File(buildDir, "xcode-frameworks")
//        from({ framework.outputDirectory })
//        into(targetDir)
//    }
//    tasks.getByName("build").dependsOn(packForXcode)
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                api("com.github.webrtc-sdk:android:104.5112.07")
                implementation("androidx.core:core:$coreVersion")
            }
        }
        val androidTest by getting

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }
}

android {
    namespace = "org.webrtc.kmm"
    compileSdk = 33
    defaultConfig {
        minSdk = 21
        targetSdk = 33
    }
}
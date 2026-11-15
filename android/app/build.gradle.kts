import org.gradle.api.tasks.Copy

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val generatedAssetsDir = layout.buildDirectory.dir("generated/mediapipeAssets")
val verifyPhoneDetectorModel by tasks.registering {
    val modelFile = file("src/main/assets/efficientdet_lite0.tflite")
    doFirst {
        if (!modelFile.isFile) {
            throw GradleException(
                "Download efficientdet_lite0.tflite as described in android/README.md"
            )
        }
    }
}
val copyFaceLandmarkerModel by tasks.registering(Copy::class) {
    val modelFile = rootProject.file("../face_landmarker.task")
    from(modelFile)
    into(generatedAssetsDir)
    doFirst {
        if (!modelFile.isFile) {
            throw GradleException(
                "The repository's face_landmarker.task was not found at ${modelFile.absolutePath}"
            )
        }
    }
}

android {
    namespace = "com.smartstudymonitor.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartstudymonitor.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets.getByName("main").assets.srcDir(generatedAssetsDir)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.named("preBuild").configure {
    dependsOn(copyFaceLandmarkerModel, verifyPhoneDetectorModel)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.21")
}

import com.google.protobuf.gradle.*

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id(libs.plugins.jetbrains.kotlin.kapt.get().pluginId)
    id(libs.plugins.google.protobuf.get().pluginId)
}

android {
    namespace = "co.cobli.healthmonitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.cobli.healthmonitor"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.create("release") {
                keyAlias = "sprd_release"
                keyPassword = "123456"
                storeFile = file("../signature/sprd_release.jks")
                storePassword = "123456"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.0"
    }
    sourceSets["main"].proto {
        srcDir("$rootDir/schema-registry/protos/cameraMessageProtocol")
    }
    sourceSets["main"].java {
        srcDirs("build/generated/source/proto/main/java")
    }
    sourceSets["main"].kotlin {
        srcDirs("build/generated/source/proto/main/kotlin")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.runtime.rxjava2)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.aws.android.sdk.core)
    implementation(libs.aws.android.sdk.s3)
    implementation(libs.grpc.grpc.stub)
    implementation(libs.grpc.grpc.kotlin.stub)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.gson)
    implementation(libs.material)
    implementation(libs.protobuf.kotlin.lite)

    annotationProcessor(libs.androidx.room.compiler)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("java") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
        generateProtoTasks {
            all().forEach {
                it.plugins {
                    create("java") {
                        option("lite")
                    }
                    create("grpc") {
                        option("lite")
                    }
                    create("grpckt") {
                        option("lite")
                    }
                }
                it.builtins {
                    create("kotlin") {
                        option("lite")
                    }
                }
            }
        }
    }
}
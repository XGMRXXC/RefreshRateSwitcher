import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "com.dsh.refreshswitch"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.dsh.refreshswitch"
        minSdk = 29
        targetSdk = 36
        versionCode = 4215
        versionName = "4.2.1.5"
    }

    signingConfigs {
        create("dsh") {
            storeFile = file("D:/android-build/dsh.keystore")
            storePassword = "dsh123456"
            keyAlias = "dsh"
            keyPassword = "dsh123456"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("dsh")
        }
        debug {
            signingConfig = signingConfigs.getByName("dsh")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module",
        )
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    // 现代 API（libXposed 102）：编译期依赖，运行时由 LSPosed 提供

    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.8.8")
    // MIUIX 的 SuperDropdown / 原生 popup 需要它（miuix 未做 api 传递）
    implementation("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.0.1")

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.animation)
    // 直接用 androidx material3，才能使用 MaterialExpressiveTheme / ShortNavigationBar 等表达性 API
    implementation("androidx.compose.material3:material3:1.4.0")

    // 固定在 compileSdk 36 / AGP 8.x 可用的一组 AndroidX 版本
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.savedstate:savedstate:1.3.0")
    implementation("androidx.core:core-ktx:1.17.0")
}

configurations.all {
    // 排除 CMP 的 material3 包装（它把表达性 API 标为 internal），统一用 androidx material3
    exclude(group = "org.jetbrains.compose.material3")
    resolutionStrategy {
        force(
            "androidx.core:core:1.17.0",
            "androidx.core:core-ktx:1.17.0",
            "androidx.activity:activity:1.11.0",
            "androidx.activity:activity-compose:1.11.0",
            "androidx.lifecycle:lifecycle-common:2.9.4",
            "androidx.lifecycle:lifecycle-runtime:2.9.4",
            "androidx.lifecycle:lifecycle-runtime-ktx:2.9.4",
            "androidx.lifecycle:lifecycle-runtime-compose:2.9.4",
            "androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4",
            "androidx.lifecycle:lifecycle-viewmodel:2.9.4",
            "androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4",
            "androidx.savedstate:savedstate:1.3.0",
            "androidx.annotation:annotation:1.9.1",
        )
    }
}

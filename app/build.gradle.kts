plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Counts total commits (e.g., 145)
fun getGitVersionCode(): Int {
    if (project.hasProperty("versionCode")) {
        return project.property("versionCode").toString().toInt()
    }
    return providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir(rootDir)
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.get()
}

// Gets the latest tag (e.g., "1.0.4"), or commit hash if no tag exists
// follow https://semver.org/
fun getGitVersionName(): String {
    if (project.hasProperty("versionName")) {
        return project.property("versionName").toString()
    }
    return providers.exec {
        commandLine("git", "describe", "--tags", "--always")
        workingDir(rootDir)
        isIgnoreExitValue = true
    }.standardOutput.asText.map {
        it.trim()
            .removePrefix("v")
            .ifEmpty { "1.0.0-dev" }
    }.get()
}

android {
    namespace = "io.github.airdaydreamers.melddrive"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.airdaydreamers.melddrive"
        minSdk = 31
        versionCode = getGitVersionCode()
        versionName = getGitVersionName()

        testInstrumentationRunner = "io.github.airdaydreamers.melddrive.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            storeFile =
                file(
                    System.getenv("KEYSTORE_PATH")
                        ?: "../release_key_melddrive.jks",
                )
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: project.findProperty("RELEASE_STORE_PASSWORD") as String?
            keyAlias = System.getenv("KEY_ALIAS")
                ?: project.findProperty("RELEASE_KEY_ALIAS") as String?
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: project.findProperty("RELEASE_KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.jvmArgs("-Xshare:off")
        }
    }
}

tasks.register("printVersion") {
    group = "help"
    description = "Prints the version code and version name of the application"
    val vCode = android.defaultConfig.versionCode
    val vName = android.defaultConfig.versionName
    doLast {
        println("Version Code: $vCode")
        println("Version Name: $vName")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.savedstate.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.smbj)
    implementation(libs.smbj.rpc)
    implementation(libs.slf4j.api)
    implementation(libs.tink.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.wsc)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.slf4j.simple)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// KoverReport configuration for code coverage
kover {
    reports {
        total {
            html {
                onCheck = true
            }
            verify {
                onCheck = false // Only check coverage in CI, not locally
                rule {
                    minBound(30) // 30% minimum code coverage threshold but the goal is 60%
                }
            }
        }
    }
}

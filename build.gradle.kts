import io.gitlab.arturbosch.detekt.Detekt

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

allprojects {
    pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
        dependencies {
            "detektPlugins"(libs.detekt.rules.libraries)
            "detektPlugins"(libs.detekt.rules.ruleauthors)
        }

        detekt {
            buildUponDefaultConfig = true
            parallel = true
            autoCorrect = true
            config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        }

        tasks.withType<Detekt>().configureEach {
            reports {
                ignoreFailures = false
                html.required.set(true) // observe findings in your browser with structure and code snippets
                sarif.required.set(true) // standardized SARIF format (https://sarifweb.azurewebsites.net/) to support integrations with GitHub Code Scanning
            }
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget = JavaVersion.VERSION_17.toString()
        }
    }

    pluginManager.withPlugin("com.diffplug.spotless") {
        spotless {
            kotlin {
                target("**/*.kt")
                targetExclude("**/build/**/*.kt")
                ktlint("1.8.0")
                    .editorConfigOverride(
                        mapOf(
                            "android" to true,
                            "indent_size" to "4",
                            "continuation_indent_size" to "4",
                            "ktlint_standard_no-wildcard-imports" to "disabled",
                            "ktlint_standard_function-naming" to "disabled",
                            "ktlint_standard_property-naming" to "disabled",
                            "ktlint_standard_package-name" to "disabled",
                        ),
                    )
            }
            kotlinGradle {
                target("*.gradle.kts", "**/*.gradle.kts")
                targetExclude("**/build/**/*.gradle.kts")
                ktlint("1.8.0")
            }
        }
    }
}

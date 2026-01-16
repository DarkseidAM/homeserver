import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibraryKmp) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.sonarqube) apply true
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

apply(plugin = "org.jlleitschuh.gradle.ktlint")

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    // Root usually only has .kts files, so we don't need Android checks here
    android.set(false)
    debug.set(true)
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(true)

        // Ensure it runs on your Android/KMP sources
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        reporters {
            reporter(ReporterType.CHECKSTYLE) // Sonar understands Checkstyle format
            reporter(ReporterType.HTML) // For you to read locally
        }
    }

    configure<DetektExtension> {
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        source.setFrom(files("src"))
        // Setup report generation so Sonar can read it
        buildUponDefaultConfig = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            xml.required.set(true) // Required for SonarCloud
            html.required.set(true) // Nice for human reading
            txt.required.set(false)
        }
    }
}

sonar {
    properties {
        property("sonar.projectKey", "DarkseidAM_homeserver")
        property("sonar.organization", "darkseidam")

        property("sonar.kotlin.detekt.reportPaths", "build/reports/detekt/detekt.xml")
    }
}

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("java")
    id("jacoco")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij)
    alias(libs.plugins.sonarqube)

    alias(libs.plugins.version.catalog.update)
}

val isWindowsHost = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val livyBuildSearchableOptions = providers.gradleProperty("livyBuildSearchableOptions")
    .map(String::toBoolean)
    .getOrElse(false)
val basePluginVersion = "1.5.5"
val pluginVersionSuffix = providers.gradleProperty("pluginVersionSuffix").orNull?.trim().orEmpty()
val marketplacePublishingToken = providers.gradleProperty("intellijPlatformPublishingToken")
    .orElse(providers.environmentVariable("ORG_GRADLE_PROJECT_intellijPlatformPublishingToken"))
    .orElse(providers.environmentVariable("PUBLISH_TOKEN_PLUGIN"))
    .orElse(providers.environmentVariable("PUBLISH_TOKEN"))


group = "com.queukat"
version = if (pluginVersionSuffix.isBlank()) {
    basePluginVersion
} else {
    "$basePluginVersion-$pluginVersionSuffix"
}

repositories {
    mavenCentral()
}

jacoco {
    toolVersion = "0.8.13"
}

sonar {
    properties {
        property("sonar.projectKey", "com.queukat:livy_new")
        property("sonar.projectName", "livy_new")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.absolutePath
        )
        property(
            "sonar.coverage.exclusions",
            listOf(
                "src/main/kotlin/com/queukat/livy_new/bottompanel/**",
                "src/main/kotlin/com/queukat/livy_new/editor/**",
                "src/main/kotlin/com/queukat/livy_new/*Action.kt",
                "src/main/kotlin/com/queukat/livy_new/*Dialog.kt",
                "src/main/kotlin/com/queukat/livy_new/*Factory.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyAuthSessionStore.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyAuthUi.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyBackground.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyBrowserAuthDialog.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyClientProvider.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyConsoleLauncher.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyPluginConfigurable.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyProfileSelection.kt",
                "src/main/kotlin/com/queukat/livy_new/LivySourceContext.kt",
                "src/main/kotlin/com/queukat/livy_new/LivyStatementDetailsDialog.kt",
                "src/main/kotlin/com/queukat/livy_new/ShowStatementsDialog.kt"
            ).joinToString(",")
        )
    }
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set(libs.versions.intellijPlatform.get())
    type.set("IC")
    plugins.set(listOf())
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.json)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    named<Test>("test") {
        useJUnitPlatform {
            excludeTags("screenshots")
        }
        extensions.configure<JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
        finalizedBy(named("jacocoTestReport"))
    }

    named<JacocoReport>("jacocoTestReport") {
        dependsOn(named("test"))
        classDirectories.setFrom(layout.buildDirectory.dir("instrumented/instrumentCode"))
        sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    named<org.jetbrains.intellij.tasks.BuildSearchableOptionsTask>("buildSearchableOptions") {
        enabled = livyBuildSearchableOptions && !isWindowsHost
    }

    named<org.jetbrains.intellij.tasks.JarSearchableOptionsTask>("jarSearchableOptions") {
        enabled = livyBuildSearchableOptions && !isWindowsHost
    }

    val testSourceSet = the<SourceSetContainer>()["test"]

    register<Test>("generateScreenshots") {
        description = "Generate user-facing plugin screenshots into docs/screenshots."
        group = "documentation"
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        useJUnitPlatform {
            includeTags("screenshots")
        }
        systemProperty(
            "java.util.prefs.PreferencesFactory",
            "com.queukat.livy_new.testsupport.InMemoryPreferencesFactory"
        )
        systemProperty(
            "livy.screenshots.outputDir",
            layout.projectDirectory.dir("docs/screenshots").asFile.absolutePath
        )
        outputs.dir(layout.projectDirectory.dir("docs/screenshots"))
    }


    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("")
    }

    verifyPluginConfiguration {
        pluginVerifierDownloadDir.set(
            layout.buildDirectory.dir("pluginVerifier/downloads").map { it.asFile.absolutePath }
        )
    }

    runPluginVerifier {
        ideVersions.set(listOf("IU-261.23567.138"))
        failureLevel.set(
            listOf(
                org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel.DEPRECATED_API_USAGES,
                org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
                org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel.NOT_DYNAMIC
            )
        )
        downloadDir.set(
            layout.buildDirectory.dir("pluginVerifier/downloads").map { it.asFile.absolutePath }
        )
        verificationReportsDir.set(
            layout.buildDirectory.dir("reports/pluginVerifier").map { it.asFile.absolutePath }
        )
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(marketplacePublishingToken)
    }
}

import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij)

    alias(libs.plugins.version.catalog.update)
}


group = "com.queukat"
version = "1.4"

repositories {
    mavenCentral()
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
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

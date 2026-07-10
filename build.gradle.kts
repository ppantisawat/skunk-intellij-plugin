import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.jvm.tasks.Jar

plugins {
    scala
    id("org.jetbrains.intellij.platform")
}

val pluginId: String = providers.gradleProperty("pluginId").get()
val pluginName: String = providers.gradleProperty("pluginName").get()
val pluginRepositoryUrl: String = providers.gradleProperty("pluginRepositoryUrl").get()
val platformVersion: String = providers.gradleProperty("platformVersion").get()
val scalaPluginVersion: String = providers.gradleProperty("scalaPluginVersion").get()
val scalaVersion: String = providers.gradleProperty("scalaVersion").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("org.scala-lang:scala-library:$scalaVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.scala-lang:scala-reflect:$scalaVersion")

    intellijPlatform {
        intellijIdea(platformVersion)
        bundledPlugin("com.intellij.java")
        plugin("org.intellij.scala", scalaPluginVersion)
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        id.set(pluginId)
        name.set(pluginName)
        version.set(project.version.toString())

        description.set(
            """
            Adds IntelliJ Scala type inference support for Skunk's Scala 2 sql interpolator.
            """.trimIndent()
        )

        changeNotes.set(
            """
            Initial version with Skunk 1.0.0 Scala 2 sql interpolator support.
            """.trimIndent()
        )

        ideaVersion {
            sinceBuild.set("261")
            untilBuild.set("261.*")
        }

        vendor {
            name.set("Pakanon Pantisawat")
            url.set(pluginRepositoryUrl)
        }
    }

    publishing {
        token.set(providers.gradleProperty("intellijPlatformPublishingToken"))
    }

    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }

    pluginVerification {
        ides {
            current()
            recommended()
        }
    }
}

tasks {
    val pluginJar = named<Jar>("jar").flatMap { jar: Jar -> jar.archiveFile }

    test {
        dependsOn(pluginJar)
        useJUnit()
        systemProperty("idea.force.use.core.classloader", "true")
        jvmArgumentProviders.add(
            objects.newInstance<ExtensionJarArgumentProvider>().apply {
                extensionJar.set(pluginJar)
            }
        )
    }
}

abstract class ExtensionJarArgumentProvider : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val extensionJar: RegularFileProperty

    override fun asArguments(): Iterable<String> =
        listOf("-Dskunk.intellij.test.extensionJar=${extensionJar.get().asFile.absolutePath}")
}

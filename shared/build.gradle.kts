import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.security.SecureRandom
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val jasperGeneratedDir = layout.buildDirectory.dir("generated/source/jasper/kotlin")
val jasperSecretsPath = rootProject.layout.projectDirectory.file("secrets/jasper.local.properties").asFile

val generateJasperEmbeddedConfig by tasks.registering {
    description = "Embeds obfuscated Jasper secrets from gitignored local properties"
    inputs.file(jasperSecretsPath).optional()
    outputs.dir(jasperGeneratedDir)
    // Local secrets + random mask are execution-time only.
    notCompatibleWithConfigurationCache("Reads local Jasper secrets and writes obfuscated embed at execution time")
    doLast {
        fun escapeKotlinString(value: String): String = buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '$' -> append("\\$")
                    else -> append(ch)
                }
            }
            append('"')
        }

        fun ByteArray.toKotlinIntList(): String =
            joinToString(prefix = "intArrayOf(", postfix = ")") { b ->
                (b.toInt() and 0xFF).toString()
            }

        val props = Properties()
        if (jasperSecretsPath.exists()) {
            jasperSecretsPath.inputStream().use { props.load(it) }
        }
        fun prop(name: String): String = props.getProperty(name)?.trim().orEmpty()

        val apiKey = prop("jasper.api.key")
        val modelId = prop("jasper.model.id")
        val chatPath = prop("jasper.chat.path").ifBlank { "/v1/chat/completions" }
        val modelsPath = prop("jasper.models.path").ifBlank { "/v1/models" }

        val random = SecureRandom()
        val mask = ByteArray(48).also { random.nextBytes(it) }
        val plain = apiKey.encodeToByteArray()
        val obfuscated = ByteArray(plain.size) { i ->
            (plain[i].toInt() xor mask[i % mask.size].toInt()).toByte()
        }

        val configured = apiKey.isNotBlank() && modelId.isNotBlank() && chatPath.isNotBlank()
        val outRoot = jasperGeneratedDir.get().asFile
        val pkgDir = outRoot.resolve("com/example/personalledger/data/ai/jasper")
        pkgDir.mkdirs()
        val outFile = pkgDir.resolve("JasperEmbeddedSecrets.kt")
        // Never write the plaintext key into this generated source.
        outFile.writeText(
            """
            |package com.example.personalledger.data.ai.jasper
            |
            |/**
            | * Build-time generated. Plaintext API key is never stored in this file.
            | * XOR obfuscation only raises the bar for casual extraction; the decode mask
            | * ships in the same binary, so this is NOT strong secret protection.
            | */
            |internal object JasperEmbeddedSecrets {
            |    const val isConfigured: Boolean = $configured
            |    const val modelId: String = ${escapeKotlinString(modelId)}
            |    const val chatPath: String = ${escapeKotlinString(chatPath)}
            |    const val modelsPath: String = ${escapeKotlinString(modelsPath)}
            |
            |    private val mask: IntArray = ${mask.toKotlinIntList()}
            |    private val payload: IntArray = ${obfuscated.toKotlinIntList()}
            |
            |    fun apiKeyOrNull(): String? {
            |        if (!isConfigured || payload.isEmpty()) return null
            |        val bytes = ByteArray(payload.size) { i ->
            |            (payload[i] xor mask[i % mask.size]).toByte()
            |        }
            |        return bytes.decodeToString().takeIf { it.isNotBlank() }
            |    }
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "com.example.personalledger.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(jasperGeneratedDir)
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.coroutines)
                implementation(libs.multiplatform.settings.noarg)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.json)
            }
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateJasperEmbeddedConfig)
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

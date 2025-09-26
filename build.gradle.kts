@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.2.0"
}

group = "nl.helico"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

/*dependencies {
    implementation(ktorLibs.network)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {


    jvmToolchain(21)
}*/

kotlin {

    jvm()

    macosArm64()

    js {
        useEsModules()
        nodejs()
        binaries.executable()
    }

    wasmJs {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(ktorLibs.network)
            }
        }

        jvmTest {
            dependencies {
                implementation(project.dependencies.platform("org.testcontainers:testcontainers-bom:1.21.3"))
                implementation("org.testcontainers:postgresql")
                implementation("org.testcontainers:junit-jupiter")
                implementation(kotlin("test"))
            }
        }
    }
}

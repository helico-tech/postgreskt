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

kotlin {

    jvm {
    }

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
    }
}

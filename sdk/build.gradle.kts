description = "epotheke SDK Package"

plugins {
    id("epotheke.lib-jvm-conventions")
    id("epotheke.lib-android-conventions")
    //id("epotheke.lib-ios-conventions")
    id("epotheke.publish-conventions")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.logging)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.websocket)
                implementation(libs.kotlin.coroutines.core)
                implementation(libs.kotlin.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.test.basics)
            }
        }
        val androidMain by getting {
            dependencies {
                api(libs.oec.android)
                implementation(libs.kotlin.coroutines.android)
            }
        }
//        val iosMain by getting {
//            dependencies {
//                implementation(libs.ktor.client.darwin)
//            }
//        }
    }

//    cocoapods {
//        name = "epotheke-sdk"
//        homepage = "https://www.epotheke.com"
//        summary = "Framework for using the CardLink protocol."
//        authors = "ecsec GmbH"
//        license = "GPLv3"
//        framework {
//            baseName = "epotheke"
//            binaryOption("bundleId", "com.epotheke.sdk")
//        }
//
//        pod("open-ecard") {
//            // TODO: use version from catalogue
//            version = "2.1.11"
//            //source = path("/Users/florianotto/pod")
//            ios.deploymentTarget = "13.0"
//            moduleName = "OpenEcard"
//        }
//    }
}

android {
    namespace = "com.epotheke"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }

    packaging {
        resources.excludes.add("cif-repo/repo-config.properties")
    }

    publishing {
        singleVariant("release") {
            // if you don't want sources/javadoc, remove these lines
            withSourcesJar()
            withJavadocJar()
        }
    }
}
publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

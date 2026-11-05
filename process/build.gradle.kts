plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.vibecoderspace.linux.process"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 26
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

group = "com.github.vibecoderspace"
version = System.getenv("VERSION") ?: "0.2.0"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.vibecoderspace"
                artifactId = "linux-android-process"
                version = project.version.toString()
            }
        }
    }
}

dependencies {
    api(project(":linux"))
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
}

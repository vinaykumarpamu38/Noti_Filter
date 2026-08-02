plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.techy.noti_filter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techy.noti_filter"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.0001"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    buildTypes {

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Room Core
    implementation ("androidx.room:room-runtime:2.8.4")

    // Annotation Processor (Java)ui
    annotationProcessor ("androidx.room:room-compiler:2.8.4")

    // Optional (recommended for debugging / testing)
    implementation ("androidx.room:room-testing:2.8.4")

    // Lifecycle (optional but useful)
    implementation ("androidx.lifecycle:lifecycle-runtime:2.10.0")

    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    implementation("com.opencsv:opencsv:5.12.0")

    implementation("com.google.android.material:material:1.11.0")

    // Phase 4: Google Sign-In + Drive API (drive.file scope only - see Phase 4 setup notes)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client-gson:1.44.2") {
        exclude(group = "org.apache.httpcomponents")
    }

    // Firebase App Check - protects the Phase 5 Cloud Function from being
    // called by anything other than a genuine build of this app
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // Weekly scheduled training trigger
    implementation("androidx.work:work-runtime:2.9.1")
}
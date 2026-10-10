import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hang2515.personalcalendar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hang2515.personalcalendar"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.2"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.hang2515.personalcalendar"
        val cloudSettings = Properties().apply {
            val file = rootProject.file("cloud-config.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }
        listOf("CLOUD_API_URL", "CLOUD_AUTH_DOMAIN", "CLOUD_CLIENT_ID").forEach { key ->
            val value = cloudSettings.getProperty(key, "")
            require(!value.contains('"') && !value.contains('\\') && !value.contains('\n')) { "云服务配置格式无效" }
            buildConfigField("String", key, "\"$value\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("net.openid:appauth:0.11.1")
    ksp("androidx.room:room-compiler:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.work:work-testing:2.12.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

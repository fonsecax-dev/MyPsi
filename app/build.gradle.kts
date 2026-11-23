import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// chave de assinatura guardada no projeto (assinatura/, fora do git): a mesma para teste e versão final,
// para as atualizações instalarem sempre por cima sem perder os dados. Sem ela, compila com a chave de teste do PC.
val ficheiroAssinatura = rootProject.file("assinatura/assinatura.properties")
val assinatura = Properties().apply { if (ficheiroAssinatura.exists()) ficheiroAssinatura.inputStream().use { load(it) } }

android {
    namespace = "com.aodaci.mypsi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aodaci.mypsi"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }
    signingConfigs {
        if (ficheiroAssinatura.exists()) create("mypsi") {
            storeFile = rootProject.file("assinatura/" + assinatura.getProperty("storeFile"))
            storePassword = assinatura.getProperty("storePassword")
            keyAlias = assinatura.getProperty("keyAlias")
            keyPassword = assinatura.getProperty("keyPassword")
        }
    }
    val chave = signingConfigs.findByName("mypsi") ?: signingConfigs.getByName("debug")
    buildTypes {
        getByName("debug") { signingConfig = chave }
        getByName("release") {
            signingConfig = chave
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // org.json real nos testes JVM (no Android vem da plataforma)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.vaivan"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.vaivan"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
// Exporta o esquema do banco local (pasta app/schemas) a cada compilação. Esses arquivos
// JSON são a "foto" de cada versão e DEVEM ser versionados no Git: o Room usa a foto para
// conferir se as migrações estão certas.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// O Navigation SDK traz embutida sua própria cópia das classes do Maps SDK
// (com.google.android.gms.maps.*). Excluímos o play-services-maps de qualquer
// dependência transitiva (ex.: Places) para não duplicar essas classes no APK.
configurations.all {
    exclude(group = "com.google.android.gms", module = "play-services-maps")
}

dependencies {

    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0") // pro .await()

    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")

    // Alinhado com a versão do catálogo (androidx.camera.view / cameraView = 1.6.2),
    // para não misturar módulos do CameraX em versões diferentes.
    val camerax_version = "1.6.2"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation(libs.androidx.camera.view)

    // Google Navigation SDK - navegação turno-a-turno dentro do app do motorista.
    // Requer a Navigation SDK habilitada (com billing) no mesmo projeto Google Cloud
    // da API key já usada pelo Maps SDK/Routes API/Places.
    implementation("com.google.android.libraries.navigation:navigation:8.0.0")

    // Places SDK for Android (New) - a partir da 5.x o SDK fala só com a Places API (New).
    // Fixado em 5.3.0 (não a mais recente, 6.0.2): essa é a versão mais nova cujo
    // kotlin-stdlib embutido (2.3.0) ainda é compatível com o compilador Kotlin do
    // projeto (2.2.10, que só lê metadata até 2.3.0) - 6.0.2 usa stdlib 2.4.0 e
    // quebra a compilação inteira (até "listOf"/"mapOf" param de resolver).
    implementation("com.google.android.libraries.places:places:5.3.0")
    // play-services-maps NÃO é declarado diretamente: o Navigation SDK já traz embutida
    // uma cópia compatível das classes do Maps SDK (com.google.android.gms.maps.*), e
    // declarar as duas juntas gera classes duplicadas na hora de montar o APK.
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.9.0")

    // Glide - carregamento das fotos reais dos passageiros (foto do Firebase
    // Storage) no roteiro de navegação; uso básico só, sem AppGlideModule/KSP.
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.google.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
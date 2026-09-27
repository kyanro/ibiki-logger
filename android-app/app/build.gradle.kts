plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.kyanro.ibiki_logger"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.kyanro.ibiki_logger"
        minSdk = 29
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

abstract class LicenseAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val notices: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyNotices() {
        val directory = outputDirectory.dir("licenses").get().asFile.apply { mkdirs() }
        notices.files.forEach { it.copyTo(directory.resolve(it.name), overwrite = true) }
    }
}

// Keep one editable copy of each notice and include it in every APK.
val licenseAssets by tasks.registering(LicenseAssetsTask::class) {
    notices.from(rootProject.file("../LICENSE"), rootProject.file("../ASSET_LICENSES.md"), rootProject.file("../THIRD_PARTY_NOTICES.md"))
    notices.from(rootProject.fileTree("../third-party-licenses") { include("*.txt") })
    outputDirectory.set(layout.buildDirectory.dir("generated/licenseAssets"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(licenseAssets, LicenseAssetsTask::outputDirectory)
}

dependencies {
  implementation("com.google.ai.edge.litert:litert:1.4.2")
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation("androidx.test:rules:1.7.0")
  androidTestImplementation(libs.androidx.test.espresso.core)

}

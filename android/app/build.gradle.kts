plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.acrylic.outbound.pda"
    compileSdk = 36
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "com.acrylic.outbound.pda"
        minSdk = 28
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0-pairing"
        val serverUrl = providers.gradleProperty("pdaServerUrl").orElse("").get()
        val environment = providers.gradleProperty("pdaEnvironment").orElse("未配置服务环境").get()
        require(!serverUrl.contains('"') && !serverUrl.contains('\\') && !serverUrl.contains('\n'))
        require(!environment.contains('"') && !environment.contains('\\') && !environment.contains('\n'))
        buildConfigField("String", "PDA_SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "PDA_ENVIRONMENT", "\"$environment\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { debug { applicationIdSuffix = ".debug" }; release { isMinifyEnabled = false } }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.inputs.file(rootProject.file("../server/build/pda-contract-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-submission-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-recall-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-continuation-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-disposition-fixture.json"))
            it.inputs.dir(file("schemas/com.acrylic.outbound.pda.data.PdaDatabase"))
            it.systemProperty("pda.contractFixture", rootProject.file("../server/build/pda-contract-fixture.json").absolutePath)
            it.systemProperty("pda.submissionFixture", rootProject.file("../server/build/pda-submission-fixture.json").absolutePath)
            it.systemProperty("pda.recallFixture", rootProject.file("../server/build/pda-recall-fixture.json").absolutePath)
            it.systemProperty("pda.continuationFixture", rootProject.file("../server/build/pda-continuation-fixture.json").absolutePath)
            it.systemProperty("pda.dispositionFixture", rootProject.file("../server/build/pda-disposition-fixture.json").absolutePath)
            it.systemProperty("pda.schemas", file("schemas/com.acrylic.outbound.pda.data.PdaDatabase").absolutePath)
        }
    }
    lint { abortOnError = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.sqlite:sqlite-framework:2.5.0")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

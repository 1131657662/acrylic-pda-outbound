import java.util.Properties

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
        versionCode = providers.gradleProperty("pdaVersionCode").map(String::toInt).orElse(21).get()
        versionName = providers.gradleProperty("pdaVersionName").orElse("0.16.0-r06-release").get()
        val serverUrl = providers.gradleProperty("pdaServerUrl").orElse("").get()
        val environment = providers.gradleProperty("pdaEnvironment").orElse("未配置服务环境").get()
        val updatePublicKey = providers.gradleProperty("pdaUpdatePublicKey").orElse("").get()
        require(!serverUrl.contains('"') && !serverUrl.contains('\\') && !serverUrl.contains('\n'))
        require(!environment.contains('"') && !environment.contains('\\') && !environment.contains('\n'))
        require(!updatePublicKey.contains('"') && !updatePublicKey.contains('\\') && !updatePublicKey.contains('\n'))
        buildConfigField("String", "PDA_SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "PDA_ENVIRONMENT", "\"$environment\"")
        buildConfigField("String", "PDA_UPDATE_PUBLIC_KEY", "\"$updatePublicKey\"")
        buildConfigField("boolean", "TEMPORARY_CONTINUATION_DEVELOPMENT", "false")
        buildConfigField("boolean", "TEMPORARY_HANDOVER_DEVELOPMENT", "false")
        buildConfigField("boolean", "RECOVERED_HANDOVER_DEVELOPMENT", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    testBuildType = providers.gradleProperty("pdaTestBuildType").orElse("debug").get().also {
        require(it in setOf("debug", "staging")) { "Device checks must use a test build" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    // Release signing identity used from R06 onwards. The keystore and its passwords stay outside Git
    // in .local/pda-signing/ (see docs/PDA_SIGNING_GUIDE.md); when the properties file is absent no
    // release signing config is created, so debug/staging builds and tests still work on any machine.
    val signingPropertiesFile = rootProject.file("../.local/pda-signing/signing.properties")
    val signingProperties = Properties()
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use { signingProperties.load(it) }
    val releaseKeystoreFile = signingProperties.getProperty("storeFile")
        ?.let { signingPropertiesFile.parentFile.resolve(it) }
    signingConfigs {
        val keystore = releaseKeystoreFile
        if (keystore != null && keystore.isFile) {
            create("release") {
                storeFile = keystore
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "PDA_UPDATE_CHANNEL", "\"debug\"")
            val isolatedContinuation = providers.gradleProperty("pdaIsolatedTemporaryContinuation").orElse("false").get()
            require(isolatedContinuation in setOf("true", "false"))
            buildConfigField("boolean", "TEMPORARY_CONTINUATION_DEVELOPMENT", isolatedContinuation)
            val isolatedHandover = providers.gradleProperty("pdaIsolatedTemporaryHandover").orElse("false").get()
            require(isolatedHandover in setOf("true", "false"))
            buildConfigField("boolean", "TEMPORARY_HANDOVER_DEVELOPMENT", isolatedHandover)
            val isolatedRecoveredHandover = providers.gradleProperty("pdaIsolatedRecoveredHandover").orElse("false").get()
            require(isolatedRecoveredHandover in setOf("true", "false"))
            buildConfigField("boolean", "RECOVERED_HANDOVER_DEVELOPMENT", isolatedRecoveredHandover)
        }
        release {
            isMinifyEnabled = false
            if (releaseKeystoreFile?.isFile == true) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "PDA_UPDATE_CHANNEL", "\"production\"")
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-test"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "PDA_UPDATE_CHANNEL", "\"staging\"")
            buildConfigField("boolean", "TEMPORARY_CONTINUATION_DEVELOPMENT", "false")
            buildConfigField("boolean", "TEMPORARY_HANDOVER_DEVELOPMENT", "false")
            buildConfigField("boolean", "RECOVERED_HANDOVER_DEVELOPMENT", "false")
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.inputs.property("e2aAndroidHttpPrivate", System.getenv("E2A_ANDROID_HTTP_PRIVATE").orEmpty())
            it.inputs.property("temporarySuccessHttpPrivate", System.getenv("TEMP_SUCCESS_HTTP_PRIVATE").orEmpty())
            it.inputs.property("temporaryContinuationHttpPrivate", System.getenv("TEMP_CONTINUATION_HTTP_PRIVATE").orEmpty())
            it.inputs.property("temporaryArchiveHttpPrivate", System.getenv("TEMP_ARCHIVE_HTTP_PRIVATE").orEmpty())
            it.inputs.property("draftEvidenceHttpPrivate", System.getenv("DRAFT_EVIDENCE_HTTP_PRIVATE").orEmpty())
            it.inputs.property("recoveredHandoverHttpPrivate", System.getenv("RECOVERED_HANDOVER_ANDROID_HTTP_PRIVATE").orEmpty())
            it.inputs.property("recoveredHandoverHttpPhase", System.getenv("RECOVERED_HANDOVER_ANDROID_PHASE").orEmpty())
            val archiveFixture = rootProject.file("../server/build/pda-temporary-archive-fixture.json")
            if (archiveFixture.exists()) it.inputs.file(archiveFixture)
            it.systemProperty("pda.temporaryArchiveFixture", archiveFixture.absolutePath)
            val successFixture = rootProject.file("../server/build/pda-temporary-success-fixture.json")
            if (successFixture.exists()) it.inputs.file(successFixture)
            it.systemProperty("pda.temporarySuccessFixture", successFixture.absolutePath)
            it.inputs.file(rootProject.file("../server/build/pda-contract-fixture.json"))
            it.inputs.file(rootProject.file("../server/src/test/resources/e2a/e2a-v1-empty-set-vector.json"))
            it.systemProperty("pda.e2aVector", rootProject.file("../server/src/test/resources/e2a/e2a-v1-empty-set-vector.json").absolutePath)
            it.inputs.file(rootProject.file("../server/src/test/resources/e2a/e2a-intake-http-vector.json"))
            it.systemProperty("pda.e2aIntakeVector", rootProject.file("../server/src/test/resources/e2a/e2a-intake-http-vector.json").absolutePath)
            it.inputs.file(rootProject.file("../server/build/pda-temporary-contract-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-temporary-audit-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-temporary-submission-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-submission-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-archive-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-audit-archive-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-recall-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-continuation-fixture.json"))
            it.inputs.file(rootProject.file("../server/build/pda-disposition-fixture.json"))
            it.inputs.dir(file("schemas/com.acrylic.outbound.pda.data.PdaDatabase"))
            it.systemProperty("pda.contractFixture", rootProject.file("../server/build/pda-contract-fixture.json").absolutePath)
            it.systemProperty("pda.temporaryFixture", rootProject.file("../server/build/pda-temporary-contract-fixture.json").absolutePath)
            it.systemProperty("pda.temporaryAuditFixture", rootProject.file("../server/build/pda-temporary-audit-fixture.json").absolutePath)
            it.systemProperty("pda.temporarySubmissionFixture", rootProject.file("../server/build/pda-temporary-submission-fixture.json").absolutePath)
            it.systemProperty("pda.submissionFixture", rootProject.file("../server/build/pda-submission-fixture.json").absolutePath)
            it.systemProperty("pda.archiveFixture", rootProject.file("../server/build/pda-archive-fixture.json").absolutePath)
            it.systemProperty("pda.auditArchiveFixture", rootProject.file("../server/build/pda-audit-archive-fixture.json").absolutePath)
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
    testImplementation("androidx.work:work-testing:2.10.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

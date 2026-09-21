import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 版本号来源是 git tag（vX.Y.Z），本地构建与 CI 同一套规则，免得包内自报的版本落后于
// 实际发布的 tag。versionCode 必须单调递增，否则已装用户装不上新版，所以固定用
// X*10000 + Y*100 + Z（v1.0.4 → 10004）。取不到 tag（无 git、无 tag）时回退到兜底值。
// 仍可用 -PappVersionName=1.0.5 -PappVersionCode=10005 覆盖。
val tagVersion: Pair<Int, String>? = runCatching {
    val tag = providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*.[0-9]*.[0-9]*")
    }.standardOutput.asText.get().trim()
    val parts = Regex("""^v(\d+)\.(\d+)\.(\d+)$""").matchEntire(tag)?.groupValues
        ?: error("tag 形如 vX.Y.Z 才认，实际是 $tag")
    val (major, minor, patch) = parts.drop(1).map(String::toInt)
    (major * 10000 + minor * 100 + patch) to tag.removePrefix("v")
}.getOrNull()

android {
    namespace = "cc.openxiot.wematrix"
    compileSdk = 37

    defaultConfig {
        applicationId = "cc.openxiot.wematrix"
        minSdk = 29
        targetSdk = 37
        // 兜底值必须与上面的公式自洽：1.0.3 → 1*10000 + 0*100 + 3 = 10003，别写成 103
        versionCode = (findProperty("appVersionCode") as String?)?.toInt()
            ?: tagVersion?.first
            ?: 10003
        versionName = (findProperty("appVersionName") as String?)
            ?: tagVersion?.second
            ?: "1.0.3"
    }

    signingConfigs {
        // keystore.properties 不入库（见 .gitignore），CI 上不存在。缺了就干脆不建 release 签名，
        // 否则 file("") 会抛 "Cannot convert '' to File."，配置期就失败，assembleDebug 也跟着挂。
        val props = Properties().apply {
            val file = rootProject.file("keystore.properties")
            if (file.exists()) load(FileInputStream(file))
        }
        val storeFilePath = props.getProperty("storeFile")
        if (!storeFilePath.isNullOrBlank()) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            excludes += listOf("META-INF/*")
        }
        resources {
            excludes += listOf(
                "META-INF/*",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/INDEX.LIST",
                "META-INF/native-image/io.netty/transport/native-image.properties",
                "META-INF/native-image/io.netty/transport/reflection-gateway.json",
                "META-INF/native-image/io.netty/codec-http2/native-image.properties",
                "META-INF/native-image/io.netty/codec-http/native-image.properties",
                "META-INF/native-image/io.netty/buffer/native-image.properties",
                "META-INF/native-image/io.netty/handler/native-image.properties",
                "META-INF/native-image/io.netty/common/native-image.properties"
            )
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // QR code scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") {
        exclude(group = "com.google.zxing")
    }
    implementation("com.google.zxing:core:3.5.4")

    // Core
    implementation("androidx.core:core-ktx:1.19.0")

    // Openxiot
    implementation("cc.openxiot:xiot-spec:0.1.9")
    implementation("cc.openxiot:xiot-spec-codec-vertx:0.1.9")
    implementation("cc.openxiot:xiot-support-codegen-vertx:0.1.9")

    // Weixin
    implementation("com.tencent.mm.opensdk:wechat-sdk-android:6.8.40")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 工程里唯一的测试源集：只在 app/src/test 下，钉住 Modbus 帧生成与数量口径的逐字节结果
    // （见 RequestFrameTest）。纯 JVM 计算，不起模拟器。
    testImplementation("junit:junit:4.13.2")
}

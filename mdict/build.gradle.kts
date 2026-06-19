plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// 纯 Kotlin/JVM 词典解析库（MDX/MDD）。不依赖 Android，单测跑在 JVM 上。
// 设计文档：docs/38-mdx-mdd-parser-library-design.md

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.org.jetbrains.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.org.jetbrains.kotlinx.coroutines.test)
    // 单测读 fixtures/manifest.json 做断言基准
    testImplementation(libs.org.jetbrains.kotlinx.serialization.json)
}

tasks.test {
    useJUnit()
}

// 划词查词 Demo 服务（真实词典数据驱动原型 UI）。
// 用法：./gradlew :mdict:runDemo            （默认 test-dict 下的词典）
//      ./gradlew :mdict:runDemo --args="D:/path/to/dict.mdx"
tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "启动划词查词 Demo（http://localhost:8765）"
    mainClass.set("com.shuli.reader.mdict.demo.DictDemoServer")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    standardInput = System.`in`
}


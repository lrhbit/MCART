plugins {
    java
    kotlin("jvm") version "2.0.20"
}

group = "com.axolotl"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // 本地随附的 Swing 外观与布局库
    implementation(files("lib/flatlaf-3.7.1.jar", "lib/miglayout-3.7.4.jar"))
    implementation(kotlin("stdlib"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/java", "src/main/kotlin"))
        }
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// 可直接运行的 fat jar：包含 Kotlin 运行时与本地 Swing 库
tasks.jar {
    archiveBaseName.set("mc-pixel-art-converter")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "com.axolotl.mcart.Main")
    }
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
}

plugins {
    java
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("dev.w0fv1.sample.Main")
}

dependencies {
    // This will be substituted by includeBuild("..") to the parent sources.
    implementation("dev.w0fv1:fmapper:0.0.4")
    annotationProcessor("dev.w0fv1:fmapper:0.0.4")

    // Only for @Entity/@Id annotations in this demo.
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
}

val javacAddExports = listOf(
    "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
)

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Afmapper.inline=true")

    // Processor uses javac internals: run javac in a forked JVM with required exports.
    options.isFork = true
    options.forkOptions.jvmArgs = mutableListOf<String>().apply {
        addAll(javacAddExports.map { "--add-exports=$it" })
    }
}

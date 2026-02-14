plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
}

group = "dev.w0fv1"

// Release versioning:
// - Local/dev: keep a default version.
// - CI/tag build: when pushing a tag like `v0.0.5`, publish version `0.0.5`.
val releaseVersion = run {
    val fromProp = providers.gradleProperty("releaseVersion").orNull
    if (!fromProp.isNullOrBlank()) return@run fromProp

    val refType = System.getenv("GITHUB_REF_TYPE") // "tag" in GitHub Actions tag builds
    val refName = System.getenv("GITHUB_REF_NAME") // the tag name, e.g. "v0.0.5"
    if (refType == "tag" && !refName.isNullOrBlank() && refName.startsWith("v")) {
        return@run refName.substring(1)
    }

    "0.0.4"
}
version = releaseVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {

    implementation("com.google.auto.service:auto-service:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
// https://mvnrepository.com/artifact/jakarta.persistence/jakarta.persistence-api
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")


    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
    testImplementation("org.ow2.asm:asm:9.7.1")
}

tasks.test {
    useJUnitPlatform()
}

val javacAddExports = listOf(
    "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
)

tasks.withType<JavaCompile>().configureEach {
    // Needed to compile javac AST injection helpers (Lombok-style).
    options.compilerArgs.addAll(javacAddExports.flatMap { listOf("--add-exports", it) })
}

tasks.test {
    // Needed to load com.sun.tools.javac.* during compile-testing runs.
    jvmArgs(javacAddExports.map { "--add-exports=$it" })
}

tasks.withType<Javadoc>().configureEach {
    // Avoid Javadoc build failures in CI due to doclint and missing external docs.
    (options as? StandardJavadocDocletOptions)?.apply {
        addBooleanOption("Xdoclint:none", true)
        addStringOption("encoding", "UTF-8")
    }
}


//import org.gradle.plugins.signing.Sign
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("fmapper Library")
                description.set("A compile-time annotation processor that injects MyEntity.FieldMapper into @Entity classes.")
                url.set("https://github.com/LaiQiInfoTech/fmapper")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("w0fv1")
                        name.set("w0fv1")
                        email.set("hi@w0fv1.dev")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/LaiQiInfoTech/fmapper.git")
                    developerConnection.set("scm:git:ssh://github.com/LaiQiInfoTech/fmapper.git")
                    url.set("https://github.com/LaiQiInfoTech/fmapper")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            // GitHub Packages Maven endpoint is tied to the GitHub repository that hosts the package.
            // This repository is LaiQiInfoTech/fmapper, so publish to that namespace.
            url = uri("https://maven.pkg.github.com/LaiQiInfoTech/fmapper")
            credentials {
                // For GitHub Actions, use GITHUB_ACTOR + GITHUB_TOKEN (passed as -Dgpr.token).
                // For local publishing, pass -Dgpr.user=... -Dgpr.token=...
                username = System.getProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
                password = System.getProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}


signing {
    // GitHub Packages 不强制要求 GPG 签名。
    // 默认不签名；需要签名时显式开启：`-Psigning=true`
    val signingEnabled = providers.gradleProperty("signing")
        .map { it.equals("true", ignoreCase = true) }
        .orElse(false)
        .get()

    if (signingEnabled) {
        // 如果在构建时手动输入密码，可以使用 `useGpgCmd()` 启用命令行 GPG
        useGpgCmd()
        sign(publishing.publications["mavenJava"])
    }
}

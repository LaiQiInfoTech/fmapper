# Fmapper Library 0.1.0

Fmapper 是一个编译期注解处理器库。它会为 `@Entity` 类注入静态内部类 `FieldMapper`，减少手写 Setter 的重复代码，并提供统一的 `set/get` 入口。

## 特性

- Inline 模式：直接向实体类注入 `MyEntity.FieldMapper.set/get`（无须 `new`）。
- 提供 `set(entity, "field", value)` / `get(entity, "field")` 入口，便于动态字段赋值。
- 对 `List<T>` 字段提供专门处理（`clear + addAll`）。

## 环境要求

- JDK 21+
- 使用本项目源码构建时，建议用 JDK 21 运行 Gradle

## 安装

在业务项目的 `build.gradle.kts` 中配置：

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/LaiQiInfoTech/fmapper")
        credentials {
            // GitHub Actions: GITHUB_ACTOR / GITHUB_TOKEN
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("dev.w0fv1:fmapper:0.1.0")
    annotationProcessor("dev.w0fv1:fmapper:0.1.0")
}
```

## 使用方法

1. 定义 `@Entity` 类，并提供标准 JavaBean `getXxx/setXxx` 方法。

```java
package dev.w0fv1;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class MyEntity {
    @Id
    private Long id;
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

2. 启用 inline 模式（见下方配置），然后直接通过 `MyEntity.FieldMapper` 赋值/取值：

```java
MyEntity entity = new MyEntity();
MyEntity.FieldMapper.set(entity, "id", 1L);
Long id = (Long) MyEntity.FieldMapper.get(entity, "id");
```

也可以使用字段级的静态方法（无需字符串字段名）：

```java
MyEntity.FieldMapper.setId(entity, 1L);
Long id = MyEntity.FieldMapper.getId(entity);
```

### 启用 Inline `MyEntity.FieldMapper.set/get`（仅 javac）

启用后，会直接把静态内部类 `FieldMapper` 注入到实体类里，允许使用：

```java
MyEntity.FieldMapper.set(entity, "id", 1L);
Long id = (Long) MyEntity.FieldMapper.get(entity, "id");
```

在 Gradle 中启用（`build.gradle.kts`）：

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Afmapper.inline=true")

    // 需要 fork 才能传 JVM 参数（javac 内部 API 用于 AST 注入）
    options.isFork = true
    options.forkOptions.jvmArgs.addAll(listOf(
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    ))
}
```

## 注意事项

- 处理器目前扫描 `jakarta.persistence.Entity`。
- 生成代码依赖 `getXxx/setXxx` 命名约定；缺失时会编译失败。
- 不启用 inline 时，实体类不会注入 `FieldMapper`（也不会生成额外源码）。

## 发布到 GitHub Packages

发布到 GitHub Packages 不需要 GPG 签名（本项目默认不签名）。

在 GitHub Actions 中发布（推荐）：

```bash
./gradlew publish -Dgpr.token=${GITHUB_TOKEN}
```

本机发布：

```bash
.\gradlew.bat publish -Dgpr.user=YOUR_GITHUB_USERNAME -Dgpr.token=YOUR_GITHUB_TOKEN
```

如果你确实需要签名（可选）：

```bash
.\gradlew.bat publish -Dgpr.user=YOUR_GITHUB_USERNAME -Dgpr.token=YOUR_GITHUB_TOKEN -Psigning=true
```

## Sample

仓库内置一个可运行 sample：`sample/`（通过 composite build 引用上一层的 fmapper 源码）。

运行：

```bash
.\gradlew.bat -p sample run
```

## 许可证

Fmapper 基于 Apache License 2.0 协议发布，详见 `LICENSE`。

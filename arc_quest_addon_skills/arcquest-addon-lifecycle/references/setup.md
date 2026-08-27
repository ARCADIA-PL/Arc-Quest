# 依赖与元数据

## 本地 JAR 依赖

附属的 `libs/` 放置：

```text
arc_quest-forge1.20.1-1.0.6-all.jar
arc_quest-forge1.20.1-1.0.6-sources.jar
```

实际编译和开发运行依赖 `-all.jar`：

```groovy
dependencies {
    implementation fg.deobf(files(
            'libs/arc_quest-forge1.20.1-1.0.6-all.jar'
    ))
}
```

不要将 `-sources.jar` 添加到 `implementation`、`compileOnly` 或 `runtimeOnly`；它包含 `.java`，只作为 IDE Source attachment。IntelliJ 可在 `Project Structure > Libraries` 为 `-all.jar` 附加该文件。

`implementation` 不会自动把 ArcQ 打进附属 JAR，除非附属另外错误地对它使用 `jarJar`。不要嵌套打包 ArcQ；最终客户端和服务端各安装一个 ArcQ 本体。

## Maven 依赖

如果团队将 ArcQ 发布到私有 Maven，应发布主运行产物和 sources classifier，并让 Gradle 元数据关联源码。坐标由团队仓库决定；不要根据文件名虚构远程仓库坐标。

## `mods.toml`

附属必须声明 ArcQ 依赖。假设附属 mod id 为 `example_addon`：

```toml
[[dependencies.example_addon]]
modId="arc_quest"
mandatory=true
versionRange="[1.0.6,2)"
ordering="AFTER"
side="BOTH"
```

- `mandatory=true`：代码直接链接 ArcQ 类型时必须存在。
- `ordering="AFTER"`：明确附属在 ArcQ 后装载。
- `side="BOTH"`：只要附属注册服务端内容并使用 ArcQ 同步，就应双端安装。
- `versionRange` 应按附属实际验证范围收紧。只验证了 1.0.6 时可以使用 `[1.0.6,1.0.7)`；不要声称未测试版本兼容。

## Loader 和映射要求

附属必须匹配 Minecraft 1.20.1 Forge。ArcQ 公开签名使用 Mojang Official 命名，例如 `ServerPlayer`、`ResourceLocation`、`GuiGraphics`。使用 Parchment 的附属通常可链接相同二进制符号，但编译时仍要确认参数和方法名映射一致。

## 最小编译验证

```powershell
.\gradlew.bat compileJava --no-daemon
```

如果附属有 Mixin 指向 ArcQ，再执行完整 `build` 检查 refmap/reobf。发布前至少启动一次专用服务端，确认 common 代码没有加载 `net.minecraft.client`。

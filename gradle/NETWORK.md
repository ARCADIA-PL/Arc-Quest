# NeoForge 1.21.1 构建网络

本分支使用 Java 21、Gradle Wrapper 8.11.1 和 ModDevGradle 2.0.74，依赖保持 HTTPS 证书及主机名验证。ForgeGradle 的 check.certs 开关不适用于本分支。

```powershell
.\gradlew.bat test build compileGameTestJava
```

既有 gradle.properties 配置本机 localhost:7890 代理。本轮该路由访问 NeoForge Maven 时出现 Remote host terminated the handshake，使用仅针对该主机的临时直连覆盖后，真实下载、编译和验收均成功：

```powershell
.\gradlew.bat test build '-Dhttp.nonProxyHosts=maven.neoforged.net'
```

该参数只选择网络路由，不跳过证书验证。未把临时主机路由写入项目或用户全局配置；网络环境不同可使用正常命令。单次成功不保证网络永不掉线。JEI 与 Minecraft、NeoForge 的版本核实及验收见 [对齐记录](NEOFORGE_ALIGNMENT.md)。

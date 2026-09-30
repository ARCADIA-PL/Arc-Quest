# ForgeGradle 网络预检说明

## 当前项目配置

根目录 `gradle.properties` 设置了：

```properties
systemProp.net.minecraftforge.gradle.check.certs=false
```

这个开关只跳过 ForgeGradle 的附加站点连接预检，避免它将间歇性的 TLS 握手中断包装成 `Failed to validate certificate for host` 并阻止整个项目配置。它不是根证书修复，也不能保证实际网络请求永不掉线。

**真正的 HTTPS 下载仍执行原有的证书链和主机名验证。** 项目没有修改 HTTPS 仓库地址、安装证书、替换 Java truststore、使用宽松 TrustManager，或关闭 Gradle 下载阶段的 TLS 验证。也没有修改本机用户级 Gradle 代理或 `nonProxyHosts`。

## 本次诊断依据

以下为 2026-10-01（UTC+08:00）的本机诊断记录，不代表其他机器需要同样的网络路由：

- Gradle Wrapper 为 8.8，实际加载的 ForgeGradle 为 6.0.54。构建守护进程使用 PATH 中的 Zulu 21.0.1；项目的 Java 编译 toolchain 仍为 17，这两者不是同一个配置。
- 原始命令 `gradlew.bat help --stacktrace --no-daemon --console=plain` 没有任何绕过参数，29 秒后失败。该次报错主机为 `libraries.minecraft.net`，底层异常为 `SSLHandshakeException: Remote host terminated the handshake`，由 `EOFException: SSL peer shut down incorrectly` 引起。没有出现 PKIX 路径构建失败或证书过期异常。
- 对本机缓存的 ForgeGradle 6.0.54 字节码进行了核对：`EnvironmentChecks.testServerConnection` 向 `https://maven.minecraftforge.net/` 和 `https://libraries.minecraft.net/` 发起额外的 `HttpsURLConnection` HEAD 请求；它将任意 `SSLException` 都包装成上述“证书校验失败”提示。因此提示本身不足以判断根证书出了问题。
- 使用默认 JSSE 信任链的短超时 Java 探针，确实成功读取过 ForgeGradle 的 HTTPS POM 和 Minecraft 的 Brigadier HTTPS JAR。观测到的站点证书分别由 Google Trust Services 和 Microsoft 的公开 CA 签发，当时仍在有效期内；没有向探针注入 TrustManager 或关闭主机名验证。Zulu 21、Oracle 17 和 Temurin 21 的比较也没有证明这是某个 JDK 缺少根证书的问题。
- 原代理路径与直连路径都存在间歇性超时或握手中断。只在测试进程中追加两个精确主机的 `http.nonProxyHosts` 后，`ProxySelector` 明确返回 DIRECT，但三轮 Forge 根地址 HEAD 仍然超时。一次保留证书预检的 Gradle help 虽然通过，日志中仍出现独立的 MCPRepo 元数据下载 SSL EOF，不能据此宣称整个网络已经稳定。
- Java 的 HTTPS 代理选择与 Gradle 8.8 的 `SystemDefaultRoutePlanner` 都使用默认 `ProxySelector`，所以 `http.nonProxyHosts` 确实适用于这些 HTTPS 请求。由于测试路由并不稳定，没有把临时规则固化到用户级或项目级配置。

## 验证与恢复预检

正常构建不再需要临时的 `-Dnet.minecraftforge.gradle.check.certs=false`：

```powershell
.\gradlew.bat help --stacktrace --no-daemon --console=plain
.\gradlew.bat test build --no-daemon --console=plain
```

首条命令用于验证项目配置阶段；它不等价于完整下载、编译或客户端验收。第二条才会执行对应构建任务。本次修复的实际验证结果应以构建日志为准，不使用 `--offline` 掩盖网络问题。

本次落盘后的实际结果：第一条普通命令在 6 秒内 `BUILD SUCCESSFUL`、退出码为 0。随后使用 `./gradlew.bat -PobjectiveIconRuntimeAudit test build jarJar runClient --console=plain` 完成完整构建：427 项 Java 测试全部通过，有 JEI 的真实客户端验收 PASS，并生成 12 张截图；整体构建耗时 1 分 38 秒、退出码为 0，日志为 `build/objective-icons-defaults-final.log`。两条命令均未传入临时 `-D` 参数、未启用 offline；这两次日志没有出现证书预检失败或 SSL 握手异常。这些结果仅代表本次运行，不保证后续网络请求永不掉线。

网络路径恢复稳定后，可单次重新启用预检，或删除根目录中的上述属性：

```powershell
.\gradlew.bat -Dnet.minecraftforge.gradle.check.certs=true help --stacktrace --no-daemon --console=plain
```

如果后续真实依赖下载仍然报 TLS 错误，应根据其实际主机、嵌套异常和本机代理路由继续处理。这个开关既不会使不可信证书变为可信，也不会绕过真实下载的握手失败。不要将它扩展成全局 HTTPS 校验关闭或 HTTP 仓库替换。

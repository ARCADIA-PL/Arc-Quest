# ArcQ 发布包构建与生产映射验证

## Forge 1.20.1

使用 JDK 17 构建。发布含内嵌依赖的 `build/libs/arc_quest-forge1.20.1-<版本>-all.jar`：

```powershell
.\gradlew.bat assemble verifyReleaseJars --console=plain
```

依赖已缓存时可增加 `--offline`。只执行 `jarJar` 也会自动执行 `reobfJarJar` 并检查结果；不能使用重映射前的临时 JAR 发布。

`verifyReleaseJars` 检查普通和 bundled JAR：

- `arc_quest.mixins.json` 指向的 refmap 必须存在，Screen 的 width／height／font、GameRenderer.getFov、PlayerList.save／playerIo、Entity.load 的生产映射必须完整。
- 任务界面调用 Minecraft 的字节码必须已转换为 Forge 1.20.1 的 SRG 名称。
- bundled JAR 的 JarJar 元数据及 MixinExtras、Ponder、Flywheel 内嵌文件必须存在。

这些检查也接入 `assemble` 和重映射任务。主源码的编译关闭增量，以便每次源码改变时 Mixin 处理器看到所有 Mixin；refmap 被声明为编译输出，缺失时不能继续使用旧的编译缓存。此调整影响编译时间，不增加游戏运行开销。

发布前还应在隔离游戏实例使用实际 `-all.jar` 进行启动验收。开发环境 `runClient` 使用不同名称空间，不能替代生产 JAR 的映射验收。更新时替换旧 ArcQ 文件，同一 `mods` 目录只保留一个 ArcQ 运行包；不要放入 `-sources.jar`，也不要同时放普通和 `-all.jar`。

## NeoForge 1.21.1

使用 JDK 21 和该分支的 ModDevGradle 构建，发布 `build/libs/arc_quest-neoforge1.21.1-<版本>.jar`。该生产运行环境使用命名映射，不照搬 Forge 的 `reobfJarJar` 接线或 SRG 校验。Forge JAR 与 NeoForge JAR 不能互换。

## 2026-10-03 生产故障记录

外部日志使用 Minecraft 1.20.1、Forge 47.4.0、Java 21.0.9，加载 `arc_quest-forge1.20.1-1.0.8-all.jar`。日志先报告 `arc_quest.refmap.json` 无法读取，再因 `MixinPonderUIStubAccessor` 找不到 `Screen.width:I` 崩溃。

本地复现了两项发布缺陷：`jarJar` 独立组装时遗漏 MixinGradle 只添加到普通 JAR 的 refmap；单独执行 `jarJar` 时不会自动执行生产重映射，生成代码仍直接调用开发名称 `Minecraft.getWindow/setScreen`。修复为 bundled 任务显式添加完整生成的 refmap，并在其后执行 ForgeGradle 自动创建的 `reobfJarJar`，保留 Accessor 的正常 remap 设置。

实际完成的验证：

| 场景 | 结果 |
| --- | --- |
| 缺失原 refmap 后重新编译与发布检查 | 自动重新生成完整映射，通过 |
| 单独 `jarJar`，主编译 UP-TO-DATE | 自动重映射与检查，通过 |
| 仅修改普通临时 Java 类，再次编译打包 | 完整 refmap 保留，普通／bundled 两包检查通过 |
| 去除临时类后的 `assemble` | 自动发布检查通过；最终 JAR 无探针或验收模组代码 |
| 官方 Forge 47.4.0 生产客户端，Java 17 | 主菜单、Screen 三个 setter、GameRenderer FOV invoker 通过，正常退出 0 |
| 同一生产客户端，Java 21 | 同样通过，正常退出 0 |

客户端使用最终发布包，未注入开发源码或开发映射；测试中的审计模组单独放入隔离实例，完成后正常退出，不访问用户存档。它绕过首次启动的无障碍欢迎页后确认主菜单，并实际调用两个 ArcQ Mixin 接口。Java 21 测试使用本地 Zulu 21，未复现外部 Oracle 21.0.9 的全部整合包环境。

验证覆盖本次发布映射故障；未复测外部整合包的全部 282 个模组、实际服务器或存档游玩流程。日志中另有 Forge `field_to_method.js` 的独立报错，本次没有据此修改第三方模组。

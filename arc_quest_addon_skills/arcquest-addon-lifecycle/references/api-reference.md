# 工程和生命周期深入参考

## 依赖产物

正式 ArcQ 构建产物：

```text
arc_quest-forge1.20.1-1.0.6-all.jar
arc_quest-forge1.20.1-1.0.6-sources.jar
```

`-all.jar` 是编译和运行依赖。`-sources.jar` 只给 IDE 阅读源码，不能单独编译，也不要放进 `implementation`、`runtimeOnly` 或 `jarJar`。

本地依赖示意：

```groovy
dependencies {
    implementation fg.deobf(files(
            'libs/arc_quest-forge1.20.1-1.0.6-all.jar'
    ))
}
```

附属成品不嵌套 ArcQ。客户端和服务端分别安装同一 ArcQ 本体及附属。

## `mods.toml`

代码直接链接 ArcQ 类型时声明 mandatory dependency：

```toml
[[dependencies.example_addon]]
modId="arc_quest"
mandatory=true
versionRange="[1.0.6,1.0.7)"
ordering="AFTER"
side="BOTH"
```

版本范围只声明实际验证范围。`ordering=AFTER` 不替代注册事件；它只描述模组加载顺序。

## ArcQ 初始化方式

ArcQ 主类注册：

- CLIENT config：一般、Toast、文本；
- COMMON config：日志；
- MOD 总线：common setup、config loading、capability registration；
- Forge 总线：运行时事件；
- 客户端 setup：Ponder、HUD overlays、key mappings、config screen；
- 网络：单个 Forge `SimpleChannel`，在注册事件后初始化；
- 数据生成：`GatherDataEvent` 注册语言等 Provider；
- 数据重载：`AddReloadListenerEvent`。

附属不要重复初始化 ArcQ network/config/capability，也不要调用 ArcQ bootstrap。

## 注册事件时间线

ArcQ 在 `FMLCommonSetupEvent#enqueueWork` 中发布六类 `ArcQuestRegistrationEvent`，然后冻结 Registry。附属 listener 在 MOD 总线上同步执行。

注册 callback 内允许：

- 构建不可变 Definition；
- 读取已注册 Minecraft Registry 对象；
- 注册 ArcQ 定义和扩展 resolver；
- 验证附属依赖项存在。

不适合：

- 访问服务器世界或玩家；
- 网络请求/数据库；
- 客户端 Screen/renderer 初始化；
- 延迟到 server starting 才注册 Definition；
- 捕获 event 留到以后使用。

## 冻结和重复 ID

ArcQ 冻结：Quest、Quest Group、Dialogue、NPC binding、Entity Dialogue Extension、Trade、Gacha、Guide、Guide Group。

freeze 后注册通常抛异常或破坏同步快照。重复代码 ID 也应视为错误，不要通过 catch 后忽略来“兼容”。

代码定义和 datapack snapshot 有分层来源信息。附属不调用 `replaceDatapackSnapshot`，也不直接修改生成/热重载层。

## 事件总线选择

```text
ArcQuestRegistrationEvent.* -> MOD event bus
api.event.* runtime events  -> MinecraftForge.EVENT_BUS
Forge gameplay events       -> MinecraftForge.EVENT_BUS
client MOD registration     -> MOD event bus + Dist.CLIENT
```

静态 `@Mod.EventBusSubscriber` 必须写正确 `bus`。最常见错误是把 Quest registration listener 放到默认 Forge bus，导致永远不触发。

## 侧隔离

common 注册类可以引用 ServerPlayer、Definition 和 Registry，但不能 import `net.minecraft.client` 或 `org.arcadia.arc_quest.client`。

客户端扩展放独立类，并通过：

- `@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD)`；或
- `DistExecutor.safeRunWhenOn`；或
- 附属已有 client bootstrap。

仅在方法体里“判断 dist”不足以防止 JVM 验证 common 类签名时加载客户端类型。

## 附属 API 演进

如果附属自己也提供 ArcQ 扩展 API：

- public 接口新增方法用 `default`；
- 新参数保留旧重载；
- 新返回信息使用新方法名或结果对象；
- 事件新增字段保留旧构造器；
- ID 和事件发布时机稳定；
- 不暴露 ArcQ 内部 mutable object 作为长期契约。

## 验证

最低顺序：

```text
附属 compileJava
  -> ArcQ compileJava（若同时改 ArcQ）
  -> 附属 build（有 Mixin/资源）
  -> runServer 检查专服类加载和 Registry
  -> runClient 检查同步与 UI
```

注册错误通常在 common setup/freeze 时暴露。专用服务器测试用于发现客户端类泄漏，不能只用单人客户端代替。

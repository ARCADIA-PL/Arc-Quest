# 客户端正式扩展点

## 隔离原则

所有下列 Registry 都引用 `net.minecraft.client` 或 Screen/HUD 类型，只能从客户端初始化类加载。使用 `DistExecutor`、`@EventBusSubscriber(value = Dist.CLIENT)` 或项目既有 client setup，不能从模组主类静态字段直接引用。

Registry 当前使用 `CopyOnWriteArrayList`，没有通用 unregister。每个附属实例只注册一次，不在 Screen 构造器或每次打开时注册。

## `DialogueOverlayRegistry`

```text
DialogueOverlayRegistry.register(new DialogueOverlayRegistry.Overlay() {
    @Override
    public boolean shouldRender(DialogueScreen screen, String dialogueId) {
        return dialogueId.startsWith("example_addon:");
    }

    @Override
    public void render(GuiGraphics graphics, DialogueScreen screen,
                       String dialogueId, int mouseX, int mouseY, float partialTick) {
        // 仅绘制附属顶层表现，恢复自己修改的渲染状态。
    }
});
```

Overlay 在对话顶层每帧调用。`shouldRender` 先快速过滤。不要保存 Screen 强引用、执行 I/O、修改服务端业务状态或与 ArcQ 控件争夺不受控输入。

## `JournalTabVisibilityRegistry`

```text
JournalTabVisibilityRegistry.register(tab ->
        tab != JournalTypes.Tab.COMPLETED || ExampleClientConfig.showCompletedTab());
```

所有 Filter 均通过才可见；任一 false 会隐藏 Tab。Filter 是全局组合规则，不要为了临时 Screen 状态永久隐藏其他模组内容。

## `QuestMarkerPresentationRegistry`

```text
QuestMarkerPresentationRegistry.registerColorResolver(marker ->
        marker.getId().startsWith("example_addon:") ? 0xFFFFCC33 : null);

QuestMarkerPresentationRegistry.registerLabelResolver(marker ->
        marker.getId().startsWith("example_addon:")
                ? Component.translatable("marker.example_addon.target")
                : null);
```

Label/Color resolver 返回 null 表示交给后续 resolver 或默认逻辑。Visibility filter 为 AND 关系。按 namespace/style hint 快速过滤，避免每帧查 Registry 或遍历世界。

## `MarkerCameraBasisRegistry`

第三方相机、骑乘镜头或特殊视角可提供投影 basis：

```text
MarkerCameraBasisRegistry.register(new MarkerCameraBasisRegistry.Provider() {
    @Override
    public boolean applies(Camera camera) {
        return ExampleCameraApi.isActive();
    }

    @Override
    public MarkerCameraBasisRegistry.Basis resolve(
            Camera camera, MarkerCameraBasisRegistry.Basis fallback) {
        return new MarkerCameraBasisRegistry.Basis(
                ExampleCameraApi.forward(),
                ExampleCameraApi.left(),
                ExampleCameraApi.up());
    }
});
```

ArcQ 会标准化非零向量，并对 null/近零向量回退。provider 每帧执行，不分配大对象、不查询服务器状态。

## 非正式内部类

未在本页列出的 HUD renderer、Screen 布局、客户端 cache、网络 packet 和 Mixin 目标属于内部实现，即使 public 也不自动成为稳定扩展点。新附属优先使用本页列出的正式 Registry。

## GUI 验证

客户端扩展至少验证：

- GUI scale 2、3、4；
- 小窗口和超宽屏；
- 对话/日志关闭动画；
- 与 Embeddium/Oculus 等渲染环境；
- Screen 切换后无重复 listener 或强引用；
- 专用服务器启动不加载 client class。

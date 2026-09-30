# Opt-in objective icon client regression

Run serially in the disposable integrated world named exactly `ArcQ Objective Icon Verification`:

```powershell
.\gradlew.bat runClient -PobjectiveIconRuntimeAudit
.\gradlew.bat runClient -PobjectiveIconRuntimeAudit -PwithoutJei
```

The gate retains native single/parallel journals, COLLECT/CRAFT/OFFER/DELIVER default item icons,
real COLLECT/OFFER tag candidates and rotation, focus, JEI round trip,
six baked heads and cow/pig portraits, resource reload invalidation, and read-only inventory/XP checks.
It requires 12 screenshots with JEI or 11 without JEI in
`run/screenshots/objective-icons/{with-jei|without-jei}` and terminates the client with a
`[ARCQ_OBJECTIVE_ICON_AUDIT] PASS` or `FAIL` log marker.

Additional visual assertions inspect the captured framebuffer, not just requested alpha values:

- Native cutout item, 3D item, enchanted item, explicit image, multi-layer cow, baked head,
  synthetic soft RGBA source drawn through production `TextureObjectiveIcon`, and a public visual
  extension that calls `ObjectiveIconAlpha.renderItem` with inner opacity .5.
- Opacity 1, .5, .05, 0, then 1 again: visible low-alpha pixels, unchanged zero-alpha background,
  restored render state, group opacity, and known source-alpha composition.
  The nested extension is compared against the plain item at effective opacity .5, .25 and .025,
  so entering an outer composition must preserve the inner opacity without a sudden brightness jump.
- An accepted minimal quest rendered by real `QuestJournalScreen` / `ObjectiveRowRenderer`,
  captured opaque and during actual `onClose()` at .5 opacity. Test reflection holds only animation
  clocks/reveals; the production closing state, layout, row, item rendering, and tooltip path remain live.
- The real text settings screen over the real journal, with deterministic high-Z parent text/items
  intersecting the panel body, slider, and action buttons. Parent control, clean modal and stressed
  modal screenshots are compared; small expected panel translucency is allowed.
- The existing tag tooltip must contain exactly item name and tag. A closing plain-item tooltip
  retains its one-line active request until its held fade finishes, with no JEI hint text.

The synthetic texture is released during success/failure cleanup. The audit does not change or save
text-size settings. GUI scale, pause-on-focus-loss and player invulnerability are restored.
The deterministic probes complement normal animation screenshots; they do not verify every GPU,
resource pack, third-party shader, or all text-size settings.

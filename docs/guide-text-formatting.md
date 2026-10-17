# Guide legacy formatting and wrapping

Guide descriptions may contain Minecraft legacy color/bold/reset codes in translated text. `GuideClientTextResolver` resolves the current language and dynamic arguments, then expands those codes into native styled component runs before the screen calls `Font.split`.

Minecraft's formatted-string wrapping can start a continuation line with the style active at the break. A later legacy reset can then restore that line's bold style, rather than the original normal body style. This leaks emphasis into the body and may make the rendered line wider than its wrapping limit. Parsing once before wrapping gives every run its explicit style and removes that ambiguity.

- Plain descriptions retain the original component without allocating replacement runs.
- Dynamic key bindings keep their resolved text and native styles.
- Native styles such as color, bold, font and interactions are retained; a legacy reset restores the containing component's base style.
- Parsing happens while the Guide screen builds its text cache, not on each rendered frame.
- Addons continue using existing Guide APIs and normal translated Components; no custom renderer or addon workaround is required.

Runtime regression: DuelQuest's `GuideCopyClientSmoke` compares unchanged visible text, actual colored-bold and normal glyphs, dynamic keys and percentages for all 17 addon guides / 33 pages in Chinese and English, at wrapping widths 130, 220 and 360. It also measures the unexpanded legacy text for comparison. The single-client smoke is muted and uses a 5G heap; its result and screenshots are archived under DuelQuest's verification evidence.

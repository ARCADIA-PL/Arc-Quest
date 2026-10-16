# Read-only collection details

ArcQ protocol 23 adds supplementary archive details without changing frozen collection rules, record IDs, acquisition requirements or reward entitlements.

Register a provider during common setup, on both physical sides:

```java
CollectionDetailProviderRegistry.register(
    ResourceLocation.parse("example:equipment/longsword"),
    true, // open this entry's archive view first
    context -> new CollectionDetailDocument(
        Component.translatable("example.catalogue.details"),
        List.of(new CollectionDetailDocument.Section(
            Component.translatable("example.catalogue.cost"),
            Component.empty(), Component.empty(),
            List.of(new CollectionDetailDocument.Line(
                List.of(new ItemStack(Items.DIAMOND)),
                Items.DIAMOND.getDescription(), 2,
                context.player().getInventory().countItem(Items.DIAMOND)))))));
```

Only the server invokes providers. They must be read-only: do not open trade sessions, grant rewards, consume items, change flags or advance quests. Use translated Components and real display stacks rather than server-localized strings. A line's `owned == -1` suppresses inventory comparisons, for example for an already completed one-time sample or an exchange result. An unknown custom cost can use a descriptive label with `required == 0` and `owned == -1`.

For a real `ItemTradeOffer` payment, use `previewCost(player)` rather than recounting inventory. It uses the transaction's exact NBT/tag matcher, dynamic count resolver, main inventory, equipped armor and offhand. Cursor stacks cannot be spent. `getDisplayStacks()` retains tooltip NBT; `getDisplayCount(player)` evaluates a dynamic reward amount for the player.

The journal requests only its open public entry. The server verifies the player's session epoch, actual collection run, frozen phase/binding and visible/revealed projection before calling a provider. Discovery is not required. A different player's request, hidden binding, obsolete run or invalid epoch produces an empty document. Providers therefore work for previously accepted runs without a definition-version migration.

Responses preserve item NBT and Component trees. Documents allow at most 16 sections, 128 total lines, 64 lines per section, 64 icon alternatives per line, 8192 characters per serialized Component and 256 KiB per response. A provider failure or oversized response produces an unavailable notice. Never truncate a payment into a misleading partial price.

The client refreshes only the open entry, at most once per second; one request stays in flight for up to five seconds before retrying. Selecting another entry requests immediately. Request IDs reject old replies; run, session and datapack-reload changes invalidate the cache. Closing the details or disconnecting also invalidates pending requests. Refreshing supplementary inventory counts does not write collection records or create unread notifications.

`CollectionDetailPacketTest` covers the real wire codec with custom stack NBT, component preservation, quantity/cardinality limits, delayed responses and selection/run/reload/session invalidation. Add-ons should additionally exercise providers against their actual registered content in server GameTest and the production journal in a muted game client.

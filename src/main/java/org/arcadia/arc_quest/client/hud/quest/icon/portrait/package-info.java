/**
 * Static, client-only Objective entity portraits. These pictures have no item/JEI identity.
 *
 * <p>Built-in head sources are the six Minecraft 1.20.1 skeleton, wither skeleton, zombie, creeper,
 * piglin and dragon head items. The standard adapter bakes an independently-created SkullModelBase,
 * never a living entity, with a fixed orthographic front view. Custom head models must register an
 * explicit adapter and front frame through RegisterObjectiveIconsEvent. Registering a head item
 * alone does not infer an arbitrary mod's orientation. Player profiles and dynamic NBT skins are
 * deliberately outside this static source contract; use a texture or an explicit static adapter.
 *
 * <p>Cow and pig demonstrate verified texture-only adaptations. Their default definitions live in
 * assets/arc_quest/objective_icons/portraits, separately from the resource-pack override directory.
 * UVs were checked against the local Minecraft 1.20.1 CowModel.createBodyLayer, PigModel.createBodyLayer
 * and ModelPart.Cube NORTH polygon: a cube at texture offset (u,v), dimensions (w,h,d), has front
 * region (u+d,v+d,w,h). Cow: head (6,6,8,8), each horn (23,1,1,3), 64x32 source. Pig: head (8,8,8,8)
 * then snout (17,17,4,3), 64x32 source. The snippets are asserted by EntityPortraitRuleTest. They are
 * representative base appearances; native KILL matches the entity type, not a skin variant.
 *
 * <p>A pack can replace AUTO appearance with
 * assets/ENTITY_NAMESPACE/arc_quest/objective_icons/entities/ENTITY_PATH.json. Its type is one of
 * arc_quest:texture, arc_quest:entity_texture_portrait, arc_quest:none. A malformed or unavailable
 * explicit rule remains NONE and cannot expose a lower-priority default. The texture-portrait
 * definition has texture, referenceSize, canvasSize and up to 32 back-to-front layers; each layer
 * specifies region, destination, optional flipX/flipY and unsigned ARGB tint (#AARRGGBB or integer).
 * Region coordinates are normalized by referenceSize, so same-layout HD textures work. Changed UV
 * layouts need updated rules; unknown entities and variants are never guessed.
 *
 * <p>Call EntityPortraits.prepare before GUI batches/scissors to bake queued heads. A pending head
 * visual is temporarily unavailable; it must not cause the caller to permanently cache NONE or
 * choose a different fallback. Only a confirmed failed source permits the registered UV fallback.
 * Head generation uses full-bright emissive shading with depth writes and leaves global directional
 * lighting untouched. A temporary framebuffer is read back once, converted to straight alpha and
 * immediately destroyed; the 64-entry LRU owns the resulting DynamicTextures.
 *
 * <p>Resource reload and session reset replace the generation before releasing previous resources,
 * invalidating all stale visual handles. Resource-manager textures are borrowed, not destroyed by
 * this cache. Public registration is deterministic and closes after the client registration event.
 */
package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

package org.arcadia.arc_quest.integration.storage;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import java.io.IOException;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.arcadia.arc_quest.inventory.PlayerItemAccess.StackAmount;
import org.arcadia.arc_quest.questplayer.persistence.PlayerNbtFiles;

/** Only for long-lived addon construction; ordinary trades keep their synchronous receipts. */
final class BeyondDimensionsPayments {
    private static final PlayerNbtFiles FILES = new PlayerNbtFiles(128L << 20, 512L << 20);
    private BeyondDimensionsPayments() {}

    private static CompoundTag receipts(DimensionsNet net) {
        return ((StoragePaymentReceipts) net).arcQuest$payments();
    }
    private static ListTag costTag(List<StackAmount> cost) {
        var result = new ListTag();
        for (var part : cost) {
            if (part.amount() <= 0 || part.amount() > Integer.MAX_VALUE || part.template().isEmpty())
                throw new IllegalArgumentException("Invalid durable item cost");
            var tag = new CompoundTag(); tag.put("Item", part.template().save(new CompoundTag()));
            tag.putLong("Count", part.amount()); result.add(tag);
        }
        return result;
    }
    private static CompoundTag checked(DimensionsNet net, String receipt, UUID owner, List<StackAmount> cost) {
        if (receipt.isBlank() || receipt.length() > 256 || cost.isEmpty()) throw new IllegalArgumentException("Invalid durable payment");
        var entry = receipts(net).getCompound(receipt);
        if (!entry.isEmpty() && (!entry.hasUUID("Owner") || !entry.getUUID("Owner").equals(owner)
                || !entry.getList("Cost", Tag.TAG_COMPOUND).equals(costTag(cost))))
            throw new IllegalStateException("Dimensional payment receipt does not match construction");
        return entry;
    }
    static boolean committed(MinecraftServer server, DimensionsNet net, String receipt, UUID owner, List<StackAmount> cost) {
        var entry = checked(net, receipt, owner, cost);
        if (entry.isEmpty()) return false;
        flush(server, net);
        return !entry.getBoolean("Cancelled");
    }
    static void commit(MinecraftServer server, DimensionsNet net, String receipt, UUID owner, List<StackAmount> cost) {
        var previous = checked(net, receipt, owner, cost);
        if (!previous.isEmpty()) {
            if (previous.getBoolean("Cancelled")) throw new IllegalStateException("Dimensional payment was cancelled");
            flush(server, net); return;
        }
        var storage = net.getUnifiedStorage();
        var before = BeyondDimensionsItemStorage.amounts(storage);
        try {
            for (var part : cost) {
                var key = new ItemStackKey(part.template());
                var removed = storage.extract(key, part.amount(), false, false);
                if (removed.amount() != part.amount() || !key.equals(removed.key()))
                    throw new IllegalStateException("维度存储材料不足或被提取规则拒绝，未提交施工。");
            }
        } catch (RuntimeException failure) {
            try { BeyondDimensionsItemStorage.restoreDelta(storage,
                    BeyondDimensionsItemStorage.difference(before, BeyondDimensionsItemStorage.amounts(storage))); }
            catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
        var entry = new CompoundTag(); entry.putUUID("Owner", owner); entry.put("Cost", costTag(cost));
        var refund = new ListTag();
        BeyondDimensionsItemStorage.difference(before, BeyondDimensionsItemStorage.amounts(storage)).forEach((key, count) -> {
            var tag = new CompoundTag(); tag.put("Key", IStackKey.serializeNBTCommon(key)); tag.putLong("Count", count); refund.add(tag);
        });
        entry.put("Refund", refund); receipts(net).put(receipt, entry);
        // A failed write keeps the in-memory receipt: retry flushes it instead of charging twice.
        flush(server, net);
    }
    static void cancel(MinecraftServer server, DimensionsNet net, String receipt, UUID owner, List<StackAmount> cost) {
        var entry = checked(net, receipt, owner, cost);
        if (entry.isEmpty()) return;
        if (!entry.getBoolean("Cancelled")) {
            Map<IStackKey<?>, Long> refund = new LinkedHashMap<>();
            for (var value : entry.getList("Refund", Tag.TAG_COMPOUND)) {
                var tag = (CompoundTag) value;
                var key = IStackKey.deserializeNBTCommon(tag.getCompound("Key"));
                if (key.isEmpty()) throw new IllegalStateException("Cannot decode construction refund resource");
                refund.put(key, tag.getLong("Count"));
            }
            BeyondDimensionsItemStorage.restoreDelta(net.getUnifiedStorage(), refund);
            entry.putBoolean("Cancelled", true);
        }
        flush(server, net);
    }
    static void finish(MinecraftServer server, DimensionsNet net, String receipt, UUID owner, List<StackAmount> cost) {
        checked(net, receipt, owner, cost);
        receipts(net).remove(receipt);
        flush(server, net);
    }
    private static void flush(MinecraftServer server, DimensionsNet net) {
        if (!server.isSameThread()) throw new IllegalStateException("Durable storage payment must run on the server thread");
        var root = new CompoundTag(); root.put("data", net.save(new CompoundTag()));
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        var file = server.getWorldPath(LevelResource.ROOT).resolve("data/BDNet_" + net.getId() + ".dat");
        try {
            FILES.write(file, root);
            if (!FILES.read(file).equals(root)) throw new IOException("Dimensional payment save verification failed");
            net.setDirty(false);
        } catch (IOException failure) { net.setDirty(); throw new IllegalStateException("维度存储付款记录保存失败，施工已暂停，可稍后重试。", failure); }
    }
}

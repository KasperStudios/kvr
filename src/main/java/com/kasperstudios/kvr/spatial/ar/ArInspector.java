package com.kasperstudios.kvr.spatial.ar;

import com.kasperstudios.kvr.config.KvrSpaceConfig;
import com.kasperstudios.kvr.inspect.InspectServer;
import com.kasperstudios.kvr.platform.KVRNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 0034a — AR Block Inspector (replaces the floor grid). The block the visor looks at (ray up to
 * {@link #RANGE} blocks) gets a neon corner outline and an info plate: name, registry id, hardness, tool / tier
 * and whether the held item harvests it, signal level for redstone, growth for crops, slot usage for containers
 * (asked from the server, {@link InspectServer}). Data is built on the client tick; {@link ArRenderer} and
 * {@link #renderHud} draw it.
 *
 * <p>Off while a window is under the hand ray, a screen is open or a Virtual Space is active (the world is hidden).
 * Client init: {@code ArInspector.initClient()} (registers the reply receiver).
 */
public final class ArInspector {

    public static final double RANGE = 8.0;
    private static final long FADE_MS = 220;
    private static final int REFRESH_TICKS = 20;

    /** One text line on the plate. */
    public record Line(String text, int color) {}

    /** What the plate shows. {@code box} is world-space (the block's outline shape). */
    public record Info(BlockPos pos, String name, String id, ItemStack icon, List<Line> lines, AABB box) {}

    private record Slots(int used, int total, long at) {}

    @Nullable private static volatile Info info;
    @Nullable private static BlockPos shownPos;
    private static long shownSince;
    private static int ticksOnTarget;
    private static final Map<BlockPos, Slots> slots = new HashMap<>();
    private static boolean initialized;

    private ArInspector() {}

    public static void initClient() {
        if (initialized) return;
        initialized = true;
        KVRNetwork.registerClientReceiver(InspectServer.REPLY, buf -> {
            InspectServer.Reply r = InspectServer.Reply.read(buf);
            Minecraft.getInstance().execute(() -> slots.put(r.pos(), new Slots(r.used(), r.total(), System.currentTimeMillis())));
        });
    }

    @Nullable
    public static Info info() {
        return info;
    }

    /** 0..1 fade-in since the target changed. */
    public static float appear() {
        return info == null ? 0f : Math.min(1f, (System.currentTimeMillis() - shownSince) / (float) FADE_MS);
    }

    // --- tick ----------------------------------------------------------------------------------

    /** {@code on}: glasses lit, inspector enabled, not in a space, no window under the ray. */
    static void tick(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (!on || player == null || level == null || mc.screen != null) {
            clear();
            return;
        }
        HitResult hit = player.pick(RANGE, 1f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            clear();
            return;
        }
        BlockPos pos = bh.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            clear();
            return;
        }
        if (!pos.equals(shownPos)) {
            shownPos = pos.immutable();
            shownSince = System.currentTimeMillis();
            ticksOnTarget = 0;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof net.minecraft.world.Container || be != null && isStorage(be)) {
            if (ticksOnTarget % REFRESH_TICKS == 0) KVRNetwork.sendToServerIfAccepted(InspectServer.QUERY, buf -> buf.writeBlockPos(pos));
        }
        ticksOnTarget++;
        info = build(level, player, pos, state, be != null);
        if (slots.size() > 64) slots.clear();
    }

    private static void clear() {
        info = null;
        shownPos = null;
    }

    /** Client-side BEs of chests/barrels are not {@code Container} on every version: fall back to the class name. */
    private static boolean isStorage(BlockEntity be) {
        String n = be.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return n.contains("chest") || n.contains("barrel") || n.contains("shulker") || n.contains("hopper")
            || n.contains("dispenser") || n.contains("dropper") || n.contains("furnace") || n.contains("smoker");
    }

    private static Info build(ClientLevel level, LocalPlayer player, BlockPos pos, BlockState state, boolean hasBe) {
        String name = state.getBlock().getName().getString();
        String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        List<Line> lines = new ArrayList<>();

        float hardness = state.getDestroySpeed(level, pos);
        lines.add(new Line(hardness < 0 ? tr("kvr.inspect.unbreakable") : tr("kvr.inspect.hardness", fmt(hardness)), 0xFFE2E8F0));

        if (hardness >= 0) {
            String tool = tool(state);
            String tier = tier(state);
            if (tool != null) lines.add(new Line(tr("kvr.inspect.tool", tier == null ? tool : tool + " \u00b7 " + tier), 0xFFCBD5E1));
            if (state.requiresCorrectToolForDrops()) {
                boolean ok = player.hasCorrectToolForDrops(state);
                lines.add(new Line(tr(ok ? "kvr.inspect.harvest.ok" : "kvr.inspect.harvest.no"), ok ? 0xFF4ADE80 : 0xFFF43F5E));
            }
        }

        int signal = signal(level, pos, state);
        if (signal > 0) lines.add(new Line(tr("kvr.inspect.signal", signal), 0xFF000000 | ArRenderer.signalColor(signal)));
        else {
            int in = level.getBestNeighborSignal(pos);
            if (in > 0) lines.add(new Line(tr("kvr.inspect.signal_in", in), 0xFF000000 | ArRenderer.signalColor(in)));
        }

        int growth = growth(state);
        if (growth >= 0) lines.add(new Line(tr("kvr.inspect.growth", growth), 0xFF86EFAC));

        if (hasBe) {
            Slots s = slots.get(pos);
            if (s != null) lines.add(new Line(tr("kvr.inspect.slots", s.used(), s.total()), s.used() >= s.total() ? 0xFFFBBF24 : 0xFF93C5FD));
            else if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container || isStorage(level.getBlockEntity(pos))) {
                lines.add(new Line(tr("kvr.inspect.slots_pending"), 0xB0E2E8F0));
            }
        }

        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        ItemStack icon = new ItemStack(state.getBlock().asItem());
        return new Info(pos.immutable(), name, id, icon, List.copyOf(lines), box);
    }

    @Nullable
    private static String tool(BlockState s) {
        if (s.is(BlockTags.MINEABLE_WITH_PICKAXE)) return tr("kvr.inspect.tool.pickaxe");
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) return tr("kvr.inspect.tool.axe");
        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return tr("kvr.inspect.tool.shovel");
        if (s.is(BlockTags.MINEABLE_WITH_HOE)) return tr("kvr.inspect.tool.hoe");
        return s.requiresCorrectToolForDrops() ? null : tr("kvr.inspect.tool.hand");
    }

    @Nullable
    private static String tier(BlockState s) {
        if (s.is(BlockTags.NEEDS_DIAMOND_TOOL)) return tr("kvr.inspect.tier.diamond");
        if (s.is(BlockTags.NEEDS_IRON_TOOL)) return tr("kvr.inspect.tier.iron");
        if (s.is(BlockTags.NEEDS_STONE_TOOL)) return tr("kvr.inspect.tier.stone");
        return null;
    }

    /** Output level: POWER property, else the strongest side of a signal source. */
    private static int signal(ClientLevel level, BlockPos pos, BlockState s) {
        if (s.hasProperty(BlockStateProperties.POWER)) return s.getValue(BlockStateProperties.POWER);
        if (!s.isSignalSource()) return 0;
        int best = 0;
        for (Direction d : Direction.values()) best = Math.max(best, s.getSignal(level, pos, d));
        return best;
    }

    /** Crops, saplings, berries…: any integer property named "age", as 0..100 %. -1 = none. */
    private static int growth(BlockState s) {
        for (Property<?> p : s.getProperties()) {
            if (p instanceof IntegerProperty ip && p.getName().equals("age")) {
                int max = 0;
                for (Integer v : ip.getPossibleValues()) max = Math.max(max, v);
                return max <= 0 ? -1 : Math.round(100f * s.getValue(ip) / max);
            }
        }
        return -1;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    // --- HUD widget (position = HUD) -----------------------------------------------------------

    /** Top-left screen widget: icon, name, id, lines. Slides in from the left with the fade. */
    public static void renderHud(net.minecraft.client.gui.GuiGraphics g) {
        Info i = info;
        if (i == null || KvrSpaceConfig.get().inspectorPosition() != KvrSpaceConfig.InspectorPosition.HUD) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        var font = mc.font;
        float a = appear();
        int accent = com.kasperstudios.kvr.config.KvrUiConfig.get().theme().accent & 0xFFFFFF;
        int w = Math.max(font.width(i.name()) + 30, font.width(i.id()) + 30);
        for (Line l : i.lines()) w = Math.max(w, font.width(l.text()) + 12);
        w = Math.min(w, 240);
        int h = 30 + i.lines().size() * 10 + 4;
        int x = 6 - Math.round((1 - a) * 24);
        int y = 6;
        g.fill(x, y, x + w, y + h, ArDraw.alpha(0x0B1220, 0.82f * a));
        g.fill(x, y, x + 2, y + h, ArDraw.alpha(accent, a));
        g.fill(x, y + h - 1, x + w, y + h, ArDraw.alpha(accent, 0.5f * a));
        if (!i.icon().isEmpty()) g.renderItem(i.icon(), x + 6, y + 6);
        g.drawString(font, i.name(), x + 26, y + 6, ArDraw.alpha(0xFFFFFF, Math.max(0.05f, a)), false);
        g.drawString(font, i.id(), x + 26, y + 17, ArDraw.alpha(accent, Math.max(0.05f, a)), false);
        int ly = y + 30;
        for (Line l : i.lines()) {
            g.drawString(font, l.text(), x + 6, ly, ArDraw.alpha(l.color() & 0xFFFFFF, Math.max(0.05f, a * ((l.color() >>> 24) / 255f))), false);
            ly += 10;
        }
    }
}

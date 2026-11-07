package com.kasperstudios.kvr.power;

import com.kasperstudios.kvr.platform.KVRServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Block 3 bootstrap (common, both sides) + the {@code /kvr power} debug command, so the power model
 * and the Note 7 can be tested before the power window (3b) and the dock plate (3c) exist.
 *
 * <pre>
 * /kvr power status
 * /kvr power on | hud_off | off
 * /kvr power charge &lt;0..100&gt;   (op level 2)
 * </pre>
 * Targets the glasses on the head, otherwise the ones in the main hand.
 */
public final class KVRPower {

    private KVRPower() {}

    public static void init() {
        PowerTable.setBatteryLife(com.kasperstudios.kvr.config.KvrConfig.server().batteryLife()); // 0020: no more 5-second batteries
        KVRServer.get().onRegisterCommands(KVRPower::registerCommands);
    }

    static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kvr").then(Commands.literal("power")
            .then(Commands.literal("status").executes(KVRPower::status))
            .then(Commands.literal("on").executes(c -> set(c, PowerState.ON)))
            .then(Commands.literal("hud_off").executes(c -> set(c, PowerState.HUD_OFF)))
            .then(Commands.literal("off").executes(c -> set(c, PowerState.OFF)))
            .then(Commands.literal("charge")
                .requires(com.kasperstudios.kvr.compat.VersionCompat::isGamemaster)
                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(KVRPower::charge)))));
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Optional<ItemStack> glasses = target(p);
        if (glasses.isEmpty()) return fail(c);
        PowerBuild b = GlassesPower.build(glasses.get()).orElseThrow();
        PowerData d = GlassesPower.data(glasses.get());
        long now = p.level().getGameTime();
        c.getSource().sendSuccess(() -> Component.translatable("kvr.power.status",
            d.state().name(), d.percent(b), Math.round(d.charge()), Math.round(d.capacity(b)),
            String.format("%.1f", b.draw(d.state())), b.maxDischarge(),
            d.overloading() ? d.secondsLeft(now) : "-", Math.round(d.temperature(now))), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c, PowerState state) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Optional<ItemStack> glasses = target(p);
        if (glasses.isEmpty()) return fail(c);
        PowerSimulator.Event e = GlassesPower.setState(glasses.get(), state, p.level().getGameTime());
        if (e == PowerSimulator.Event.SAVED) OverloadEffects.saved(p);
        c.getSource().sendSuccess(() -> Component.translatable("kvr.power.set", state.name()), false);
        return 1;
    }

    private static int charge(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Optional<ItemStack> glasses = target(p);
        if (glasses.isEmpty()) return fail(c);
        int percent = IntegerArgumentType.getInteger(c, "percent");
        PowerBuild b = GlassesPower.build(glasses.get()).orElseThrow();
        PowerData d = GlassesPower.data(glasses.get());
        d.setChargePercent(b, percent);
        GlassesPower.save(glasses.get(), d);
        c.getSource().sendSuccess(() -> Component.translatable("kvr.power.charged", percent), false);
        return 1;
    }

    private static Optional<ItemStack> target(ServerPlayer p) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        if (GlassesPower.build(head).isPresent()) return Optional.of(head);
        ItemStack hand = p.getMainHandItem();
        return GlassesPower.build(hand).isPresent() ? Optional.of(hand) : Optional.empty();
    }

    private static int fail(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.translatable("kvr.power.no_glasses"));
        return 0;
    }
}

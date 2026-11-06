package com.kasperstudios.kvr.dock;

import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.power.OverloadEffects;
import com.kasperstudios.kvr.power.PowerBuild;
import com.kasperstudios.kvr.power.PowerData;
import com.kasperstudios.kvr.power.PowerSimulator;
import com.kasperstudios.kvr.power.PowerTable;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Dock plate state (SPEC 2.6): the glasses lying on it, a 4000 FE power bank, the solar module.
 *
 * <ul>
 *   <li>Every tick: generation (redstone 1 FE/t, solar 4 FE/t in daylight) into the bank.</li>
 *   <li>Every 20 ticks: the glasses' own power is simulated up to now (so docked time is not billed
 *       later), then up to 50 FE/t × 20 is pushed into them. An overload keeps running on the plate
 *       and goes off right there with the ground outcome.</li>
 *   <li>Blockstate {@code has_glasses / charging / solar} drives the multipart models — no renderer.</li>
 * </ul>
 * Cable input: {@link #insertEnergy}, exposed by {@code KVREnergy} per loader.
 */
public class DockPlateBlockEntity extends BlockEntity {

    private ItemStack glasses = ItemStack.EMPTY;
    private boolean solar;
    private double buffer;

    public DockPlateBlockEntity(BlockPos pos, BlockState state) {
        super(KVRDock.blockEntityType(), pos, state);
    }

    // --- ticking -------------------------------------------------------------------------------

    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean sunny = DockLogic.sunny(level.getDayTime(), level.isRaining(), level.canSeeSky(pos.above()));
        double gen = DockLogic.generation(level.hasNeighborSignal(pos), solar, sunny);
        if (gen > 0 && buffer < PowerTable.DOCK_BUFFER) {
            buffer = DockLogic.fill(buffer, gen);
            setChanged();
        }

        long now = level.getGameTime();
        if (now % PowerTable.STEP_TICKS != 0) return;

        boolean charging = false;
        Optional<PowerBuild> build = GlassesPower.build(glasses);
        if (build.isPresent()) {
            PowerData d = GlassesPower.data(glasses);
            PowerSimulator.Event event = PowerSimulator.step(build.get(), d, now, com.kasperstudios.kvr.power.PowerTable.drainScale());
            if (event == PowerSimulator.Event.DETONATE) {
                ItemStack doomed = glasses;
                glasses = ItemStack.EMPTY;
                syncState(level, pos, false);
                OverloadEffects.detonateAt(level, doomed, Vec3.atCenterOf(pos));
                return;
            }
            if (event == PowerSimulator.Event.OVERLOAD_STARTED) OverloadEffects.startAt(level, pos);
            if (d.overloading() && now % PowerTable.SIREN_PERIOD_TICKS == 0) OverloadEffects.siren(level);

            double accepted = d.receive(build.get(), DockLogic.offer(buffer, PowerTable.STEP_TICKS));
            buffer -= accepted;
            charging = accepted > 0;
            GlassesPower.save(glasses, d);
            setChanged();
        }
        syncState(level, pos, charging);
    }

    /** Keep the blockstate in line with the contents; only writes when something changed. */
    void syncState(Level level, BlockPos pos, boolean charging) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(DockPlateBlock.CHARGING)) return;
        BlockState next = state
            .setValue(DockPlateBlock.HAS_GLASSES, !glasses.isEmpty())
            .setValue(DockPlateBlock.CHARGING, charging && !glasses.isEmpty())
            .setValue(DockPlateBlock.SOLAR, solar);
        if (next != state) level.setBlock(pos, next, Block.UPDATE_ALL);
    }

    // --- contents ------------------------------------------------------------------------------

    public boolean hasGlasses() {
        return !glasses.isEmpty();
    }

    /** Put glasses on the plate (takes one item from the stack). */
    public boolean placeGlasses(ItemStack stack) {
        if (hasGlasses() || !GlassesPower.isGlasses(stack)) return false;
        glasses = stack.split(1);
        changedContents();
        return true;
    }

    public ItemStack takeGlasses() {
        ItemStack out = glasses;
        glasses = ItemStack.EMPTY;
        changedContents();
        return out;
    }

    public boolean installSolar(ItemStack stack) {
        if (solar || !KVRDock.isSolarUpgrade(stack)) return false;
        stack.shrink(1);
        solar = true;
        changedContents();
        return true;
    }

    public boolean hasSolar() {
        return solar;
    }

    public ItemStack removeSolar() {
        if (!solar) return ItemStack.EMPTY;
        solar = false;
        changedContents();
        return KVRDock.solarUpgrade();
    }

    /** Glasses and solar module fall out when the plate is broken (power-bank energy is lost). */
    public void dropContents(Level level, BlockPos pos) {
        if (hasGlasses()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, takeGlasses());
        if (solar) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, removeSolar());
    }

    /** The glasses stack for the client tint (dye colour of the lying glasses). */
    public ItemStack glasses() {
        return glasses;
    }

    private void changedContents() {
        setChanged();
        if (level != null && !level.isClientSide) {
            syncState(level, worldPosition, false);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- energy (cables via KVREnergy) ---------------------------------------------------------

    public double buffer() {
        return buffer;
    }

    /** @return accepted FE; nothing is extracted from a dock, it only feeds glasses */
    public int insertEnergy(int max, boolean simulate) {
        int accepted = DockLogic.accept(buffer, max);
        if (!simulate && accepted > 0) {
            buffer += accepted;
            setChanged();
        }
        return accepted;
    }

    /** Fabric transactions write the final amount back. */
    public void setBuffer(double fe) {
        buffer = Math.max(0, Math.min(PowerTable.DOCK_BUFFER, fe));
        setChanged();
    }

    // --- persistence & client sync -------------------------------------------------------------

    //? if >=1.21.6 {
    /*// VERIFY on 1.21.11: ValueOutput / ValueInput replaced CompoundTag in block entities
    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        out.putDouble("buffer", buffer);
        out.putBoolean("solar", solar);
        out.storeNullable("glasses", ItemStack.CODEC, glasses.isEmpty() ? null : glasses);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        buffer = in.getDoubleOr("buffer", 0);
        solar = in.getBooleanOr("solar", false);
        glasses = in.read("glasses", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
    *///?} else if >=1.20.5 {
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("buffer", buffer);
        tag.putBoolean("solar", solar);
        if (!glasses.isEmpty()) tag.put("glasses", glasses.save(registries));
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer = tag.getDouble("buffer");
        solar = tag.getBoolean("solar");
        glasses = tag.contains("glasses") ? ItemStack.parseOptional(registries, tag.getCompound("glasses")) : ItemStack.EMPTY;
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("buffer", buffer);
        tag.putBoolean("solar", solar);
        if (!glasses.isEmpty()) tag.put("glasses", glasses.save(new net.minecraft.nbt.CompoundTag()));
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        buffer = tag.getDouble("buffer");
        solar = tag.getBoolean("solar");
        glasses = tag.contains("glasses") ? ItemStack.of(tag.getCompound("glasses")) : ItemStack.EMPTY;
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }
    *///?}

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    //? if >=1.21.5 {
    /*// VERIFY on 1.21.11: removal side effects moved from Block#onRemove to the block entity
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) dropContents(level, pos);
    }
    *///?}
}

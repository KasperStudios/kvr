package com.kasperstudios.kvr.dock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Dock plate (SPEC 2.6, ADR-003 4.2): a pressure-plate-thin charging pad.
 *
 * <ul>
 *   <li>RMB with glasses → they lie down on the plate (glass up, facing the player who placed the plate).</li>
 *   <li>RMB with {@code kvr:solar_upgrade} → autonomous outdoor station.</li>
 *   <li>RMB with an empty hand → glasses back; sneak + empty hand → solar module back.</li>
 *   <li>Breaking the plate drops both.</li>
 * </ul>
 * Properties are named exactly as {@code blockstates/dock_plate.json} expects.
 */
public class DockPlateBlock extends Block implements EntityBlock {

    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_GLASSES = BooleanProperty.create("has_glasses");
    public static final BooleanProperty CHARGING = BooleanProperty.create("charging");
    public static final BooleanProperty SOLAR = BooleanProperty.create("solar");

    private static final VoxelShape PLATE = Block.box(1, 0, 1, 15, 1, 15);
    private static final VoxelShape WITH_GLASSES = Shapes.or(PLATE, Block.box(3.5, 1, 6.5, 12.5, 2, 9.5));

    public DockPlateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HAS_GLASSES, false)
            .setValue(CHARGING, false)
            .setValue(SOLAR, false));
    }

    /** Suggested properties; add {@code setId(...)} on 1.21.2+ the same way KVRContent does for other blocks. */
    public static BlockBehaviour.Properties dockProperties() {
        return BlockBehaviour.Properties.of()
            .strength(0.5f)
            .sound(SoundType.METAL)
            .noOcclusion()
            .lightLevel(state -> state.getValue(CHARGING) ? 7 : 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_GLASSES, CHARGING, SOLAR);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HAS_GLASSES) ? WITH_GLASSES : PLATE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty(); // walk over it like a pressure plate
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DockPlateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (clientSide(level) || type != KVRDock.blockEntityType()) return null;
        return (l, pos, s, be) -> ((DockPlateBlockEntity) be).serverTick((ServerLevel) l, pos, s);
    }

    // --- interaction logic (version-neutral) ----------------------------------------------------

    /** @return true when the item was used on the plate */
    private static boolean useItem(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof DockPlateBlockEntity dock)) return false;
        if (dock.placeGlasses(stack) || dock.installSolar(stack)) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.3f);
            return true;
        }
        return false;
    }

    /** @return true when something was handed back */
    private static boolean useEmptyHand(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof DockPlateBlockEntity dock)) return false;
        ItemStack back = player.isShiftKeyDown() ? dock.removeSolar() : dock.takeGlasses();
        if (back.isEmpty()) return false;
        if (!player.getInventory().add(back)) player.drop(back, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.2f);
        return true;
    }

    private static boolean clientSide(Level level) {
        return com.kasperstudios.kvr.compat.VersionCompat.clientSide(level); // 0026: one boundary for the whole mod
    }

    // --- version-specific overrides --------------------------------------------------------------

    //? if >=1.21.2 {
    /*@Override
    protected net.minecraft.world.InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return net.minecraft.world.InteractionResult.PASS;
        if (clientSide(level)) return net.minecraft.world.InteractionResult.SUCCESS;
        return useItem(stack, level, pos, player) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (clientSide(level)) return net.minecraft.world.InteractionResult.SUCCESS;
        return useEmptyHand(level, pos, player) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
    }
    *///?} else if >=1.20.5 {
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (clientSide(level)) return net.minecraft.world.ItemInteractionResult.SUCCESS;
        return useItem(stack, level, pos, player)
            ? net.minecraft.world.ItemInteractionResult.SUCCESS
            : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (clientSide(level)) return net.minecraft.world.InteractionResult.SUCCESS;
        return useEmptyHand(level, pos, player) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        boolean done = stack.isEmpty() ? useEmptyHand(level, pos, player) : useItem(stack, level, pos, player);
        return done ? net.minecraft.world.InteractionResult.CONSUME : net.minecraft.world.InteractionResult.PASS;
    }
    *///?}

    //? if <1.21.5 {
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DockPlateBlockEntity dock) dock.dropContents(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    //?}
}

package plugin.talesloader.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import plugin.talesloader.net.ModNetwork;
import plugin.talesloader.registry.ModBlockEntities;
import plugin.talesloader.registry.ModDataComponents;

import javax.annotation.Nullable;
import java.util.List;

public class ChunkLoaderBlock extends BaseEntityBlock implements IWrenchable {
    public static final MapCodec<ChunkLoaderBlock> CODEC = simpleCodec(ChunkLoaderBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ChunkLoaderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ACTIVE, FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkLoaderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CHUNK_LOADER.get(), ChunkLoaderBlockEntity::serverTick);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Always fall through to useWithoutItem so holding fuel still opens the GUI.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity loader)) {
            return InteractionResult.PASS;
        }
        if (!loader.canUse(player)) {
            player.displayClientMessage(Component.translatable("message.talesloader.not_allowed", loader.getOwnerName()), true);
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(loader, pos);
            ModNetwork.sendLoaderInfo(serverPlayer, loader);
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ wrench

    /** Wrenching only turns the housing; the chunk tickets and the fuel stay untouched. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!mayWrench(context, false)) {
            return InteractionResult.FAIL;
        }
        return IWrenchable.super.onWrenched(state, context);
    }

    /** Sneak wrenching picks the loader up - same permission as breaking it by hand. */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!mayWrench(context, true)) {
            return InteractionResult.FAIL;
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        // Create's default only knows its own facing properties; ours is horizontal only.
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    private static boolean mayWrench(UseOnContext context, boolean removing) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof ChunkLoaderBlockEntity loader)) {
            return true;
        }
        boolean allowed = removing ? loader.canBreak(player) : loader.canUse(player);
        if (!allowed && !level.isClientSide()) {
            player.displayClientMessage(Component.translatable(
                    removing ? "message.talesloader.break_denied" : "message.talesloader.not_allowed",
                    loader.getOwnerName()), true);
        }
        return allowed;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity loader)) {
            return;
        }
        if (placer instanceof Player player) {
            loader.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
        Long stored = stack.get(ModDataComponents.STORED_FUEL.get());
        if (stored != null) {
            loader.setFuel(stored);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity loader) {
            loader.releaseEverything(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof ChunkLoaderBlockEntity loader && loader.getFuel() > 0L) {
            stack.set(ModDataComponents.STORED_FUEL.get(), loader.getFuel());
        }
        return List.of(stack);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        // No comparator readout - the loader must not drive automation of any kind.
        return false;
    }
}

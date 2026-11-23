package org.ae2bc.ioport;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseBlock;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;

public final class Ae2bcIoPortBlock extends AEBaseEntityBlock<Ae2bcIoPortBlockEntity> {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public Ae2bcIoPortBlock() {
        super(AEBaseBlock.metalProps());
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Override
    public void neighborChanged(
            @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
            @NonNull Block block, @NonNull BlockPos fromPos, boolean moving
    ) {
        Ae2bcIoPortBlockEntity port = this.getBlockEntity(level, pos);
        if (port != null) {
            port.updateRedstoneState();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public @NonNull IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, Ae2bcIoPortBlockEntity port) {
        return state.setValue(POWERED, port.isActive());
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        Ae2bcIoPortBlockEntity port = this.getBlockEntity(level, pos);
        if (port == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            MenuOpener.open(Ae2bcIoPortMenu.TYPE, player, MenuLocators.forBlockEntity(port));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}

package org.ae2bc.placer;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.ae2bc.registry.ItemRegistry;
import org.ae2bc.registry.component.ComponentPlacerRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockMakers;
import org.mockito.Mockito;

import java.util.List;

class PlacementPrecheckTest {
    @Test
    void placementWithoutCraftingCardSkipsTheMaterialPrecheckWalk() {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        ServerPlayer player = Mockito.mock(ServerPlayer.class);
        Mockito.when(player.level()).thenReturn(level);
        Mockito.when(level.dimension()).thenReturn(Level.OVERWORLD);
        ItemStack stack = new ItemStack(ItemRegistry.COMPONENT_PLACER.get());
        ComponentPlacerData.selection(stack, new ComponentPlacerSelection(
                Level.OVERWORLD.location(), BlockPos.ZERO, BlockPos.ZERO.east()));
        stack.set(ComponentPlacerRegistry.CABLE,
                new GenericStack(AEItemKey.of(AEParts.GLASS_CABLE.stack(AEColor.TRANSPARENT)), 1));
        stack.set(ComponentPlacerRegistry.PART,
                new GenericStack(AEItemKey.of(ItemRegistry.PP2MP_OUTPUT.get().getDefaultInstance()), 1));
        ComponentPlacerMenuHost host = Mockito.mock(
                ComponentPlacerMenuHost.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        Mockito.when(host.getItemStack()).thenReturn(stack);
        PlacementService.Result result = PlacementService.place(player, host);
        Assertions.assertEquals(2, result.placementFailed());
        Mockito.verify(level, Mockito.times(1)).isLoaded(BlockPos.ZERO);
        Mockito.verify(level, Mockito.times(1)).isLoaded(BlockPos.ZERO.east());
        Mockito.verify(host, Mockito.never()).getLinkStatus();
    }

    @Test
    void onePassCountsEmptyAndReusableCableLocationsAndKeepsPermissionChecks() {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        ServerPlayer player = Mockito.mock(ServerPlayer.class);
        IPartItem<?> cable = (IPartItem<?>) AEParts.GLASS_CABLE.stack(AEColor.TRANSPARENT).getItem();
        List<BlockPos> positions = List.of(
                BlockPos.ZERO, BlockPos.ZERO.east(), BlockPos.ZERO.west(),
                BlockPos.ZERO.north(), BlockPos.ZERO.south());
        Mockito.when(level.isLoaded(Mockito.any())).thenReturn(true);
        Mockito.when(level.mayInteract(Mockito.eq(player), Mockito.any())).thenReturn(true);
        Mockito.when(level.isLoaded(positions.get(3))).thenReturn(false);
        Mockito.when(level.mayInteract(player, positions.get(4))).thenReturn(false);
        Mockito.when(level.isEmptyBlock(positions.getFirst())).thenReturn(true);
        IPartHost reusable = Mockito.mock(IPartHost.class);
        IPart existingCable = Mockito.mock(IPart.class);
        Mockito.when(reusable.getPart(null)).thenReturn(existingCable);
        Mockito.doReturn(cable).when(existingCable).getPartItem();
        try (MockedStatic<PartHelper> parts = Mockito.mockStatic(
                PartHelper.class, Mockito.withSettings().mockMaker(MockMakers.INLINE))) {
            parts.when(() -> PartHelper.getPartHost(level, positions.get(1))).thenReturn(reusable);
            PlacementMaterials.Requirements required = PlacementMaterials.countEligible(level, player, positions, cable);
            Assertions.assertEquals(1, required.cables());
            Assertions.assertEquals(2, required.parts());
            parts.verify(() -> PartHelper.getPartHost(level, positions.getFirst()), Mockito.never());
        }
        for (BlockPos pos : positions) {
            Mockito.verify(level, Mockito.times(1)).isLoaded(pos);
        }
        Mockito.verify(level, Mockito.never()).isEmptyBlock(positions.get(3));
        Mockito.verify(level, Mockito.never()).isEmptyBlock(positions.get(4));
    }
}

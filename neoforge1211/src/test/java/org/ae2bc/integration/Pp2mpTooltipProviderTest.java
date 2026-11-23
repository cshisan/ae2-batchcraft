package org.ae2bc.integration;

import appeng.api.integrations.igtooltip.ClientRegistration;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.providers.ServerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlock;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.registry.BlockRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings("UnstableApiUsage")
class Pp2mpTooltipProviderTest {
    /* 验证转换器提示的两端注册与频率传递，覆盖未配置、高位值及切换频率。 */
    @Test
    void converterFrequencyProviderRegistersAndSendsServerStateWithoutNetwork() {
        Pp2mpTooltipProvider provider = new Pp2mpTooltipProvider();
        CommonRegistration common = Mockito.mock(CommonRegistration.class);
        ClientRegistration client = Mockito.mock(ClientRegistration.class);
        provider.registerCommon(common);
        provider.registerClient(client);

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                Ae2bcMod.MOD_ID, "fake_crafting_converter_frequency");
        ResourceLocation dataId = ResourceLocation.fromNamespaceAndPath(
                Ae2bcMod.MOD_ID, "fake_crafting_converter_frequency_data");
        ArgumentCaptor<ServerDataProvider<? super FakeCraftingConverterBlockEntity>> serverProvider =
                ArgumentCaptor.captor();
        Mockito.verify(common).addBlockEntityData(Mockito.eq(dataId),
                Mockito.eq(FakeCraftingConverterBlockEntity.class), serverProvider.capture());
        Mockito.verify(client).addBlockEntityBody(Mockito.eq(FakeCraftingConverterBlockEntity.class),
                Mockito.eq(FakeCraftingConverterBlock.class), Mockito.eq(id), Mockito.any(), Mockito.anyInt());

        BlockState state = BlockRegistry.FAKE_CRAFTING_CONVERTER.get().defaultBlockState();
        FakeCraftingConverterBlockEntity serverConverter = new FakeCraftingConverterBlockEntity(BlockPos.ZERO, state);
        Assertions.assertNull(serverConverter.getMainNode().getGrid());
        Assertions.assertFalse(serverConverter.getMainNode().isActive());
        Player player = Mockito.mock(Player.class);
        CompoundTag data = new CompoundTag();
        for (short frequency : new short[]{0, 1, (short) 0xABCD, (short) 0xFFFF}) {
            Assertions.assertTrue(serverConverter.setFrequency(frequency));
            serverProvider.getValue().provideServerData(player, serverConverter, data);
            Assertions.assertEquals(frequency, data.getShort("ae2bcFrequency"));
        }
    }
}

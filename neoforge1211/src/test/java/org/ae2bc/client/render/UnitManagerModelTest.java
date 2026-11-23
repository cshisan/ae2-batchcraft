package org.ae2bc.client.render;

import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class UnitManagerModelTest {
    @Test
    void ordinaryCableAndVanillaP2pKeepOriginalQuadsWithoutManagerGeometry() {
        BakedModel original = Mockito.mock(BakedModel.class);
        BakedModel base = Mockito.mock(BakedModel.class);
        BakedModel glass = Mockito.mock(BakedModel.class);
        BakedModel lights = Mockito.mock(BakedModel.class);
        List<BakedQuad> originalQuads = List.of(Mockito.mock(BakedQuad.class));
        Mockito.when(original.getQuads(Mockito.isNull(), Mockito.any(), Mockito.any(RandomSource.class),
                Mockito.any(ModelData.class), Mockito.any())).thenReturn(originalQuads);
        Pp2mpUnitManagerModel.ManagerBakedModel model =
                new Pp2mpUnitManagerModel.ManagerBakedModel(original, base, glass, lights);
        ModelData vanillaP2p = ModelData.builder().with(P2PTunnelFrequencyModelData.FREQUENCY, 0x10001L).build();
        Assertions.assertSame(originalQuads, model.getQuads(
                null, null, RandomSource.create(0), ModelData.EMPTY, RenderType.cutout()));
        Assertions.assertSame(originalQuads, model.getQuads(
                null, null, RandomSource.create(0), vanillaP2p, RenderType.cutout()));
        Mockito.verifyNoInteractions(lights);
        ModelData manager = vanillaP2p.derive().with(Pp2mpUnitManagerModel.PP2MP_UNIT_ID, 0x10002L).build();
        Mockito.clearInvocations(base, glass);
        model.getQuads(null, null, RandomSource.create(0), manager, RenderType.cutout());
        model.getQuads(null, null, RandomSource.create(0), manager, RenderType.cutout());
        Mockito.verifyNoInteractions(base, glass);
        Assertions.assertSame(originalQuads, model.getQuads(
                null, Direction.NORTH, RandomSource.create(0), manager, RenderType.cutout()));
    }
}

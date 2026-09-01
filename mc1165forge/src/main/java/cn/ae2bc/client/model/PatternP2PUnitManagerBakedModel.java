package cn.ae2bc.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.client.render.cablebus.CableBusBakedModel;
import appeng.client.render.cablebus.CableBusRenderState;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.util.Direction;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.IModelData;

/** Adds manager indicators without changing AE2's native cable render state. */
public final class PatternP2PUnitManagerBakedModel extends BakedModelWrapper<IBakedModel> {
    private PatternP2PUnitManagerBakedModel(IBakedModel original) {
        super(original);
    }

    public static IBakedModel wrap(IBakedModel model) {
        return model instanceof CableBusBakedModel ? new PatternP2PUnitManagerBakedModel(model) : model;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, Random random, IModelData data) {
        List<BakedQuad> cableQuads = originalModel.getQuads(state, side, random, data);
        if (side != null || MinecraftForgeClient.getRenderLayer() != RenderType.cutout()) {
            return cableQuads;
        }
        CableBusRenderState renderState = data.getData(CableBusRenderState.PROPERTY);
        if (renderState == null) return cableQuads;
        IPartHost host = findHost(renderState);
        if (host == null) return cableQuads;
        IPart part = host.getPart(AEPartLocation.INTERNAL);
        if (!(part instanceof PatternP2PUnitManagerPart)) return cableQuads;

        List<BakedQuad> result = new ArrayList<BakedQuad>(cableQuads.size() + 128);
        result.addAll(cableQuads);
        result.addAll(PatternP2PUnitManagerIndicators.getQuads((PatternP2PUnitManagerPart) part));
        return result;
    }

    private static IPartHost findHost(CableBusRenderState state) {
        if (state.getWorld() == null || state.getPos() == null) return null;
        net.minecraft.tileentity.TileEntity tile = state.getWorld().getBlockEntity(state.getPos());
        return tile instanceof IPartHost ? (IPartHost) tile : null;
    }
}

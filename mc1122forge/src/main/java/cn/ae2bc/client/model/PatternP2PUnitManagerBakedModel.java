package cn.ae2bc.client.model;

import java.util.ArrayList;
import java.util.List;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.block.networking.BlockCableBus;
import appeng.client.render.cablebus.CableBusBakedModel;
import appeng.client.render.cablebus.CableBusRenderState;
import cn.ae2bc.part.PatternP2PUnitManagerPart;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;

/** Adds manager indicators without changing AE2's native cable render state. */
public final class PatternP2PUnitManagerBakedModel implements IBakedModel {
    private final IBakedModel original;

    private PatternP2PUnitManagerBakedModel(IBakedModel original) {
        this.original = original;
    }

    public static IBakedModel wrap(IBakedModel model) {
        return model instanceof CableBusBakedModel ? new PatternP2PUnitManagerBakedModel(model) : model;
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        List<BakedQuad> cableQuads = original.getQuads(state, side, rand);
        if (side != null || MinecraftForgeClient.getRenderLayer() != BlockRenderLayer.CUTOUT) {
            return cableQuads;
        }
        if (!(state instanceof net.minecraftforge.common.property.IExtendedBlockState)) return cableQuads;
        net.minecraftforge.common.property.IExtendedBlockState extended =
                (net.minecraftforge.common.property.IExtendedBlockState) state;
        CableBusRenderState renderState = extended.getValue(BlockCableBus.RENDER_STATE_PROPERTY);
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
        IBlockAccess world = state.getWorld();
        if (world == null || state.getPos() == null) return null;
        TileEntity tile = world.getTileEntity(state.getPos());
        return tile instanceof IPartHost ? (IPartHost) tile : null;
    }

    @Override public boolean isAmbientOcclusion() { return original.isAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean isBuiltInRenderer() { return original.isBuiltInRenderer(); }
    @Override public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleTexture() {
        return original.getParticleTexture();
    }
    @Override public net.minecraft.client.renderer.block.model.ItemCameraTransforms getItemCameraTransforms() {
        return original.getItemCameraTransforms();
    }
    @Override public net.minecraft.client.renderer.block.model.ItemOverrideList getOverrides() {
        return original.getOverrides();
    }
}

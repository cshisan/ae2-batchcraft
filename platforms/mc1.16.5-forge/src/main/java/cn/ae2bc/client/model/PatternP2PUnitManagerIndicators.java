package cn.ae2bc.client.model;

import cn.ae2bc.part.PatternP2PUnitManagerPart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import appeng.api.util.AEColor;
import appeng.client.render.cablebus.CubeBuilder;
import appeng.util.Platform;
import cn.ae2bc.logic.PatternP2PUnitDimensions;
import cn.ae2bc.logic.PatternP2PUnitIdentityColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;

/** Builds the six-face frequency and unit-identity indicators missing from AE2 8's model. */
final class PatternP2PUnitManagerIndicators {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "appliedenergistics2", "part/p2p_tunnel_frequency");
    private static final Map<Long, List<BakedQuad>> CACHE = new HashMap<Long, List<BakedQuad>>();

    private PatternP2PUnitManagerIndicators() { }

    static List<BakedQuad> getQuads(PatternP2PUnitManagerPart manager) {
        Long frequencyFlags = manager.getModelData().getData(
                appeng.client.render.cablebus.P2PTunnelFrequencyModelData.FREQUENCY);
        long frequency = frequencyFlags == null ? 0 : frequencyFlags.longValue();
        long unit = Short.toUnsignedLong(PatternP2PUnitIdentityColors.encode(manager.getUnitId()));
        if ((frequency & 0x10000L) != 0) unit |= 0x10000L;
        long key = (frequency & 0x1ffffL) | (unit & 0x1ffffL) << 17;
        List<BakedQuad> cached = CACHE.get(key);
        if (cached != null) return cached;
        List<BakedQuad> built = build(frequency, unit);
        CACHE.put(key, built);
        return built;
    }

    private static List<BakedQuad> build(long frequency, long unit) {
        TextureAtlasSprite texture = Minecraft.getInstance().getModelManager()
                .getAtlas(AtlasTexture.LOCATION_BLOCKS).getSprite(TEXTURE);
        CubeBuilder builder = new CubeBuilder();
        builder.setTexture(texture);
        addFrequencyCorners(builder, frequency);
        addUnitCenter(builder, unit);
        builder.setEmissiveMaterial(false);
        return builder.getOutput();
    }

    private static void addFrequencyCorners(CubeBuilder builder, long flags) {
        AEColor[] colors = Platform.p2p().toColors((short) flags);
        boolean active = (flags & 0x10000L) != 0;
        builder.setEmissiveMaterial(active);
        for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
            setColor(builder, colors[colorIndex], active);
            float cellU = colorIndex % 2;
            float cellV = colorIndex / 2;
            for (int cornerU = 0; cornerU < 2; cornerU++) {
                for (int cornerV = 0; cornerV < 2; cornerV++) {
                    float u = (cornerU == 0 ? PatternP2PUnitDimensions.FRAME_MIN
                            : PatternP2PUnitDimensions.INNER_MAX) + cellU;
                    float v = (cornerV == 0 ? PatternP2PUnitDimensions.FRAME_MIN
                            : PatternP2PUnitDimensions.INNER_MAX) + cellV;
                    addCellToEveryFace(builder, u, v);
                }
            }
        }
    }

    private static void addUnitCenter(CubeBuilder builder, long flags) {
        AEColor[] colors = Platform.p2p().toColors((short) flags);
        boolean active = (flags & 0x10000L) != 0;
        builder.setEmissiveMaterial(active);
        for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
            setColor(builder, colors[colorIndex], active);
            float u = PatternP2PUnitDimensions.ID_MIN + colorIndex % 2;
            float v = PatternP2PUnitDimensions.ID_MIN + colorIndex / 2;
            addCellToEveryFace(builder, u, v);
        }
    }

    private static void addCellToEveryFace(CubeBuilder builder, float u, float v) {
        float min = PatternP2PUnitDimensions.FRAME_MIN;
        float max = PatternP2PUnitDimensions.FRAME_MAX;
        float depth = PatternP2PUnitDimensions.INDICATOR_DEPTH;
        builder.addCube(u, v, min - depth, u + 1, v + 1, min);
        builder.addCube(u, v, max, u + 1, v + 1, max + depth);
        builder.addCube(min - depth, v, u, min, v + 1, u + 1);
        builder.addCube(max, v, u, max + depth, v + 1, u + 1);
        builder.addCube(u, min - depth, v, u + 1, min, v + 1);
        builder.addCube(u, max, v, u + 1, max + depth, v + 1);
    }

    private static void setColor(CubeBuilder builder, AEColor color, boolean active) {
        if (active) {
            builder.setColorRGB(color.mediumVariant);
        } else {
            float scale = 0.3f / 255.0f;
            builder.setColorRGB((color.blackVariant >> 16 & 0xff) * scale,
                    (color.blackVariant >> 8 & 0xff) * scale,
                    (color.blackVariant & 0xff) * scale);
        }
    }
}

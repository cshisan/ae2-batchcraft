package org.ae2bc.client.render;

import appeng.api.util.AEColor;
import appeng.util.Platform;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.GeometryLoaderManager;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import org.ae2bc.part.Pp2mpIndicatorColor;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterModel;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipFile;

class FakeCraftingConverterBakingTest {
    @Test
    void v17ModelBakesBodyFloatingPaperAndCompleteInventoryModel() throws Exception {
        // 独立测试环境不派发客户端初始化事件，在测试范围内注册所需加载器。
        Field loadersField = GeometryLoaderManager.class.getDeclaredField("LOADERS");
        loadersField.setAccessible(true);
        Object previousLoaders = loadersField.get(null);
        Map<ResourceLocation, IGeometryLoader<?>> loaders = new HashMap<>();
        FakeCraftingConverterModel.registerGeometryLoaders(new ModelEvent.RegisterGeometryLoaders(loaders));
        loadersField.set(null, ImmutableMap.copyOf(loaders));
        NamedRenderTypeManager.init();
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                "ae2_batchcraft", "block/fake_crafting_converter"
        );
        try (SpriteContents contents = this.readBodyContents(location);
             SpriteContents paperContents = this.readPaperContents();
             SpriteContents frequencyContents = this.readFrequencyContents()) {
            TestSprite paperSprite = new TestSprite(paperContents);
            TestSprite frequencySprite = new TestSprite(frequencyContents);
            TestBaker baker = new TestBaker(new TestSprite(contents), paperSprite, frequencySprite);
            int originalPixel = contents.getOriginalImage().getPixelRGBA(0, 0);
            BakedModel model = baker.bake(location, BlockModelRotation.X0_Y0, baker.getModelTextureGetter());
            // 烘焙只借用图集资源，结束后原图必须仍可读取且未被修改。
            Assertions.assertEquals(originalPixel, contents.getOriginalImage().getPixelRGBA(0, 0));
            FakeCraftingConverterModel.ConverterBakedModel converter = Assertions.assertInstanceOf(
                    FakeCraftingConverterModel.ConverterBakedModel.class, model
            );
            BlockState state = Blocks.GLASS.defaultBlockState();
            RandomSource random = RandomSource.create(0);
            Assertions.assertTrue(model.getRenderTypes(state, random, ModelData.EMPTY).contains(RenderType.translucent()));
            Assertions.assertTrue(model.getRenderTypes(state, random, ModelData.EMPTY).contains(RenderType.solid()));
            Assertions.assertFalse(model.getRenderTypes(state, random, ModelData.EMPTY).contains(RenderType.cutout()));
            List<BakedQuad> solid = model.getQuads(state, null, random, ModelData.EMPTY, RenderType.solid());
            List<BakedQuad> glass = model.getQuads(state, null, random, ModelData.EMPTY, RenderType.translucent());
            List<BakedQuad> body = model.getQuads(state, null, random, ModelData.EMPTY, null);
            Assertions.assertFalse(solid.isEmpty());
            Assertions.assertFalse(glass.isEmpty());
            Assertions.assertEquals(body.size(), solid.size() + glass.size());
            Assertions.assertTrue(solid.stream().noneMatch(glass::contains));
            Assertions.assertTrue(model.getQuads(state, null, random, ModelData.EMPTY, RenderType.cutout()).isEmpty());
            int bodySize = body.size();
            List<BakedQuad> paper = converter.getPaperModel().getQuads(state, null, random,
                    ModelData.EMPTY, RenderType.cutout());
            Assertions.assertTrue(converter.getPaperModel().getRenderTypes(state, random, ModelData.EMPTY)
                    .contains(RenderType.cutout()));
            Assertions.assertEquals(534, bodySize);
            this.assertFrequencyIndicators(model, frequencySprite);
            // 原版纸张由正反面和贴图轮廓侧边组成，而不是六面贴图立方体。
            Assertions.assertTrue(paper.size() > 6);
            Assertions.assertTrue(paper.stream().allMatch(quad -> quad.getSprite() == paperSprite));
            JsonArray elements = this.readModel().getAsJsonArray("elements");
            int rotatedElements = 0;
            float floorTop = Float.NaN;
            float ceilingBottom = Float.NaN;
            for (JsonElement entry : elements) {
                JsonObject element = entry.getAsJsonObject();
                if ("EnergyInscription".equals(element.get("name").getAsString())) {
                    floorTop = element.getAsJsonArray("to").get(1).getAsFloat() / 16;
                }
                if ("AmethystCoreBase".equals(element.get("name").getAsString())) {
                    ceilingBottom = element.getAsJsonArray("from").get(1).getAsFloat() / 16;
                }
                if (element.has("extended_rotation")) {
                    this.assertRotatedVertices(element, body);
                    rotatedElements++;
                }
                if (element.get("name").getAsString().startsWith("ChamberSupport")) {
                    for (Direction direction : Direction.values()) {
                        Assertions.assertEquals(1, solid.stream()
                                        .filter(quad -> this.matchesFace(quad, element, direction)).count(),
                                "Each support face must be present in the solid layer");
                        Assertions.assertTrue(glass.stream()
                                        .noneMatch(quad -> this.matchesFace(quad, element, direction)),
                                "Support faces must never enter the glass layer");
                    }
                }
            }
            Assertions.assertEquals(0, rotatedElements);
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (BakedQuad quad : paper) {
                int[] vertices = quad.getVertices();
                int stride = vertices.length / 4;
                for (int vertex = 0; vertex < 4; vertex++) {
                    float x = Float.intBitsToFloat(vertices[vertex * stride]);
                    float y = Float.intBitsToFloat(vertices[vertex * stride + 1]);
                    float z = Float.intBitsToFloat(vertices[vertex * stride + 2]);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    // 旋转半径始终落在中央开口内，避免旋转时碰到四周玻璃斜片。
                    float radiusSquared = (x - 0.5f) * (x - 0.5f) + (z - 0.5f) * (z - 0.5f);
                    Assertions.assertTrue(radiusSquared < (3.0f / 16) * (3.0f / 16));
                }
            }
            // 纸张宽高为 5.0 模型像素，配合上下各 0.5 像素的浮动保持舱内间隙。
            Assertions.assertEquals(5.0f / 16, maxX - minX, 0.0001f);
            Assertions.assertEquals(5.0f / 16, maxY - minY, 0.0001f);
            Assertions.assertEquals(0.5f, (minX + maxX) / 2, 0.0001f);
            Assertions.assertEquals(FakeCraftingConverterModel.PAPER_CENTER_Y, (minY + maxY) / 2, 0.0001f);
            // 对实际烘焙顶点应用动画，最高和最低位置都必须与舱体保持间隙。
            Assertions.assertTrue(Float.isFinite(floorTop));
            Assertions.assertTrue(Float.isFinite(ceilingBottom));
            float lowestY = this.animatePaper(new Vector3f(0.5f, minY, 0.5f), 60, 0).y();
            float highestY = this.animatePaper(new Vector3f(0.5f, maxY, 0.5f), 20, 0).y();
            Assertions.assertTrue(lowestY > floorTop + 0.05f / 16, "Paper intersects the base at its lowest position");
            Assertions.assertTrue(highestY < ceilingBottom - 0.05f / 16, "Paper intersects the core at its highest position");

            ResourceLocation itemLocation = ResourceLocation.fromNamespaceAndPath(
                    "ae2_batchcraft", "item/fake_crafting_converter"
            );
            BakedModel item = baker.bake(itemLocation, BlockModelRotation.X0_Y0, baker.getModelTextureGetter());
            ItemStack stack = new ItemStack(Items.PAPER);
            for (ItemDisplayContext display : ItemDisplayContext.values()) {
                BakedModel displayed = item.applyTransform(display, new PoseStack(), display.name().endsWith("LEFTHAND"));
                Assertions.assertEquals(bodySize + paper.size(), displayed.getQuads(state, null, random,
                        ModelData.EMPTY, null).size());
                for (boolean fabulous : new boolean[]{false, true}) {
                    List<BakedModel> passes = displayed.getRenderPasses(stack, fabulous);
                    Assertions.assertEquals(4, passes.size());
                    this.assertItemPass(passes.get(0), "solid", solid.size() - 32, stack, fabulous);
                    this.assertItemPass(passes.get(1), "solid", 32, stack, fabulous);
                    this.assertItemPass(passes.get(2), "cutout", paper.size(), stack, fabulous);
                    this.assertItemPass(passes.get(3), "translucent", glass.size(), stack, fabulous);
                    this.assertUnboundIndicators(passes.get(1).getQuads(null, null, random, ModelData.EMPTY, null), false);
                }
            }
            Assertions.assertEquals(4, item.getRenderPasses(stack, false).size());
            Assertions.assertEquals(Set.of(location, ResourceLocation.withDefaultNamespace("item/paper"),
                    ResourceLocation.fromNamespaceAndPath("ae2", "part/p2p_tunnel_frequency")), baker.textures);
        } finally {
            loadersField.set(null, previousLoaders);
        }
    }

    @Test
    void importedAssetsUseEnglishNamesAndRetainTranslucentPixels() throws Exception {
        JsonObject model = this.readModel();
        Assertions.assertTrue(model.toString().chars().allMatch(character -> character < 128));
        Assertions.assertFalse(model.has("render_type"), "The loader selects a render layer for each face");
        Assertions.assertEquals("minecraft:block/block", model.get("parent").getAsString());
        Assertions.assertEquals(250, model.getAsJsonArray("elements").size());
        Assertions.assertEquals("minecraft:item/paper",
                model.getAsJsonObject("textures").get("paper").getAsString());
        Assertions.assertEquals(3, model.getAsJsonArray("groups").size());
        Set<String> names = new HashSet<>();
        Set<Integer> referencedCells = new HashSet<>();
        int frequencyCells = 0;
        for (JsonElement element : model.getAsJsonArray("elements")) {
            Assertions.assertTrue(names.add(element.getAsJsonObject().get("name").getAsString()));
            for (JsonElement face : element.getAsJsonObject().getAsJsonObject("faces").asMap().values()) {
                if ("#frequency".equals(face.getAsJsonObject().get("texture").getAsString())) {
                    frequencyCells++;
                    Assertions.assertTrue(element.getAsJsonObject().get("name").getAsString()
                            .matches(".*Frequency(Left|Right)Cell[1-4]"));
                    continue;
                }
                JsonArray uv = face.getAsJsonObject().getAsJsonArray("uv");
                int column = (int) Math.floor((uv.get(0).getAsDouble() + uv.get(2).getAsDouble()) / 4);
                int row = (int) Math.floor((uv.get(1).getAsDouble() + uv.get(3).getAsDouble()) / 4);
                Assertions.assertTrue(column >= 0 && column < 8 && row >= 0 && row < 8);
                Assertions.assertTrue(row * 8 + column < 18, "Face references an empty atlas cell");
                referencedCells.add(row * 8 + column);
                for (int index = 0; index < 4; index++) {
                    double origin = (index % 2 == 0 ? column : row) * 2;
                    double value = uv.get(index).getAsDouble();
                    Assertions.assertTrue(value >= origin - 0.000001 && value <= origin + 2 + 0.000001,
                            "Face crosses atlas cell boundaries");
                }
            }
        }
        Assertions.assertEquals(32, frequencyCells);
        Assertions.assertNull(FakeCraftingConverterBakingTest.class.getResource(
                "/assets/ae2_batchcraft/textures/block/fake_crafting_converter_energy.png"));
        Assertions.assertNull(FakeCraftingConverterBakingTest.class.getResource(
                "/assets/ae2_batchcraft/textures/block/fake_crafting_converter_port.png"));
        Assertions.assertFalse(names.contains("FloatingPaper"));
        Assertions.assertFalse(names.contains("PaperBacking"));
        Assertions.assertFalse(names.contains("LevitationBeam"));
        Assertions.assertFalse(names.stream().anyMatch(name -> name.startsWith("LevitationParticle")));
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                Assertions.assertTrue(names.contains("GoldChamberLatch" + x + z));
                Assertions.assertTrue(names.contains("SteppedDomeCorner" + x + z + "Lower"));
                Assertions.assertTrue(names.contains("SteppedDomeCorner" + x + z + "Upper"));
                Assertions.assertTrue(names.contains("SupportCap" + x + z));
                Assertions.assertFalse(names.contains("GoldUpperBevelTrim" + x + z));
            }
        }
        String path = "/assets/ae2_batchcraft/textures/block/fake_crafting_converter.png";
        try (InputStream stream = FakeCraftingConverterBakingTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream);
            try (NativeImage image = NativeImage.read(stream)) {
                Assertions.assertEquals(128, image.getWidth());
                Assertions.assertEquals(128, image.getHeight());
                int translucentPixels = 0;
                Set<Integer> occupiedCells = new HashSet<>();
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int alpha = image.getPixelRGBA(x, y) >>> 24;
                        int cell = (y / 16) * 8 + x / 16;
                        // 删除无引用的精灵图后前移补位，末尾空格完全透明。
                        if (cell >= 18) {
                            Assertions.assertEquals(0, image.getPixelRGBA(x, y),
                                    "Unused atlas cells must remain transparent");
                        }
                        if (alpha > 0) {
                            occupiedCells.add(cell);
                        }
                        if (alpha > 0 && alpha < 255) {
                            translucentPixels++;
                        }
                    }
                }
                Assertions.assertTrue(translucentPixels > 0);
                Assertions.assertEquals(18, occupiedCells.size());
                Assertions.assertEquals(referencedCells, occupiedCells,
                        "Every populated atlas cell must be referenced by the model");
            }
        }
    }

    /* 检查所有实际绘制面，玻璃拼接、阶梯顶角及接口不得共面重叠。 */
    @Test
    void importedBodyHasNoCoplanarOverlappingFaces() throws Exception {
        JsonArray elements = this.readModel().getAsJsonArray("elements");
        String[][] faceNames = {{"west", "east"}, {"down", "up"}, {"north", "south"}};
        String[] endpoints = {"from", "to"};
        for (JsonElement entry : elements) {
            JsonObject fastener = entry.getAsJsonObject();
            String name = fastener.get("name").getAsString();
            for (JsonElement otherEntry : elements) {
                JsonObject other = otherEntry.getAsJsonObject();
                if (other == fastener || other.has("extended_rotation")
                        || other.has("rotation")
                        && other.getAsJsonObject("rotation").get("angle").getAsDouble() != 0) {
                    continue;
                }
                for (int axis = 0; axis < 3; axis++) {
                    int next = (axis + 1) % 3;
                    int last = (axis + 2) % 3;
                    double overlapNext = Math.min(this.coordinate(fastener, "to", next),
                            this.coordinate(other, "to", next))
                            - Math.max(this.coordinate(fastener, "from", next),
                            this.coordinate(other, "from", next));
                    double overlapLast = Math.min(this.coordinate(fastener, "to", last),
                            this.coordinate(other, "to", last))
                            - Math.max(this.coordinate(fastener, "from", last),
                            this.coordinate(other, "from", last));
                    if (overlapNext <= 0.000001 || overlapLast <= 0.000001) {
                        continue;
                    }
                    for (int side = 0; side < 2; side++) {
                        if (!fastener.getAsJsonObject("faces").has(faceNames[axis][side])) {
                            continue;
                        }
                        for (int otherSide = 0; otherSide < 2; otherSide++) {
                            if (!other.getAsJsonObject("faces").has(faceNames[axis][otherSide])) {
                                continue;
                            }
                            Assertions.assertTrue(Math.abs(this.coordinate(fastener, endpoints[side], axis)
                                            - this.coordinate(other, endpoints[otherSide], axis)) > 0.000001,
                                    "Coplanar body faces: " + name + " / "
                                            + other.get("name").getAsString() + " / " + faceNames[axis][side]);
                        }
                    }
                }
            }
        }
    }

    /* 四个金色角扣保持相同尺寸，并相对方块中心镜像对称。 */
    @Test
    void chamberLatchesHaveEqualEdgeInsetsAndUnchangedDimensions() throws Exception {
        Map<String, JsonObject> corners = new HashMap<>();
        for (JsonElement element : this.readModel().getAsJsonArray("elements")) {
            JsonObject object = element.getAsJsonObject();
            String name = object.get("name").getAsString();
            if (name.matches("GoldChamberLatch[01]{2}")) {
                corners.put(name, object);
            }
        }
        Assertions.assertEquals(4, corners.size());
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                JsonObject corner = corners.get("GoldChamberLatch" + x + z);
                Assertions.assertNotNull(corner);
                double insetX = x == 0 ? this.coordinate(corner, "from", 0)
                        : 16 - this.coordinate(corner, "to", 0);
                double insetZ = z == 0 ? this.coordinate(corner, "from", 2)
                        : 16 - this.coordinate(corner, "to", 2);
                Assertions.assertEquals(0.25, insetX, 0.000001);
                Assertions.assertEquals(0.25, insetZ, 0.000001);
                Assertions.assertEquals(1.9,
                        this.coordinate(corner, "to", 0) - this.coordinate(corner, "from", 0), 0.000001);
                Assertions.assertEquals(1.9,
                        this.coordinate(corner, "to", 2) - this.coordinate(corner, "from", 2), 0.000001);
                Assertions.assertEquals(6.35, this.coordinate(corner, "from", 1), 0.000001);
                Assertions.assertEquals(7.45, this.coordinate(corner, "to", 1), 0.000001);
            }
        }
    }

    private double coordinate(JsonObject element, String endpoint, int axis) {
        return element.getAsJsonArray(endpoint).get(axis).getAsDouble();
    }

    @Test
    void paperBobsAndSpinsClockwiseAroundItsOwnCenterWithoutDrift() {
        Vector3f center = new Vector3f(0.5f, FakeCraftingConverterModel.PAPER_CENTER_Y, 0.5f);
        for (long time = 0; time <= 320; time++) {
            Vector3f moved = this.animatePaper(center, time, 0);
            Assertions.assertEquals(center.x(), moved.x(), 0.00001f);
            Assertions.assertEquals(center.z(), moved.z(), 0.00001f);
            Assertions.assertTrue(Math.abs(moved.y() - center.y()) <= 0.5f / 16 + 0.00001f);
        }
        Assertions.assertEquals(center.y() + 0.5f / 16, this.animatePaper(center, 20, 0).y(), 0.00001f);
        Assertions.assertEquals(center.y() - 0.5f / 16, this.animatePaper(center, 60, 0).y(), 0.00001f);
        Vector3f east = new Vector3f(0.75f, center.y(), 0.5f);
        Vector3f quarterTurn = this.animatePaper(east, 40, 0);
        // 从上方俯视，东侧点转向南侧点即为顺时针。
        Assertions.assertEquals(0.5f, quarterTurn.x(), 0.00001f);
        Assertions.assertEquals(0.75f, quarterTurn.z(), 0.00001f);
        Assertions.assertTrue(east.distance(this.animatePaper(east, 160, 0)) < 0.00001f);
        Assertions.assertTrue(this.animatePaper(east, 159, 0.9999f)
                .distance(this.animatePaper(east, 160, 0)) < 0.00001f);
        Assertions.assertTrue(this.animatePaper(east, 0, 0.5f)
                .distance(this.animatePaper(east, 1_000_000_000_000L, 0.5f)) < 0.00001f);
    }

    private Vector3f animatePaper(Vector3f point, long gameTime, float partialTick) {
        PoseStack poseStack = new PoseStack();
        FakeCraftingConverterRenderer.applyPaperAnimation(poseStack, gameTime, partialTick);
        return poseStack.last().pose().transformPosition(new Vector3f(point));
    }

    /* 从四面外侧检查颜色顺序、灯面位置和明暗，主体及玻璃不得随频率改变。 */
    private void assertFrequencyIndicators(BakedModel model, TextureAtlasSprite sprite) {
        BlockState state = Blocks.GLASS.defaultBlockState();
        RandomSource random = RandomSource.create(0);
        List<BakedQuad> originalBody = model.getQuads(state, null, random, ModelData.EMPTY, null).stream()
                .filter(quad -> quad.getSprite() != sprite).toList();
        for (short frequency : new short[]{0, 0x1234, (short) 0xabcd}) {
            for (boolean active : new boolean[]{false, true}) {
                ModelData data = ModelData.builder()
                        .with(FakeCraftingConverterBlockEntity.FREQUENCY_PROPERTY, frequency)
                        .with(FakeCraftingConverterBlockEntity.ACTIVE_PROPERTY, active).build();
                List<BakedQuad> all = model.getQuads(state, null, random, data, null);
                Assertions.assertEquals(originalBody, all.stream().filter(quad -> quad.getSprite() != sprite).toList());
                List<BakedQuad> lamps = all.stream().filter(quad -> quad.getSprite() == sprite).toList();
                Assertions.assertEquals(32, lamps.size());
                Assertions.assertEquals(lamps, model.getQuads(state, null, random, data, RenderType.solid())
                        .stream().filter(quad -> quad.getSprite() == sprite).toList());
                Assertions.assertTrue(model.getQuads(state, null, random, data, RenderType.translucent())
                        .stream().noneMatch(quad -> quad.getSprite() == sprite));
                AEColor[] colors = Platform.p2p().toColors(frequency);
                for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
                    List<BakedQuad> side = lamps.stream().filter(quad -> quad.getDirection() == direction).toList();
                    Assertions.assertEquals(8, side.size());
                    for (int cell = 0; cell < 8; cell++) {
                        BakedQuad quad = side.get(cell);
                        this.assertFrequencyCellPosition(quad, direction, cell);
                        int rgb = Pp2mpIndicatorColor.getRgb(colors[cell % 4], active);
                        int light = active ? LightTexture.FULL_BRIGHT : 0;
                        int[] vertices = quad.getVertices();
                        for (int vertex = 0; vertex < 4; vertex++) {
                            int offset = vertex * (vertices.length / 4);
                            Assertions.assertEquals(QuadTransformers.toABGR(0xff000000 | rgb), vertices[offset + 3]);
                            Assertions.assertEquals(light, vertices[offset + 6]);
                        }
                        Assertions.assertEquals(-1, quad.getTintIndex());
                        Assertions.assertFalse(quad.isShade());
                        Assertions.assertFalse(quad.hasAmbientOcclusion());
                    }
                }
                if (frequency == 0) {
                    this.assertUnboundIndicators(lamps, active);
                }
            }
        }
    }

    private void assertFrequencyCellPosition(BakedQuad quad, Direction direction, int cell) {
        int horizontalAxis = direction.getAxis() == Direction.Axis.Z ? 0 : 2;
        int normalAxis = direction.getAxis() == Direction.Axis.Z ? 2 : 0;
        float modelPlane = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15.55f : 0.45f;
        int[] vertices = quad.getVertices();
        float horizontalMin = Float.POSITIVE_INFINITY;
        float horizontalMax = Float.NEGATIVE_INFINITY;
        float heightMin = Float.POSITIVE_INFINITY;
        float heightMax = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * (vertices.length / 4);
            float horizontal = Float.intBitsToFloat(vertices[offset + horizontalAxis]);
            float height = Float.intBitsToFloat(vertices[offset + 1]);
            horizontalMin = Math.min(horizontalMin, horizontal);
            horizontalMax = Math.max(horizontalMax, horizontal);
            heightMin = Math.min(heightMin, height);
            heightMax = Math.max(heightMax, height);
            float plane = Float.intBitsToFloat(vertices[offset + normalAxis]);
            Assertions.assertTrue(Math.abs(plane - modelPlane / 16.0f) < 0.00001f
                    || Math.abs(plane - (1.0f - modelPlane / 16.0f)) < 0.00001f);
        }
        Assertions.assertTrue(horizontalMin >= 1.0f / 16 && horizontalMax <= 15.0f / 16);
        Assertions.assertTrue(heightMin >= 2.0f / 16 && heightMax <= 4.6f / 16);
        Assertions.assertEquals(1.0f / 16, horizontalMax - horizontalMin, 0.00001f);
        Assertions.assertEquals(1.0f / 16, heightMax - heightMin, 0.00001f);
    }

    /* AE2 的零频率为四个白色：通电亮白，未通电暗灰，物品模型使用后者。 */
    private void assertUnboundIndicators(List<BakedQuad> lamps, boolean active) {
        Assertions.assertEquals(32, lamps.size());
        for (BakedQuad quad : lamps) {
            int[] vertices = quad.getVertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * (vertices.length / 4);
                Assertions.assertEquals(active ? 0xffe0e0e0 : 0xff363636, vertices[offset + 3]);
                Assertions.assertEquals(active ? LightTexture.FULL_BRIGHT : 0, vertices[offset + 6]);
            }
        }
    }

    /* 验证每个物品绘制遍次只包含自身几何，并使用对应的普通或极佳画质渲染层。 */
    private void assertItemPass(BakedModel pass, String layer, int faces, ItemStack stack, boolean fabulous) {
        RenderTypeGroup types = NamedRenderTypeManager.get(ResourceLocation.withDefaultNamespace(layer));
        Assertions.assertEquals(List.of(fabulous ? types.entityFabulous() : types.entity()),
                pass.getRenderTypes(stack, fabulous));
        Assertions.assertEquals(faces, pass.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null).size());
    }

    private boolean matchesFace(BakedQuad quad, JsonObject element, Direction direction) {
        if (quad.getDirection() != direction) {
            return false;
        }
        int normalAxis = switch (direction.getAxis()) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int axis = 0; axis < 3; axis++) {
            double expectedMin = this.coordinate(element, "from", axis) / 16;
            double expectedMax = this.coordinate(element, "to", axis) / 16;
            if (axis == normalAxis) {
                if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                    expectedMin = expectedMax;
                } else {
                    expectedMax = expectedMin;
                }
            }
            float actualMin = Float.POSITIVE_INFINITY;
            float actualMax = Float.NEGATIVE_INFINITY;
            for (int vertex = 0; vertex < 4; vertex++) {
                float value = Float.intBitsToFloat(vertices[vertex * stride + axis]);
                actualMin = Math.min(actualMin, value);
                actualMax = Math.max(actualMax, value);
            }
            if (Math.abs(actualMin - expectedMin) > 0.00001 || Math.abs(actualMax - expectedMax) > 0.00001) {
                return false;
            }
        }
        return true;
    }

    private SpriteContents readBodyContents(ResourceLocation location) throws IOException {
        String path = "/assets/ae2_batchcraft/textures/block/fake_crafting_converter.png";
        try (InputStream stream = FakeCraftingConverterBakingTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream);
            NativeImage image = NativeImage.read(stream);
            return new SpriteContents(location, new FrameSize(image.getWidth(), image.getHeight()),
                    image, ResourceMetadata.EMPTY);
        }
    }

    private SpriteContents readPaperContents() throws IOException {
        // FML 测试隔离了原版资源类路径，直接读取其运行配置已有的原版资源包。
        String classpath = System.getProperty("legacyClassPath.file");
        Assertions.assertNotNull(classpath);
        Path resourcesPath = Files.readAllLines(Path.of(classpath)).stream()
                .filter(entry -> entry.endsWith("-client-extra-aka-minecraft-resources.jar"))
                .map(Path::of).findFirst().orElseThrow(() -> new IOException("Minecraft resources JAR is missing"));
        try (ZipFile resources = new ZipFile(resourcesPath.toFile());
             InputStream stream = resources.getInputStream(resources.getEntry("assets/minecraft/textures/item/paper.png"))) {
            NativeImage image = NativeImage.read(stream);
            return new SpriteContents(ResourceLocation.withDefaultNamespace("item/paper"),
                    new FrameSize(image.getWidth(), image.getHeight()), image, ResourceMetadata.EMPTY);
        }
    }

    private SpriteContents readFrequencyContents() throws IOException {
        try (InputStream stream = Platform.class.getResourceAsStream(
                "/assets/ae2/textures/part/p2p_tunnel_frequency.png")) {
            Assertions.assertNotNull(stream);
            NativeImage image = NativeImage.read(stream);
            return new SpriteContents(ResourceLocation.fromNamespaceAndPath("ae2", "part/p2p_tunnel_frequency"),
                    new FrameSize(image.getWidth(), image.getHeight()), image, ResourceMetadata.EMPTY);
        }
    }

    private JsonObject readModel() throws IOException {
        String path = "/assets/ae2_batchcraft/models/block/fake_crafting_converter.json";
        try (InputStream stream = FakeCraftingConverterBakingTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /* 用逐轴旋转独立计算八个顶点，验证任意角度没有被忽略或拍平成轴对齐矩形。 */
    private void assertRotatedVertices(JsonObject element, List<BakedQuad> quads) {
        JsonObject rotation = element.getAsJsonObject("extended_rotation");
        JsonArray origin = rotation.getAsJsonArray("origin");
        double[] angles = new double[3];
        if (rotation.has("axis")) {
            int axis = "xyz".indexOf(rotation.get("axis").getAsString());
            angles[axis] = Math.toRadians(rotation.get("angle").getAsDouble());
        } else {
            for (int axis = 0; axis < 3; axis++) {
                angles[axis] = Math.toRadians(rotation.get("xyz".substring(axis, axis + 1)).getAsDouble());
            }
        }
        List<Vector3f> expected = new ArrayList<>();
        for (int corner = 0; corner < 8; corner++) {
            double[] position = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                String endpoint = (corner & (1 << axis)) == 0 ? "from" : "to";
                position[axis] = element.getAsJsonArray(endpoint).get(axis).getAsDouble()
                        - origin.get(axis).getAsDouble();
            }
            for (int axis = 0; axis < 3; axis++) {
                int next = (axis + 1) % 3;
                int last = (axis + 2) % 3;
                double firstValue = position[next];
                double secondValue = position[last];
                position[next] = firstValue * Math.cos(angles[axis]) - secondValue * Math.sin(angles[axis]);
                position[last] = firstValue * Math.sin(angles[axis]) + secondValue * Math.cos(angles[axis]);
            }
            expected.add(new Vector3f(
                    (float) ((position[0] + origin.get(0).getAsDouble()) / 16),
                    (float) ((position[1] + origin.get(1).getAsDouble()) / 16),
                    (float) ((position[2] + origin.get(2).getAsDouble()) / 16)));
        }
        List<Vector3f> actual = new ArrayList<>();
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            List<Vector3f> faceVertices = new ArrayList<>();
            for (int vertex = 0; vertex < 4; vertex++) {
                faceVertices.add(new Vector3f(Float.intBitsToFloat(vertices[vertex * stride]),
                        Float.intBitsToFloat(vertices[vertex * stride + 1]),
                        Float.intBitsToFloat(vertices[vertex * stride + 2])));
            }
            if (faceVertices.stream().allMatch(point -> expected.stream()
                    .anyMatch(corner -> corner.distance(point) < 0.00001f))) {
                Assertions.assertEquals(FaceBakery.calculateFacing(quad.getVertices()), quad.getDirection());
                actual.addAll(faceVertices);
            }
        }
        Assertions.assertEquals(element.getAsJsonObject("faces").size() * 4, actual.size());
        for (Vector3f corner : expected) {
            Assertions.assertTrue(actual.stream().anyMatch(point -> corner.distance(point) < 0.00001f),
                    element.get("name").getAsString() + ": missing corner " + corner);
        }
    }

    private static final class TestSprite extends TextureAtlasSprite {
        private TestSprite(SpriteContents contents) {
            super(InventoryMenu.BLOCK_ATLAS, contents, 128, 128, 0, 0);
        }
    }

    private static final class TestBaker implements ModelBaker {
        private final Map<ResourceLocation, UnbakedModel> models = new HashMap<>();
        private final Set<ResourceLocation> textures = new HashSet<>();
        private final TextureAtlasSprite sprite;
        private final TextureAtlasSprite paperSprite;
        private final TextureAtlasSprite frequencySprite;

        private TestBaker(TextureAtlasSprite sprite, TextureAtlasSprite paperSprite, TextureAtlasSprite frequencySprite) {
            this.sprite = sprite;
            this.paperSprite = paperSprite;
            this.frequencySprite = frequencySprite;
            // 原版 block/block 只提供显示变换；无客户端资源包的几何测试使用空几何父模型。
            this.models.put(ResourceLocation.withDefaultNamespace("block/block"),
                    BlockModel.fromString("{\"gui_light\":\"side\"}"));
        }

        @Override
        public UnbakedModel getModel(ResourceLocation location) {
            UnbakedModel existing = this.models.get(location);
            if (existing != null) {
                return existing;
            }
            String path = "/assets/" + location.getNamespace() + "/models/" + location.getPath() + ".json";
            try (InputStream stream = FakeCraftingConverterBakingTest.class.getResourceAsStream(path)) {
                Assertions.assertNotNull(stream, path);
                BlockModel model = BlockModel.fromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                this.models.put(location, model);
                model.resolveParents(this::getModel);
                return model;
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public UnbakedModel getTopLevelModel(ModelResourceLocation location) {
            return this.getModel(location.id());
        }

        @Override
        @Deprecated
        public BakedModel bake(ResourceLocation location, ModelState state) {
            return this.bake(location, state, this.getModelTextureGetter());
        }

        @Override
        public BakedModel bake(ResourceLocation location, ModelState state,
                               Function<Material, TextureAtlasSprite> sprites) {
            return this.bakeUncached(this.getModel(location), state, sprites);
        }

        @Override
        public BakedModel bakeUncached(UnbakedModel model, ModelState state,
                                       Function<Material, TextureAtlasSprite> sprites) {
            return model.bake(this, sprites, state);
        }

        @Override
        public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
            return material -> {
                this.textures.add(material.texture());
                if (material.texture().equals(ResourceLocation.fromNamespaceAndPath("ae2", "part/p2p_tunnel_frequency"))) {
                    return this.frequencySprite;
                }
                return material.texture().equals(ResourceLocation.withDefaultNamespace("item/paper"))
                        ? this.paperSprite : this.sprite;
            };
        }
    }
}

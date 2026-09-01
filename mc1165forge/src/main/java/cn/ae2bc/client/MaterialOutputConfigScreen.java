package cn.ae2bc.client;

import appeng.client.gui.Icon;
import appeng.client.gui.me.items.PatternTermScreen;
import appeng.client.gui.widgets.TabButton;
import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.logic.DirectionLayout;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import com.mojang.blaze3d.matrix.MatrixStack;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** AE2-styled per-material output configuration for the pattern terminal. */
public final class MaterialOutputConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 142;

    private final PatternTermScreen parent;
    private final int windowId;
    private final int inputSlot;
    private final ItemStack input;
    private final DirectionLayout layout;
    private final Map<Direction, Ae2Button> directionButtons =
            new EnumMap<Direction, Ae2Button>(Direction.class);
    private final Map<MaterialOutputForm, Ae2Button> formButtons =
            new EnumMap<MaterialOutputForm, Ae2Button>(MaterialOutputForm.class);
    private MaterialOutputConfigData config;
    private Ae2Button autoButton;
    private TabButton backButton;

    public MaterialOutputConfigScreen(PatternTermScreen parent, int windowId, int inputSlot,
            ItemStack input, MaterialOutputConfigData config, DirectionLayout layout) {
        super(new TranslationTextComponent("gui.ae2_batchcraft.material_output_config.title"));
        this.parent = parent;
        this.windowId = windowId;
        this.inputSlot = inputSlot;
        this.input = input.copy();
        this.config = config == null ? MaterialOutputConfigData.EMPTY : config;
        this.layout = layout;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        addDirectionButton(left + 63, top + 36, layout.front());
        autoButton = addButton(directionButton(left + 118, top + 36, null));
        addDirectionButton(left + 8, top + 57, layout.left());
        addDirectionButton(left + 63, top + 57, Direction.UP);
        addDirectionButton(left + 118, top + 57, layout.right());
        addDirectionButton(left + 8, top + 78, Direction.DOWN);
        addDirectionButton(left + 63, top + 78, layout.back());

        int index = 0;
        for (MaterialOutputForm form : MaterialOutputForm.values()) {
            final MaterialOutputForm selectedForm = form;
            Ae2Button button = addButton(new Ae2Button(
                    left + 8 + index * 55, top + 116, 50, 18,
                    tr("gui.ae2_batchcraft.material_output_form." + form.getSerializedName()),
                    ignored -> selectOutputForm(selectedForm)));
            button.active = form.supports(input);
            formButtons.put(form, button);
            index++;
        }

        Minecraft minecraft = Minecraft.getInstance();
        backButton = addButton(new TabButton(Icon.ARROW_LEFT, parent.getTitle(),
                minecraft.getItemRenderer(), button -> onClose()));
        backButton.x = left + 152;
        backButton.y = top - 5;
        refreshSelections();
    }

    private Ae2Button directionButton(int x, int y, Direction direction) {
        return new Ae2Button(x, y, 50, 18, DirectionText.name(direction, layout),
                button -> selectDirection(direction));
    }

    private void addDirectionButton(int x, int y, Direction direction) {
        Ae2Button button = addButton(directionButton(x, y, direction));
        directionButtons.put(direction, button);
    }

    private void selectDirection(Direction direction) {
        config = config.withDirection(inputSlot, direction);
        syncConfig();
    }

    private void selectOutputForm(MaterialOutputForm form) {
        if (!form.supports(input)) return;
        config = config.withOutputForm(inputSlot, form);
        syncConfig();
    }

    private void syncConfig() {
        ((PatternEncodingTermMenuExtension) parent.getMenu()).ae2bc$setMaterialOutputConfig(config);
        ModNetwork.sendMaterialOutputConfig(windowId, config.toPacked());
        refreshSelections();
    }

    private void refreshSelections() {
        Direction selectedDirection = config.getDirection(inputSlot);
        if (autoButton != null) autoButton.active = selectedDirection != null;
        for (Map.Entry<Direction, Ae2Button> entry : directionButtons.entrySet()) {
            entry.getValue().active = entry.getKey() != selectedDirection;
        }
        for (Map.Entry<MaterialOutputForm, Ae2Button> entry : formButtons.entrySet()) {
            entry.getValue().active = entry.getKey().supports(input);
        }
    }

    @Override
    public void onClose() {
        ContainerSubScreen.switchTo(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        renderBackground(matrices);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        Ae2GuiSkin.draw(matrices, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        font.draw(matrices, title, left + 8, top + 6, 0x404040);
        font.draw(matrices, new TranslationTextComponent(
                "gui.ae2_batchcraft.output_direction_value",
                DirectionText.name(config.getDirection(inputSlot), layout)),
                left + 8, top + 24, 0x404040);
        font.draw(matrices, new TranslationTextComponent(
                "gui.ae2_batchcraft.material_output_form_value",
                tr("gui.ae2_batchcraft.material_output_form."
                        + config.getOutputForm(inputSlot).getSerializedName())),
                left + 8, top + 104, 0x404040);
        super.render(matrices, mouseX, mouseY, partialTick);

        if (autoButton != null && autoButton.isHovered()) {
            renderDirectionTooltip(matrices, null, mouseX, mouseY);
        }
        for (Map.Entry<Direction, Ae2Button> entry : directionButtons.entrySet()) {
            if (entry.getValue().isHovered()) {
                renderDirectionTooltip(matrices, entry.getKey(), mouseX, mouseY);
            }
        }
        for (Map.Entry<MaterialOutputForm, Ae2Button> entry : formButtons.entrySet()) {
            if (entry.getValue().isHovered()) {
                renderTooltip(matrices, new TranslationTextComponent(
                        "gui.ae2_batchcraft.material_output_form.tooltip",
                        tr("gui.ae2_batchcraft.material_output_form."
                                + entry.getKey().getSerializedName())), mouseX, mouseY);
            }
        }
    }

    private void renderDirectionTooltip(MatrixStack matrices, Direction direction, int mouseX, int mouseY) {
        renderTooltip(matrices, new TranslationTextComponent(
                "gui.ae2_batchcraft.output_direction.tooltip",
                DirectionText.name(direction, layout)), mouseX, mouseY);
    }

    private static ITextComponent tr(String key) {
        return new TranslationTextComponent(key);
    }
}

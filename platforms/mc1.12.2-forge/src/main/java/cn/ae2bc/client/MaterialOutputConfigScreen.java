package cn.ae2bc.client;

import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiTabButton;
import cn.ae2bc.logic.DirectionLayout;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

/** AE2-styled per-material output configuration for the pattern terminal. */
public final class MaterialOutputConfigScreen extends GuiScreen {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 142;
    private static final int ARROW_LEFT_ICON = 7;

    private final GuiPatternTerm parent;
    private final int windowId;
    private final int inputSlot;
    private final ItemStack input;
    private final DirectionLayout layout;
    private final Map<EnumFacing, Ae2Button> directionButtons =
            new EnumMap<EnumFacing, Ae2Button>(EnumFacing.class);
    private final Map<MaterialOutputForm, Ae2Button> formButtons =
            new EnumMap<MaterialOutputForm, Ae2Button>(MaterialOutputForm.class);
    private MaterialOutputConfigData config;
    private Ae2Button autoButton;
    private GuiTabButton backButton;

    public MaterialOutputConfigScreen(GuiPatternTerm parent, int windowId, int inputSlot,
            ItemStack input, DirectionLayout layout) {
        this.parent = parent;
        this.windowId = windowId;
        this.inputSlot = inputSlot;
        this.input = input.copy();
        this.layout = layout;
        this.config = MaterialOutputClientState.get(windowId);
    }

    @Override
    public void initGui() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        addDirectionButton(1, left + 63, top + 36, layout.front());
        autoButton = directionButton(0, left + 118, top + 36, null);
        buttonList.add(autoButton);
        addDirectionButton(2, left + 8, top + 57, layout.left());
        addDirectionButton(3, left + 63, top + 57, EnumFacing.UP);
        addDirectionButton(4, left + 118, top + 57, layout.right());
        addDirectionButton(5, left + 8, top + 78, EnumFacing.DOWN);
        addDirectionButton(6, left + 63, top + 78, layout.back());

        int index = 0;
        for (MaterialOutputForm form : MaterialOutputForm.values()) {
            Ae2Button button = new Ae2Button(20 + form.getId(),
                    left + 8 + index * 55, top + 116, 50, 18,
                    I18n.format("gui.ae2_batchcraft.material_output_form."
                            + form.getSerializedName()));
            button.enabled = form.supports(input);
            formButtons.put(form, button);
            buttonList.add(button);
            index++;
        }

        backButton = new GuiTabButton(left + 152, top - 5, ARROW_LEFT_ICON,
                I18n.format("gui.ae2_batchcraft.back"), mc.getRenderItem());
        buttonList.add(backButton);
        refreshSelections();
    }

    private Ae2Button directionButton(int id, int x, int y, EnumFacing direction) {
        Ae2Button button = new Ae2Button(id, x, y, 50, 18,
                DirectionText.name(direction, layout));
        if (direction != null) directionButtons.put(direction, button);
        return button;
    }

    private void addDirectionButton(int id, int x, int y, EnumFacing direction) {
        buttonList.add(directionButton(id, x, y, direction));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == backButton) {
            returnToParent();
            return;
        }
        if (button.id >= 0 && button.id <= 6) {
            EnumFacing direction = button.id == 0 ? null : directionForButton(button.id);
            config = config.withDirection(inputSlot, direction);
            syncConfig();
            return;
        }
        if (button.id >= 20 && button.id <= 22) {
            MaterialOutputForm form = MaterialOutputForm.fromId(button.id - 20);
            if (!form.supports(input)) return;
            config = config.withOutputForm(inputSlot, form);
            syncConfig();
        }
    }

    private EnumFacing directionForButton(int id) {
        switch (id) {
            case 1: return layout.front();
            case 2: return layout.left();
            case 3: return EnumFacing.UP;
            case 4: return layout.right();
            case 5: return EnumFacing.DOWN;
            case 6: return layout.back();
            default: return null;
        }
    }

    private void syncConfig() {
        MaterialOutputClientState.set(windowId, config);
        ModNetwork.sendMaterialOutputConfig(windowId, config.toPacked());
        refreshSelections();
    }

    private void refreshSelections() {
        EnumFacing selectedDirection = config.getDirection(inputSlot);
        if (autoButton != null) autoButton.enabled = selectedDirection != null;
        for (Map.Entry<EnumFacing, Ae2Button> entry : directionButtons.entrySet()) {
            entry.getValue().enabled = entry.getKey() != selectedDirection;
        }
        for (Map.Entry<MaterialOutputForm, Ae2Button> entry : formButtons.entrySet()) {
            entry.getValue().enabled = entry.getKey().supports(input);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            returnToParent();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void returnToParent() {
        ContainerSubScreen.switchTo(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        Ae2GuiSkin.draw(left, top, PANEL_WIDTH, PANEL_HEIGHT);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.material_output_config.title"),
                left + 8, top + 6, 0x404040);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.output_direction_value",
                        DirectionText.name(config.getDirection(inputSlot), layout)),
                left + 8, top + 24, 0x404040);
        fontRenderer.drawString(I18n.format("gui.ae2_batchcraft.material_output_form_value",
                        I18n.format("gui.ae2_batchcraft.material_output_form."
                                + config.getOutputForm(inputSlot).getSerializedName())),
                left + 8, top + 104, 0x404040);
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (autoButton != null && autoButton.isMouseOver()) {
            drawDirectionTooltip(null, mouseX, mouseY);
        }
        for (Map.Entry<EnumFacing, Ae2Button> entry : directionButtons.entrySet()) {
            if (entry.getValue().isMouseOver()) {
                drawDirectionTooltip(entry.getKey(), mouseX, mouseY);
            }
        }
        for (Map.Entry<MaterialOutputForm, Ae2Button> entry : formButtons.entrySet()) {
            if (entry.getValue().isMouseOver()) {
                drawHoveringText(I18n.format("gui.ae2_batchcraft.material_output_form.tooltip",
                        I18n.format("gui.ae2_batchcraft.material_output_form."
                                + entry.getKey().getSerializedName())), mouseX, mouseY);
            }
        }
    }

    private void drawDirectionTooltip(EnumFacing direction, int mouseX, int mouseY) {
        drawHoveringText(I18n.format("gui.ae2_batchcraft.output_direction.tooltip",
                DirectionText.name(direction, layout)), mouseX, mouseY);
    }
}

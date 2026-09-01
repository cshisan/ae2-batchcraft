package cn.ae2bc.client;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.me.items.PatternEncodingTermMenu;
import cn.ae2bc.logic.DirectionLayout;
import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.pattern.MaterialOutputForm;
import appeng.api.stacks.GenericStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.components.Tooltip;
import org.jetbrains.annotations.Nullable;

public final class MaterialOutputConfigScreen<C extends PatternEncodingTermMenu>
        extends AESubScreen<C, PatternEncodingTermScreen<C>> {
    private static final String STYLE = "/screens/ae2_batchcraft/material_output_config.json";
    private final int inputSlot;
    private final DirectionLayout layout;

    public MaterialOutputConfigScreen(PatternEncodingTermScreen<C> parent, int inputSlot, DirectionLayout layout) {
        super(parent, STYLE);
        this.inputSlot = inputSlot;
        this.layout = layout;

        addDirectionButton("auto", null);
        addDirectionButton("front", layout.front());
        addDirectionButton("left", layout.left());
        addDirectionButton("up", Direction.UP);
        addDirectionButton("right", layout.right());
        addDirectionButton("down", Direction.DOWN);
        addDirectionButton("opposite", layout.back());
        for (MaterialOutputForm form : MaterialOutputForm.values()) {
            String name = form.getSerializedName();
            var button = widgets.addButton("output" + Character.toUpperCase(name.charAt(0)) + name.substring(1),
                    Component.translatable("gui.ae2_batchcraft.material_output_form." + name),
                    () -> selectOutputForm(form));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.material_output_form.tooltip",
                    Component.translatable("gui.ae2_batchcraft.material_output_form." + name))));
            GenericStack input = GenericStack.fromItemStack(
                    getMenu().getProcessingInputSlots()[inputSlot].getItem());
            button.active = input != null && form.supports(input.what());
        }

        var icon = getMenu().getHost().getMainMenuIcon();
        var backButton = new TabButton(Icon.BACK, icon.getHoverName(), button -> returnToParent());
        widgets.add("back", backButton);
    }

    private void addDirectionButton(String widgetId, @Nullable Direction direction) {
        Component name = DirectionText.name(direction, layout);
        var button = widgets.addButton(widgetId, name, () -> selectDirection(direction));
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.output_direction.tooltip", name)));
    }

    private void selectDirection(@Nullable Direction direction) {
        ((PatternEncodingTermMenuExtension) getMenu()).ae2bc$setInputDirection(inputSlot, direction);
    }

    private void selectOutputForm(MaterialOutputForm form) {
        ((PatternEncodingTermMenuExtension) getMenu()).ae2bc$setMaterialOutputForm(inputSlot, form);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        var config = ((PatternEncodingTermMenuExtension) getMenu()).ae2bc$getMaterialOutputConfig();
        Component direction = DirectionText.name(config.getDirection(inputSlot), layout);
        Component outputForm = Component.translatable("gui.ae2_batchcraft.material_output_form."
                + config.getOutputForm(inputSlot).getSerializedName());
        setTextContent("output_direction", Component.translatable(
                "gui.ae2_batchcraft.output_direction_value", direction));
        setTextContent("output_form", Component.translatable(
                "gui.ae2_batchcraft.material_output_form_value", outputForm));
    }

    @Override
    public void onClose() {
        returnToParent();
    }
}

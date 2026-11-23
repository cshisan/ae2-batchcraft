package org.ae2bc.client.screen;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.widgets.TabButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.client.screen.component.DirectionLayout;
import org.ae2bc.client.screen.component.DirectionText;
import org.ae2bc.client.screen.component.SelectableAE2Button;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.pattern.Pp2mpEncodingTermMenu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Pp2mpMaterialOutputAttrScreen extends AESubScreen<Pp2mpEncodingTermMenu, PatternEncodingTermScreen<Pp2mpEncodingTermMenu>> {
    private final int inputSlot;
    private final DirectionLayout layout;
    private final List<Direction> directions = new ArrayList<>();
    private final List<SelectableAE2Button> directionButtons = new ArrayList<>();
    private final Map<MaterialOutputMode, SelectableAE2Button> modeButtons =
            new EnumMap<>(MaterialOutputMode.class);

    public Pp2mpMaterialOutputAttrScreen(
            PatternEncodingTermScreen<Pp2mpEncodingTermMenu> parent,
            int inputSlot, DirectionLayout layout
    ) {
        super(parent, "/screens/ae2_batchcraft/pp2mp_material_output_config.json");
        ScreenHeader.addTo(this.widgets);
        this.inputSlot = inputSlot;
        this.layout = layout;
        this.addDirectionButton("auto", null, "auto");
        this.addDirectionButton("front", layout.front(), "front");
        this.addDirectionButton("left", layout.left(), "left");
        this.addDirectionButton("up", Direction.UP, "up");
        this.addDirectionButton("right", layout.right(), "right");
        this.addDirectionButton("down", Direction.DOWN, "down");
        this.addDirectionButton("opposite", layout.back(), "opposite");
        for (MaterialOutputMode mode : MaterialOutputMode.values()) {
            String name = mode.name().toLowerCase(java.util.Locale.ROOT);
            SelectableAE2Button button = new SelectableAE2Button(
                    Component.translatable("gui.ae2_batchcraft.pattern.output_mode.button." + mode.name()),
                    () -> this.selectOutputMode(mode));
            this.widgets.add("output" + name, button);
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.ae2_batchcraft.material_output_form.tooltip",
                    Component.translatable("gui.ae2_batchcraft.pattern.output_mode." + mode.name()))));
            this.modeButtons.put(mode, button);
        }
        ItemStack icon = this.getMenu().getHost().getMainMenuIcon();
        this.widgets.add("back", new TabButton(Icon.BACK, icon.getHoverName(), button -> this.returnToParent()));
    }

    private void addDirectionButton(String id, Direction direction, String buttonName) {
        Component name = DirectionText.name(direction, this.layout);
        SelectableAE2Button button = new SelectableAE2Button(Component.translatable(
                "gui.ae2_batchcraft.direction.button." + buttonName
        ), () -> this.selectDirection(direction));
        this.widgets.add(id, button);
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.ae2_batchcraft.output_direction.tooltip", name)));
        this.directions.add(direction);
        this.directionButtons.add(button);
    }

    private void selectDirection(Direction direction) {
        this.getMenu().outputDirection(this.inputSlot, direction);
    }

    private void selectOutputMode(MaterialOutputMode mode) {
        this.getMenu().setOutputMode(this.inputSlot, mode);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        MaterialOutputMode mode = this.getMenu().mode(this.inputSlot);
        Direction direction = this.getMenu().outputDirection(this.inputSlot);
        for (MaterialOutputMode outputMode : MaterialOutputMode.values()) {
            SelectableAE2Button button = this.modeButtons.get(outputMode);
            button.active = outputMode != mode
                    && this.getMenu().supportsOutputMode(this.inputSlot, outputMode);
            button.setSelected(false);
        }
        for (int index = 0; index < this.directionButtons.size(); index++) {
            SelectableAE2Button button = this.directionButtons.get(index);
            button.active = this.directions.get(index) != direction;
            if (mode == MaterialOutputMode.FAKE_CRAFTING_CONVERTER) {
                button.active = false;
            }
            button.setSelected(false);
        }
        this.setTextContent("output_direction", Component.translatable(
                "gui.ae2_batchcraft.output_direction_value", DirectionText.name(direction, this.layout)));
        this.setTextContent("output_form", Component.translatable(
                "gui.ae2_batchcraft.material_output_form_value",
                Component.translatable("gui.ae2_batchcraft.pattern.output_mode." + mode.name())));
    }

    @Override
    public void onClose() {
        this.returnToParent();
    }
}

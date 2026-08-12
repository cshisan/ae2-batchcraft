package cn.ae2bc.client;

import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;

import cn.ae2bc.logic.EnergyDistributionMode;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** 1.16 implementation of the 1.21 energy-tunnel configuration screen. */
public final class PatternP2PTunnelEnergyScreen extends ContainerScreen<PatternP2PTunnelEnergyMenu> {
    private boolean pullEnabled;
    private EnergyDistributionMode mode;

    public PatternP2PTunnelEnergyScreen(PatternP2PTunnelEnergyMenu menu, PlayerInventory inventory, ITextComponent title) {
        super(menu, inventory, title);
        imageWidth = 200;
        imageHeight = 109;
        pullEnabled = menu.isPullEnabled();
        mode = menu.getMode();
    }

    @Override
    protected void init() {
        super.init();
        Button passive = addButton(new Ae2Button(leftPos + 12, topPos + 35, 85, 20,
                tr("gui.ae2_batchcraft.energy.mode.passive"), button -> {
                    pullEnabled = false;
                    sendCurrentSettings();
                    init(minecraft, width, height);
                }));
        Button active = addButton(new Ae2Button(leftPos + 103, topPos + 35, 85, 20,
                tr("gui.ae2_batchcraft.energy.mode.active"), button -> {
                    pullEnabled = true;
                    sendCurrentSettings();
                    init(minecraft, width, height);
                }));
        passive.active = pullEnabled;
        active.active = !pullEnabled;
        addButton(new Ae2Button(leftPos + 12, topPos + 77, 176, 20,
                distributionLabel(), button -> {
                    mode = mode.next();
                    button.setMessage(distributionLabel());
                    sendCurrentSettings();
                }));
    }

    private ITextComponent distributionLabel() {
        return tr("gui.ae2_batchcraft.energy_distribution_mode." + mode.getSerializedName());
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private void sendCurrentSettings() {
        ModNetwork.sendEnergySettings(menu, pullEnabled, mode);
    }

    @Override
    protected void renderBg(MatrixStack matrices, float partialTick, int mouseX, int mouseY) {
        Ae2GuiSkin.draw(matrices, leftPos, topPos, imageWidth, imageHeight);
        DashedSectionRenderer.draw(matrices, font, inputTitle(), leftPos, topPos, imageWidth, 28, 59);
        DashedSectionRenderer.draw(matrices, font,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration").getString(),
                leftPos, topPos, imageWidth, 70, 101);
    }

    @Override
    protected void renderLabels(MatrixStack matrices, int mouseX, int mouseY) {
        font.draw(matrices, title.getString(), 8, 6, 0x404040);
        DashedSectionRenderer.title(matrices, font, inputTitle(), 24);
        DashedSectionRenderer.title(matrices, font,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration").getString(), 66);
    }

    private String inputTitle() {
        return tr("gui.ae2_batchcraft.energy.section.input").getString() + " ("
                + tr("gui.ae2_batchcraft.energy.pull_interval",
                        PatternP2PTunnelEnergyPart.PULL_INTERVAL).getString() + ")";
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTick) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, partialTick);
        renderTooltip(matrices, mouseX, mouseY);
    }

    private static ITextComponent tr(String key, Object... args) {
        return new TranslationTextComponent(key, args);
    }
}

package cn.ae2bc.client;

import cn.ae2bc.menu.PatternP2PTunnelEnergyMenu;
import cn.ae2bc.network.ModNetwork;
import cn.ae2bc.part.PatternP2PTunnelEnergyPart;

import cn.ae2bc.logic.EnergyDistributionMode;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.TextComponentTranslation;

/** 1.12.2 implementation of the 1.21 energy-tunnel screen. */
public final class PatternP2PTunnelEnergyScreen extends GuiContainer {
    private final PatternP2PTunnelEnergyMenu menu;
    private boolean pullEnabled;
    private EnergyDistributionMode mode;

    public PatternP2PTunnelEnergyScreen(PatternP2PTunnelEnergyMenu menu, InventoryPlayer inventory) {
        super(menu);
        this.menu = menu;
        xSize = 200;
        ySize = 109;
        PatternP2PTunnelEnergyPart part = menu.getPart();
        pullEnabled = part != null && part.isPullEnabled();
        mode = part == null ? EnergyDistributionMode.EVEN : part.getDistributionMode();
    }

    @Override public void initGui() {
        super.initGui();
        GuiButton passive = new Ae2Button(1, guiLeft + 12, guiTop + 35, 85, 20,
                tr("gui.ae2_batchcraft.energy.mode.passive"));
        GuiButton active = new Ae2Button(2, guiLeft + 103, guiTop + 35, 85, 20,
                tr("gui.ae2_batchcraft.energy.mode.active"));
        passive.enabled = pullEnabled;
        active.enabled = !pullEnabled;
        buttonList.add(passive);
        buttonList.add(active);
        buttonList.add(new Ae2Button(3, guiLeft + 12, guiTop + 77, 176, 20, distributionLabel()));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            pullEnabled = false;
            sendCurrentSettings();
            rebuildGui();
        }
        else if (button.id == 2) {
            pullEnabled = true;
            sendCurrentSettings();
            rebuildGui();
        }
        else if (button.id == 3) {
            mode = mode.next();
            button.displayString = distributionLabel();
            sendCurrentSettings();
        }
    }

    private void rebuildGui() {
        buttonList.clear();
        initGui();
    }

    private String distributionLabel() {
        return tr("gui.ae2_batchcraft.energy_distribution_mode." + mode.getSerializedName());
    }

    @Override public void onGuiClosed() {
        super.onGuiClosed();
    }

    private void sendCurrentSettings() {
        ModNetwork.sendEnergy(menu, pullEnabled, mode);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        Ae2GuiSkin.draw(guiLeft, guiTop, xSize, ySize);
        DashedSectionRenderer.draw(fontRenderer, inputTitle(), guiLeft, guiTop, xSize, 28, 59);
        DashedSectionRenderer.draw(fontRenderer,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration"),
                guiLeft, guiTop, xSize, 70, 101);
    }

    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tr("item.ae2_batchcraft.pattern_p2p_tunnel_energy.name"), 8, 6, 0x404040);
        DashedSectionRenderer.title(fontRenderer, inputTitle(), 24);
        DashedSectionRenderer.title(fontRenderer,
                tr("gui.ae2_batchcraft.pattern_p2p_unit.section.energy_configuration"), 66);
    }

    private String inputTitle() {
        return tr("gui.ae2_batchcraft.energy.section.input") + " ("
                + tr("gui.ae2_batchcraft.energy.pull_interval",
                        PatternP2PTunnelEnergyPart.PULL_INTERVAL) + ")";
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    private static String tr(String key, Object... args) {
        return new TextComponentTranslation(key, args).getUnformattedText();
    }
}

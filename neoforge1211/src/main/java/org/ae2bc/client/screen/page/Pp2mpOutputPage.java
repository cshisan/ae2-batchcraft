package org.ae2bc.client.screen.page;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.component.FeatureComponent;
import org.ae2bc.client.screen.page.access.OutputAttrAccess;
import org.ae2bc.client.screen.page.access.SyncAttrAccess;
import org.ae2bc.core.part.OutputPageConfig;
import org.ae2bc.core.screen.Route;

public final class Pp2mpOutputPage extends AbstractPp2mpPage {
    private final SyncAttrAccess syncAccess;
    private final OutputAttrAccess outputAccess;
    private AE2Button enabledButton;
    private AE2Button disabledButton;

    public Pp2mpOutputPage(SyncAttrAccess syncAccess, OutputAttrAccess outputAccess) {
        super(Route.OUTPUT, 82, new FeatureComponent(
                Component.translatable("gui.ae2_batchcraft.page.general.product_extraction.title"),
                43, 74, null
        ));
        this.syncAccess = syncAccess;
        this.outputAccess = outputAccess;
    }

    /* 初始化输出端专属的产物提取配置按钮。 */
    @Override
    public void init(Pp2mpPageContext context) {
        super.init(context);
        if (this.enabledButton == null) {
            this.enabledButton = this.addButton(
                    "output_extraction_enabled",
                    Component.translatable("gui.ae2_batchcraft.enabled"),
                    () -> this.switchProductExtraction(true)
            );
            this.disabledButton = this.addButton(
                    "output_extraction_disabled",
                    Component.translatable("gui.ae2_batchcraft.disabled"),
                    () -> this.switchProductExtraction(false)
            );
            this.enabledButton.active = true;
            this.disabledButton.active = false;
        }
        this.layout();
    }

    private void switchProductExtraction(boolean open) {
        this.outputAccess.updateOutput(old -> new OutputPageConfig(open));
    }

    @Override
    public void update() {
        boolean synced = this.syncAccess != null && this.syncAccess.isSyncEnabled();
        OutputPageConfig output = this.outputAccess.output();
        this.enabledButton.active = !synced && !output.productExtraction();
        this.disabledButton.active = !synced && output.productExtraction();
    }

    @Override
    public void layout() {
        if (this.enabledButton == null) {
            return;
        }
        this.setWidgetBounds(this.enabledButton, 12, 50, 71, 18);
        this.setWidgetBounds(this.disabledButton, 89, 50, 71, 18);
    }
}

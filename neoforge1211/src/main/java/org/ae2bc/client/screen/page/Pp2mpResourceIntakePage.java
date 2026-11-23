package org.ae2bc.client.screen.page;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.network.chat.Component;
import org.ae2bc.client.screen.component.FeatureComponent;
import org.ae2bc.core.screen.Route;

public final class Pp2mpResourceIntakePage extends AbstractPp2mpPage {
    private final Runnable openProviderFilter;
    private AE2Button filterButton;

    public Pp2mpResourceIntakePage(Runnable openProviderFilter) {
        super(Route.RESOURCE_INTAKE, 82, new FeatureComponent(
                Component.translatable("gui.ae2_batchcraft.provider.section.configuration"),
                43, 74, null
        ));
        this.openProviderFilter = openProviderFilter;
    }

    /* 资源接收端分页只提供过滤入口，同步状态共用通用配置中已有的开关。 */
    @Override
    public void init(Pp2mpPageContext context) {
        super.init(context);
        if (this.filterButton == null) {
            this.filterButton = this.addButton("provider_filter",
                    Component.translatable("gui.ae2_batchcraft.provider_filter"), this.openProviderFilter);
        }
        this.layout();
    }

    @Override
    public void layout() {
        if (this.filterButton != null) {
            this.setWidgetBounds(this.filterButton, 12, 50, 148, 18);
        }
    }
}

package org.ae2bc.client.screen;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.me.common.ClientDisplaySlot;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.TabButton;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import com.google.common.primitives.Longs;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterMenu;

public final class Pp2mpProviderAmountScreen extends AESubScreen<Pp2mpProviderFilterMenu, Pp2mpProviderFilterScreen> {
    private final int markerSlot;
    private final GenericStack marker;
    private final int binding;
    private final long expectedAmount;
    private final NumberEntryWidget amount;

    public Pp2mpProviderAmountScreen(Pp2mpProviderFilterScreen parent, GenericStack marker, int markerSlot) {
        super(parent, "/screens/ae2_batchcraft/pp2mp_provider_amount.json");
        ScreenHeader.addTo(this.widgets);
        this.widgets.addBackgroundPanel("amount_slot");
        this.marker = marker;
        this.markerSlot = markerSlot;
        this.binding = this.getMenu().getBinding();
        this.expectedAmount = this.getMenu().getLimit(markerSlot);

        this.widgets.addButton("save", GuiText.Set.text(), this::confirm);
        this.widgets.add("back", new TabButton(
                Icon.BACK,
                this.getMenu().getHostTitle(),
                ignored -> this.returnToParent()
        ));
        this.amount = this.widgets.addNumberEntryWidget(
                "amountToStock", NumberEntryType.of(marker.what())
        );
        this.amount.setLongValue(this.expectedAmount);
        this.amount.setMaxValue(999999L * (long) marker.what().getAmountPerOperation());
        this.amount.setTextFieldStyle(this.style.getWidget("amountToStockInput"));
        this.amount.setMinValue(0);
        this.amount.setHideValidationIcon(true);
        this.amount.setOnConfirm(this::confirm);
        this.addClientSideSlot(new ClientDisplaySlot(marker), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    protected void init() {
        super.init();
        this.setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    /* 保存目标数量；若期间资源或配置来源已改变，服务端拒绝旧编辑。 */
    private void confirm() {
        this.amount.getLongValue().ifPresent(value -> {
            long normalized = Longs.constrainToRange(
                    value, 0, 999999L * (long) this.marker.what().getAmountPerOperation()
            );
            this.getMenu().setLimit(this.markerSlot, this.marker.what(), this.expectedAmount, normalized, this.binding);
            this.returnToParent();
        });
    }

    @Override
    public void onClose() {
        this.returnToParent();
    }
}

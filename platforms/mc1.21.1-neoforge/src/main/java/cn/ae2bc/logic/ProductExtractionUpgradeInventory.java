package cn.ae2bc.logic;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** A dedicated one-slot inventory that accepts only the product extraction card. */
public final class ProductExtractionUpgradeInventory extends AppEngInternalInventory implements IUpgradeInventory {
    private final ItemLike providerItem;
    private final Item productExtractionCard;
    private final Runnable onChanged;

    public ProductExtractionUpgradeInventory(ItemLike providerItem, Item productExtractionCard, Runnable onChanged) {
        super(1);
        this.providerItem = providerItem;
        this.productExtractionCard = productExtractionCard;
        this.onChanged = onChanged;
        setMaxStackSize(0, 1);
    }

    @Override
    public ItemLike getUpgradableItem() {
        return providerItem;
    }

    @Override
    public int getInstalledUpgrades(ItemLike upgradeCard) {
        return upgradeCard.asItem() == productExtractionCard && getStackInSlot(0).is(productExtractionCard) ? 1 : 0;
    }

    @Override
    public int getMaxInstalled(ItemLike upgradeCard) {
        return upgradeCard.asItem() == productExtractionCard ? 1 : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0 && stack.is(productExtractionCard);
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }
}

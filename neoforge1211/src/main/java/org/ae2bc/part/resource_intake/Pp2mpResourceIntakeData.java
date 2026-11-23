package org.ae2bc.part.resource_intake;

import appeng.api.stacks.AEKeyTypes;
import appeng.helpers.externalstorage.GenericStackInv;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.ItemLike;
import org.ae2bc.core.common.ProductExtractionParam;
import org.ae2bc.core.constant.Pp2mpResourceIntakeKeys;
import org.ae2bc.core.constant.ProductExtractionConfigConstants;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;

public final class Pp2mpResourceIntakeData {
    private final Runnable markForSaveAndUpdate;
    private final @Getter GenericStackInv buffer;
    private final @Getter Pp2mpProviderFilterData providerFilter;
    private @Getter boolean activePull;
    private @Getter int distributionMode;
    private @Getter ProductExtractionParam extractionParam = ProductExtractionParam.of(
            ProductExtractionConfigConstants.INTERVAL_DEFAULT,
            ProductExtractionConfigConstants.QUANTITY_DEFAULT
    );

    /* 统一持有供应配置和库存，并复用部件的变更通知。 */
    public Pp2mpResourceIntakeData(ItemLike item, Runnable markForSaveAndUpdate, Runnable providerFilterChanged) {
        this.markForSaveAndUpdate = markForSaveAndUpdate;
        this.buffer = new GenericStackInv(AEKeyTypes.getAll(), this.markForSaveAndUpdate, GenericStackInv.Mode.STORAGE, 36);
        this.providerFilter = new Pp2mpProviderFilterData(item, providerFilterChanged);
    }

    private static ProductExtractionParam normalizeExtractionParam(int interval, int quantity) {
        short normalizedInterval = (short) Math.clamp(
                interval,
                ProductExtractionConfigConstants.INTERVAL_MIN,
                ProductExtractionConfigConstants.INTERVAL_MAX
        );
        int normalizedQuantity = Math.clamp(
                quantity,
                ProductExtractionConfigConstants.QUANTITY_MIN,
                ProductExtractionConfigConstants.QUANTITY_MAX
        );
        return ProductExtractionParam.of(normalizedInterval, normalizedQuantity);
    }

    /* 恢复配置和实际插入不足的临时余量，不读取旧版供应缓存。 */
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        this.buffer.readFromChildTag(tag, Pp2mpResourceIntakeKeys.PENDING_INSERT, registries);
        this.activePull = tag.getBoolean(Pp2mpResourceIntakeKeys.ACTIVE_PULL);
        this.distributionMode = Math.floorMod(tag.getInt(Pp2mpResourceIntakeKeys.DISTRIBUTION_MODE), 3);
        this.extractionParam = Pp2mpResourceIntakeData.normalizeExtractionParam(
                tag.contains(Pp2mpResourceIntakeKeys.EXTRACTION_INTERVAL)
                        ? tag.getShort(Pp2mpResourceIntakeKeys.EXTRACTION_INTERVAL)
                        : ProductExtractionConfigConstants.INTERVAL_DEFAULT,
                tag.contains(Pp2mpResourceIntakeKeys.EXTRACTION_QUANTITY)
                        ? tag.getInt(Pp2mpResourceIntakeKeys.EXTRACTION_QUANTITY)
                        : ProductExtractionConfigConstants.QUANTITY_DEFAULT
        );
        this.providerFilter.readFromNBT(tag.getCompound(Pp2mpResourceIntakeKeys.PROVIDER_FILTER), registries);
    }

    /* 将供应配置和库存统一写入部件存档。 */
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        this.buffer.writeToChildTag(tag, Pp2mpResourceIntakeKeys.PENDING_INSERT, registries);
        tag.putBoolean(Pp2mpResourceIntakeKeys.ACTIVE_PULL, this.activePull);
        tag.putInt(Pp2mpResourceIntakeKeys.DISTRIBUTION_MODE, this.distributionMode);
        tag.putShort(Pp2mpResourceIntakeKeys.EXTRACTION_INTERVAL, this.extractionParam.getInterval());
        tag.putInt(Pp2mpResourceIntakeKeys.EXTRACTION_QUANTITY, this.extractionParam.getQuantity());
        CompoundTag filter = new CompoundTag();
        this.providerFilter.writeToNBT(filter, registries);
        tag.put(Pp2mpResourceIntakeKeys.PROVIDER_FILTER, filter);
    }

    /* 同步供应配置，库存内容仅参与持久化。 */
    public void writeToStream(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(this.activePull);
        buffer.writeByte(this.distributionMode);
        buffer.writeShort(this.extractionParam.getInterval());
        buffer.writeInt(this.extractionParam.getQuantity());
    }

    /* 完整读取同步配置并判断变化，不触发保存或再次同步。 */
    public boolean readFromStream(RegistryFriendlyByteBuf buffer) {
        boolean oldActivePull = this.activePull;
        int oldDistributionMode = this.distributionMode;
        short oldInterval = this.extractionParam.getInterval();
        int oldQuantity = this.extractionParam.getQuantity();
        this.activePull = buffer.readBoolean();
        this.distributionMode = Math.floorMod(buffer.readUnsignedByte(), 3);
        this.extractionParam = Pp2mpResourceIntakeData.normalizeExtractionParam(buffer.readShort(), buffer.readInt());
        return oldActivePull != this.activePull
                || oldDistributionMode != this.distributionMode
                || oldInterval != this.extractionParam.getInterval()
                || oldQuantity != this.extractionParam.getQuantity();
    }

    /* 切换主动获取状态，并通知部件保存、同步和唤醒调度。 */
    public void setActivePull(boolean activePull) {
        if (this.activePull != activePull) {
            this.activePull = activePull;
            this.markForSaveAndUpdate.run();
        }
    }

    /* 归一化分配模式，仅在配置实际变化时通知部件。 */
    public void setDistributionMode(int distributionMode) {
        int normalized = Math.floorMod(distributionMode, 3);
        if (this.distributionMode != normalized) {
            this.distributionMode = normalized;
            this.markForSaveAndUpdate.run();
        }
    }

    /* 更新资源接收端主动提取参数，并通知部件保存、同步和唤醒调度。 */
    public void setExtractionInterval(int interval) {
        short normalized = (short) Math.clamp(
                interval,
                ProductExtractionConfigConstants.INTERVAL_MIN,
                ProductExtractionConfigConstants.INTERVAL_MAX
        );
        if (this.extractionParam.getInterval() != normalized) {
            this.extractionParam = ProductExtractionParam.of(normalized, this.extractionParam.getQuantity());
            this.markForSaveAndUpdate.run();
        }
    }

    /* 更新资源接收端每轮主动提取数量，并通知部件保存、同步和唤醒调度。 */
    public void setExtractionQuantity(int quantity) {
        int normalized = Math.clamp(
                quantity,
                ProductExtractionConfigConstants.QUANTITY_MIN,
                ProductExtractionConfigConstants.QUANTITY_MAX
        );
        if (this.extractionParam.getQuantity() != normalized) {
            this.extractionParam = ProductExtractionParam.of(this.extractionParam.getInterval(), normalized);
            this.markForSaveAndUpdate.run();
        }
    }
}

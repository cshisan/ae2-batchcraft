package org.ae2bc.part.general.provider_filter;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocators;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.screen.Route;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class Pp2mpProviderFilterMenu extends AEBaseMenu {
    public static final MenuType<Pp2mpProviderFilterMenu> TYPE = MenuTypeBuilder
            .create(Pp2mpProviderFilterMenu::new, Pp2mpProviderFilterHost.class)
            .withMenuTitle(host -> host.getProviderFilterPart().getPartItem().asItem().getDescription())
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_provider_filter"));
    private static final String SET_LIMIT = "SetProviderLimit";
    private static final String SET_SYNC = "SetProviderSync";
    private static final String BACK = "BackToProviderParent";

    private final Pp2mpProviderFilterHost host;
    private final @Nullable Consumer<Boolean> syncSetter;
    private final ConfigInventory displayedMarkers = ConfigInventory
            .configTypes(Pp2mpProviderFilterData.SLOT_COUNT)
            .supportedTypes(Set.copyOf(AEKeyTypes.getAll()))
            .allowOverstacking(false).build();
    private final long[] displayedLimits = new long[Pp2mpProviderFilterData.SLOT_COUNT];
    private boolean synced;
    private boolean whitelist = true;
    private Pp2mpProviderFilterData lastSource;
    private int lastVersion = Integer.MIN_VALUE;
    private boolean lastSynced;
    private @Getter boolean initialized;
    private @Getter int menuVersion;

    /* 所有宿主共用一个菜单类型，只向可编辑的本地配置写入。 */
    public Pp2mpProviderFilterMenu(int id, Inventory inventory, Pp2mpProviderFilterHost host) {
        super(Pp2mpProviderFilterMenu.TYPE, id, inventory, host.getProviderFilterPart());
        this.host = host;
        this.syncSetter = host.getProviderFilterSyncSetter();
        this.synced = host.isProviderFilterSynced();
        ConfigMenuInventory markers = new ConfigMenuInventory(this.displayedMarkers) {
            @Override
            public void setItemDirect(int slot, @NonNull ItemStack stack) {
                if (Pp2mpProviderFilterMenu.this.isServerSide()) {
                    if (!Pp2mpProviderFilterMenu.this.isEditable()) {
                        return;
                    }
                    Pp2mpProviderFilterData local = Pp2mpProviderFilterMenu.this.host.getProviderFilter();
                    GenericStack marker = this.convertToSuitableStack(stack);
                    AEKey key = marker == null ? null : marker.what();
                    if (!Objects.equals(local.markers().getKey(slot), key)) {
                        local.setLimit(slot, 0);
                    }
                    local.markers().setStack(slot, marker);
                }
                super.setItemDirect(slot, stack);
            }
        };
        for (int slot = 0; slot < Pp2mpProviderFilterData.SLOT_COUNT; slot++) {
            this.addSlot(new FakeSlot(markers, slot) {
                @Override
                public boolean canSetFilterTo(ItemStack stack) {
                    return Pp2mpProviderFilterMenu.this.isEditable() && super.canSetFilterTo(stack);
                }

                /* 客户端槽位更新不经过玩家编辑权限校验，保证同步模式仍能刷新标记。 */
                @Override
                public void set(ItemStack stack) {
                    if (Pp2mpProviderFilterMenu.this.isClientSide()) {
                        this.initialize(stack);
                    } else {
                        super.set(stack);
                    }
                }

                @Override
                public void setFilterTo(ItemStack stack) {
                    if (this.canSetFilterTo(stack)) {
                        super.setFilterTo(stack);
                    }
                }
            }, SlotSemantics.CONFIG);
        }
        this.setupUpgrades(this.host.getProviderFilter().upgrades());
        this.updateUpgradeAccess();
        this.createPlayerInventorySlots(inventory);
        this.registerClientAction(SET_LIMIT, LimitChange.class, this::applyLimit);
        this.registerClientAction(SET_SYNC, Boolean.class, value -> {
            if (this.isServerSide() && value != null && this.hasSyncSwitch() && this.syncSetter != null) {
                this.syncSetter.accept(value);
                this.broadcastChanges();
            }
        });
        this.registerClientAction(BACK, () -> {
            MenuType<?> parentMenu = this.host.getProviderFilterParentMenu();
            if (this.isServerSide() && parentMenu != null) {
                MenuOpener.returnTo(parentMenu, this.getPlayer(), MenuLocators.forPart(this.host.getProviderFilterPart()));
            }
        });
    }

    public Component getHostTitle() {
        return this.host.getProviderFilterPart().getPartItem().asItem().getDescription();
    }

    public @Nullable Route getParentRoute() {
        return this.host.getProviderFilterParentRoute();
    }

    public boolean hasParent() {
        return this.host.getProviderFilterParentMenu() != null;
    }

    /* 过滤子页不能改变主页面的同步状态。 */
    public boolean hasSyncSwitch() {
        return !this.hasParent() && this.syncSetter != null;
    }

    public boolean isSynced() {
        return this.isClientSide() ? this.synced : this.host.isProviderFilterSynced();
    }

    public void setSynced(boolean value) {
        if (this.initialized && this.hasSyncSwitch()) {
            this.sendClientAction(SET_SYNC, value);
        }
    }

    public boolean isEditable() {
        return this.initialized && !this.isSynced();
    }

    public boolean isWhitelist() {
        return this.isClientSide() ? this.whitelist : this.host.getEffectiveProviderFilter().isWhitelist();
    }

    public long getLimit(int slot) {
        return slot >= 0 && slot < this.displayedLimits.length ? this.displayedLimits[slot] : 0;
    }

    public AEKey getMarkerKey(int slot) {
        return slot >= 0 && slot < this.displayedMarkers.size() ? this.displayedMarkers.getKey(slot) : null;
    }

    /* 使用 AEKey 编码完整资源身份，避免原始注册 ID 丢失数据组件或其他类型信息。 */
    public void setLimit(int slot, AEKey expectedKey, long amount, int menuVersion) {
        if (this.isServerSide() || !this.isEditable() || !this.isWhitelist()) {
            return;
        }
        if (this.menuVersion == menuVersion && Objects.equals(this.getMarkerKey(slot), expectedKey)) {
            AEKey.CODEC.encodeStart(this.registryAccess().createSerializationContext(
                    JsonOps.INSTANCE), expectedKey
            ).result().ifPresent(
                    key -> this.sendClientAction(
                            SET_LIMIT, new LimitChange(slot, Math.max(0, amount), menuVersion, key)
                    )
            );
        }
    }

    /* 同时校验配置来源、版本与窗口打开时的资源，拒绝过期或越界的数量编辑。 */
    private void applyLimit(@Nullable LimitChange change) {
        if (!this.isServerSide() || !this.isEditable() || change == null
                || change.slot() < 0 || change.slot() >= Pp2mpProviderFilterData.SLOT_COUNT
                || change.amount() < 0 || change.key() == null || change.key().isJsonNull()
                || change.menuVersion() != this.menuVersion || this.lastSource == null
                || this.host.getEffectiveProviderFilter() != this.lastSource
                || this.lastSource.version() != this.lastVersion) {
            return;
        }
        Pp2mpProviderFilterData local = this.host.getProviderFilter();
        AEKey currentKey = local.markers().getKey(change.slot());
        if (!local.isWhitelist() || currentKey == null
                || !Objects.equals(currentKey, this.displayedMarkers.getKey(change.slot()))
                || change.amount() > 999999L * currentKey.getAmountPerOperation()) {
            return;
        }
        AEKey expectedKey = AEKey.CODEC.parse(
                this.registryAccess().createSerializationContext(JsonOps.INSTANCE), change.key()).result().orElse(null);
        if (currentKey.equals(expectedKey)) {
            local.setLimit(change.slot(), change.amount());
        }
    }

    public void returnToParent() {
        this.sendClientAction(BACK);
    }

    /* 按配置版本刷新整组快照；来源和同步权限切换也推进菜单版本。 */
    private Pp2mpProviderFilterPacket prepareSync(boolean force) {
        if (this.isClientSide()) {
            return null;
        }
        Pp2mpProviderFilterData source = this.host.getEffectiveProviderFilter();
        this.updateUpgradeAccess();
        boolean synced = this.host.isProviderFilterSynced();
        if (!force && source == this.lastSource && source.version() == this.lastVersion && synced == this.lastSynced) {
            return null;
        }
        List<Long> limits = new ArrayList<>(Pp2mpProviderFilterData.SLOT_COUNT);
        for (int slot = 0; slot < Pp2mpProviderFilterData.SLOT_COUNT; slot++) {
            limits.add(source.isMarked(slot) ? source.limit(slot) : 0L);
        }
        Pp2mpProviderFilterPacket packet = new Pp2mpProviderFilterPacket(
                this.containerId, this.menuVersion + 1,
                synced, source.isWhitelist(), limits
        );
        for (int slot = 0; slot < Pp2mpProviderFilterData.SLOT_COUNT; slot++) {
            AEKey key = source.markers().getKey(slot);
            this.displayedMarkers.setStack(slot, key == null ? null : new GenericStack(key, 0));
            this.displayedLimits[slot] = packet.limits().get(slot);
        }
        this.lastSource = source;
        this.lastVersion = source.version();
        this.lastSynced = synced;
        this.menuVersion = packet.menuVersion();
        return packet;
    }

    @Override
    public void broadcastChanges() {
        Pp2mpProviderFilterPacket packet = this.prepareSync(false);
        super.broadcastChanges();
        if (this.initialized && packet != null) {
            this.sendPacketToClient(packet);
        }
    }

    /* 完整槽位数据先发送，随后发送状态；初始化期间的 broadcastChanges 不提前发包。 */
    @Override
    public void sendAllDataToRemote() {
        Pp2mpProviderFilterPacket packet = this.prepareSync(true);
        super.sendAllDataToRemote();
        if (packet != null) {
            this.sendPacketToClient(packet);
            this.initialized = true;
            this.updateUpgradeAccess();
        }
    }

    /* 用版本差判断新旧，兼容 int 连续递增跨越最大值；拒绝重复与过期状态。 */
    public void receive(Pp2mpProviderFilterPacket packet) {
        if (!this.isClientSide() || packet.containerId() != this.containerId
                || (this.initialized && packet.menuVersion() - this.menuVersion <= 0)) {
            return;
        }
        this.synced = packet.synced();
        this.menuVersion = packet.menuVersion();
        this.whitelist = packet.whitelist();
        for (int slot = 0; slot < Pp2mpProviderFilterData.SLOT_COUNT; slot++) {
            this.displayedLimits[slot] = packet.limits().get(slot);
        }
        this.initialized = true;
        this.updateUpgradeAccess();
    }

    private boolean isFilterSlot(int slot) {
        return slot >= 0 && slot < this.slots.size()
                && (this.getSlotSemantic(this.slots.get(slot)) == SlotSemantics.CONFIG
                || this.getSlotSemantic(this.slots.get(slot)) == SlotSemantics.UPGRADE);
    }

    /* 保持卡槽可见，但同步时禁止包括双击收集在内的取放操作。 */
    private void updateUpgradeAccess() {
        for (Slot slot : this.getSlots(SlotSemantics.UPGRADE)) {
            if (slot instanceof RestrictedInputSlot upgradeSlot) {
                upgradeSlot.setAllowEdit(this.isEditable());
            }
        }
    }

    /* 同时阻断原版点击、AE2 操作和 JEI 标记包，防止绕过同步只读状态。 */
    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        this.updateUpgradeAccess();
        if (!this.isEditable() && this.isFilterSlot(slot)) {
            return;
        }
        super.clicked(slot, button, type, player);
    }

    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        this.updateUpgradeAccess();
        if (!this.isEditable() && this.isFilterSlot(slot)) {
            return;
        }
        super.doAction(player, action, slot, id);
    }

    @Override
    public void setFilter(int slot, ItemStack item) {
        if (this.isEditable()) {
            super.setFilter(slot, item);
        }
    }

    @Override
    public @NonNull ItemStack quickMoveStack(Player player, int slot) {
        return this.isEditable() ? super.quickMoveStack(player, slot) : ItemStack.EMPTY;
    }

    @Override
    public boolean isValidForSlot(Slot slot, ItemStack stack) {
        return (this.isEditable() || !this.isFilterSlot(slot.index)) && super.isValidForSlot(slot, stack);
    }

    @Override
    public void swapSlotContents(int first, int second) {
        if (this.isEditable() || (!this.isFilterSlot(first) && !this.isFilterSlot(second))) {
            super.swapSlotContents(first, second);
        }
    }

    private record LimitChange(int slot, long amount, int menuVersion, JsonElement key) {
    }

}

package cn.ae2bc.mixin;

import appeng.api.networking.IManagedGridNode;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.parts.automation.StackWorldBehaviors;
import cn.ae2bc.extension.PatternProviderExtractionExtension;
import cn.ae2bc.extension.ImmediatePatternProviderReturnInventory;
import cn.ae2bc.logic.ProductExtractionSettings;
import cn.ae2bc.logic.ProductExtractionGridService;
import cn.ae2bc.logic.ExtractionSource;
import cn.ae2bc.logic.ExtractionRecoveryQueue;
import cn.ae2bc.logic.ProductExtractionTickState;
import cn.ae2bc.logic.ProductExtractor;
import cn.ae2bc.logic.ProductExtractionUpgradeInventory;
import cn.ae2bc.registry.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;

@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin implements PatternProviderExtractionExtension {
    private static final String AE2BC_UPGRADES = "Ae2bcProductExtractionUpgrades";
    private static final String AE2BC_MARKERS = "Ae2bcProductExtractionMarkers";
    private static final String AE2BC_INTERVAL = "Ae2bcProductExtractionInterval";
    private static final String AE2BC_AMOUNT = "Ae2bcProductExtractionAmount";
    private static final String AE2BC_WHITELIST = "Ae2bcProductExtractionWhitelist";
    private static final String AE2BC_RECOVERY = "Ae2bcProductExtractionRecovery";

    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private IActionSource actionSource;
    @Shadow @Final private PatternProviderReturnInventory returnInv;

    @Unique private IUpgradeInventory ae2bc$productExtractionUpgrades;
    @Unique private GenericStackInv ae2bc$productExtractionMarkers;
    @Unique private final EnumMap<Direction, BlockCapabilityCache<MEStorage, Direction>> ae2bc$storageTargets =
            new EnumMap<>(Direction.class);
    @Unique private final EnumMap<Direction, Map<AEKeyType, ExternalStorageStrategy>> ae2bc$externalStrategies =
            new EnumMap<>(Direction.class);
    @Unique private int ae2bc$productExtractionInterval = ProductExtractionSettings.DEFAULT_INTERVAL;
    @Unique private int ae2bc$productExtractionAmount = ProductExtractionSettings.DEFAULT_AMOUNT;
    @Unique private boolean ae2bc$productExtractionWhitelist;
    @Unique private ProductExtractionSettings ae2bc$cachedProductExtractionSettings;
    @Unique private int ae2bc$directionCursor;
    @Unique private ExtractionRecoveryQueue ae2bc$extractionRecovery;
    @Unique private boolean ae2bc$productExtractionBypass;
    @Unique private boolean ae2bc$flushingReturnInventory;

    @Inject(
            method = "<init>(Lappeng/api/networking/IManagedGridNode;"
                    + "Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("RETURN"))
    private void ae2bc$initializeProductExtraction(CallbackInfo ci) {
        var providerItem = host.getTerminalIcon().getItem();
        ae2bc$productExtractionUpgrades = new ProductExtractionUpgradeInventory(providerItem,
                ModContent.PRODUCT_EXTRACTION_CARD.get(), this::ae2bc$onProductExtractionChanged);
        ae2bc$productExtractionMarkers = new GenericStackInv(Set.copyOf(AEKeyTypes.getAll()),
                this::ae2bc$onProductExtractionChanged, GenericStackInv.Mode.CONFIG_TYPES,
                ProductExtractionSettings.MARKER_SLOT_COUNT);
        ae2bc$extractionRecovery = new ExtractionRecoveryQueue(host::saveChanges);
        // Direct machine output and Pattern P2P returns both converge on this inventory.
        ((GenericStackInvAccessor) returnInv).ae2bc$setFilter((slot, what) -> ae2bc$allowsReturnedProduct(what));
        ((ImmediatePatternProviderReturnInventory) returnInv)
                .ae2bc$setImmediateFlushHandler(this::ae2bc$flushReturnInventoryImmediately);
        ((ImmediatePatternProviderReturnInventory) returnInv)
                .ae2bc$setReturnTickerWakeHandler(this::ae2bc$wakePatternProviderTicker);
    }

    @Unique
    private void ae2bc$flushReturnInventoryImmediately() {
        if (ae2bc$flushingReturnInventory) {
            return;
        }

        var grid = mainNode.getGrid();
        if (mainNode.isActive() && grid != null) {
            ae2bc$flushingReturnInventory = true;
            try {
                var accessor = (PatternProviderLogicAccessor) (Object) this;
                returnInv.injectIntoNetwork(grid.getStorageService().getInventory(), actionSource,
                        accessor::ae2bc$onStackReturnedToNetwork);
            } finally {
                ae2bc$flushingReturnInventory = false;
            }
        }
    }

    @Override
    public IUpgradeInventory ae2bc$getProductExtractionUpgrades() {
        return ae2bc$productExtractionUpgrades;
    }

    @Override
    public GenericStackInv ae2bc$getProductExtractionMarkers() {
        return ae2bc$productExtractionMarkers;
    }

    @Override
    public ProductExtractionSettings ae2bc$getProductExtractionSettings() {
        if (ae2bc$cachedProductExtractionSettings != null) {
            return ae2bc$cachedProductExtractionSettings;
        }
        Set<AEKey> markers = new HashSet<>();
        for (int slot = 0; slot < ae2bc$productExtractionMarkers.size(); slot++) {
            if (ae2bc$productExtractionMarkers.getKey(slot) instanceof AEKey key) {
                markers.add(key);
            }
        }
        ae2bc$cachedProductExtractionSettings = new ProductExtractionSettings(
                ae2bc$hasProductExtractionCard(), ae2bc$productExtractionInterval,
                ae2bc$productExtractionAmount, ae2bc$productExtractionWhitelist, markers);
        return ae2bc$cachedProductExtractionSettings;
    }

    @Override
    public void ae2bc$setProductExtractionInterval(int interval) {
        int clamped = ProductExtractionSettings.clampInterval(interval);
        if (ae2bc$productExtractionInterval != clamped) {
            ae2bc$productExtractionInterval = clamped;
            ae2bc$onProductExtractionChanged();
        }
    }

    @Override
    public void ae2bc$setProductExtractionAmount(int amount) {
        int clamped = ProductExtractionSettings.clampAmount(amount);
        if (ae2bc$productExtractionAmount != clamped) {
            ae2bc$productExtractionAmount = clamped;
            ae2bc$onProductExtractionChanged();
        }
    }

    @Override
    public void ae2bc$setProductExtractionWhitelist(boolean whitelist) {
        if (ae2bc$productExtractionWhitelist != whitelist) {
            ae2bc$productExtractionWhitelist = whitelist;
            ae2bc$onProductExtractionChanged();
        }
    }

    @Override
    public boolean ae2bc$hasProductExtractionCard() {
        return ae2bc$productExtractionUpgrades.isInstalled(ModContent.PRODUCT_EXTRACTION_CARD.get());
    }

    @Override
    public boolean ae2bc$hasProductExtractionWork() {
        return ae2bc$hasProductExtractionCard() || !ae2bc$extractionRecovery.isEmpty();
    }

    @Unique
    private boolean ae2bc$allowsReturnedProduct(AEKey what) {
        if (ae2bc$productExtractionBypass || !ae2bc$hasProductExtractionCard()) {
            return true;
        }
        return ae2bc$getProductExtractionSettings().allows(what);
    }

    @Override
    @Unique
    public ProductExtractionTickState ae2bc$tickProductExtraction() {
        if (!mainNode.isActive() || !(host.getBlockEntity().getLevel() instanceof ServerLevel level)) {
            return ProductExtractionTickState.DISABLED;
        }
        boolean recoveryProgress = ae2bc$drainRecovery();
        if (!ae2bc$hasProductExtractionCard()) {
            if (recoveryProgress) {
                return ProductExtractionTickState.PROGRESSED;
            }
            return ae2bc$extractionRecovery.isEmpty()
                    ? ProductExtractionTickState.DISABLED : ProductExtractionTickState.NO_PROGRESS;
        }

        List<Direction> directions = ((PatternProviderLogicAccessor) (Object) this)
                .ae2bc$getActiveSides().stream().sorted().toList();
        if (directions.isEmpty()) {
            return recoveryProgress
                    ? ProductExtractionTickState.PROGRESSED : ProductExtractionTickState.NO_PROGRESS;
        }
        int moved = 0;
        int start = Math.floorMod(ae2bc$directionCursor, directions.size());
        ProductExtractionSettings base = ae2bc$getProductExtractionSettings();
        for (int offset = 0; offset < directions.size() && moved < base.amount(); offset++) {
            Direction direction = directions.get((start + offset) % directions.size());
            var target = ae2bc$getTarget(level, direction);
            if (target.isEmpty()) {
                continue;
            }
            int remaining = base.amount() - moved;
            ae2bc$productExtractionBypass = true;
            try {
                moved += ProductExtractor.extract(ExtractionSource.fromTypeMap(target), returnInv,
                        new ProductExtractionSettings(true, base.interval(), remaining,
                                base.whitelist(), base.markers()),
                        actionSource, ae2bc$extractionRecovery::queue);
            } finally {
                ae2bc$productExtractionBypass = false;
            }
        }
        ae2bc$directionCursor = (start + 1) % directions.size();
        return moved > 0 || recoveryProgress
                ? ProductExtractionTickState.PROGRESSED : ProductExtractionTickState.NO_PROGRESS;
    }

    @Unique
    private Map<AEKeyType, MEStorage> ae2bc$getTarget(ServerLevel level, Direction direction) {
        var pos = host.getBlockEntity().getBlockPos().relative(direction);
        Direction face = direction.getOpposite();
        var cache = ae2bc$storageTargets.get(direction);
        if (cache == null || cache.level() != level || !cache.pos().equals(pos) || cache.context() != face) {
            cache = BlockCapabilityCache.create(AECapabilities.ME_STORAGE, level, pos, face);
            ae2bc$storageTargets.put(direction, cache);
            ae2bc$externalStrategies.put(direction,
                    StackWorldBehaviors.createExternalStorageStrategies(level, pos, face));
        }
        MEStorage direct = cache.getCapability();
        if (direct != null) {
            Map<AEKeyType, MEStorage> result = new IdentityHashMap<>();
            for (var type : AEKeyTypes.getAll()) {
                result.put(type, direct);
            }
            return result;
        }
        Map<AEKeyType, MEStorage> result = new IdentityHashMap<>();
        for (var entry : ae2bc$externalStrategies.getOrDefault(direction, Map.of()).entrySet()) {
            var wrapper = entry.getValue().createWrapper(false, this::ae2bc$alertProductExtraction);
            if (wrapper != null) {
                result.put(entry.getKey(), wrapper);
            }
        }
        return result;
    }

    @Unique
    private boolean ae2bc$drainRecovery() {
        ae2bc$productExtractionBypass = true;
        try {
            return ae2bc$extractionRecovery.drain((what, amount) ->
                    returnInv.insert(what, amount, Actionable.MODULATE, actionSource));
        } finally {
            ae2bc$productExtractionBypass = false;
        }
    }

    @Unique
    private void ae2bc$onProductExtractionChanged() {
        ae2bc$cachedProductExtractionSettings = null;
        host.saveChanges();
        ae2bc$updateProductExtractionRegistration();
        ae2bc$alertProductExtraction();
    }

    @Unique
    private void ae2bc$alertProductExtraction() {
        mainNode.ifPresent((grid, node) ->
                grid.getService(ProductExtractionGridService.class).wake(node, this));
    }

    @Unique
    private void ae2bc$wakePatternProviderTicker() {
        mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    @Unique
    private void ae2bc$updateProductExtractionRegistration() {
        mainNode.ifPresent((grid, node) ->
                grid.getService(ProductExtractionGridService.class).update(node, this));
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void ae2bc$updateExtractionRegistrationForNodeState(CallbackInfo ci) {
        ae2bc$updateProductExtractionRegistration();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2bc$readProductExtraction(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        ae2bc$productExtractionUpgrades.readFromNBT(data, AE2BC_UPGRADES, registries);
        ae2bc$productExtractionMarkers.readFromChildTag(data, AE2BC_MARKERS, registries);
        ae2bc$productExtractionInterval = data.contains(AE2BC_INTERVAL, Tag.TAG_INT)
                ? ProductExtractionSettings.clampInterval(data.getInt(AE2BC_INTERVAL))
                : ProductExtractionSettings.DEFAULT_INTERVAL;
        ae2bc$productExtractionAmount = data.contains(AE2BC_AMOUNT, Tag.TAG_INT)
                ? ProductExtractionSettings.clampAmount(data.getInt(AE2BC_AMOUNT))
                : ProductExtractionSettings.DEFAULT_AMOUNT;
        ae2bc$productExtractionWhitelist = data.getBoolean(AE2BC_WHITELIST);
        ae2bc$cachedProductExtractionSettings = null;
        ae2bc$extractionRecovery.read(data, AE2BC_RECOVERY, registries);
        ae2bc$updateProductExtractionRegistration();
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2bc$writeProductExtraction(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        ae2bc$productExtractionUpgrades.writeToNBT(data, AE2BC_UPGRADES, registries);
        ae2bc$productExtractionMarkers.writeToChildTag(data, AE2BC_MARKERS, registries);
        data.putInt(AE2BC_INTERVAL, ae2bc$productExtractionInterval);
        data.putInt(AE2BC_AMOUNT, ae2bc$productExtractionAmount);
        data.putBoolean(AE2BC_WHITELIST, ae2bc$productExtractionWhitelist);
        ae2bc$extractionRecovery.write(data, AE2BC_RECOVERY, registries);
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void ae2bc$dropProductExtractionUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        for (ItemStack stack : ae2bc$productExtractionUpgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        ae2bc$extractionRecovery.addDrops(drops, host.getBlockEntity().getLevel(),
                host.getBlockEntity().getBlockPos());
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void ae2bc$clearProductExtraction(CallbackInfo ci) {
        ae2bc$productExtractionUpgrades.clear();
        ae2bc$productExtractionMarkers.clear();
        ae2bc$extractionRecovery.clear();
        ae2bc$updateProductExtractionRegistration();
    }
}

package org.ae2bc.client.screen;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.client.screen.component.RightToolbarPanel;
import org.ae2bc.client.screen.component.ScaledIconButton;
import org.ae2bc.client.screen.component.ScrollingAE2Button;
import org.ae2bc.client.screen.component.TaskResetConfirmation;
import org.ae2bc.client.screen.component.TextButton;
import org.ae2bc.client.screen.page.Pp2mpPage;
import org.ae2bc.client.screen.page.Pp2mpPageContext;
import org.ae2bc.core.dispatch.TaskAllocationMode;
import org.ae2bc.core.dispatch.TaskSplitMode;
import org.ae2bc.core.part.GeneralPageConfig;
import org.ae2bc.core.part.InputConfig;
import org.ae2bc.core.part.OutputConfig;
import org.ae2bc.core.part.UnitConfig;
import org.ae2bc.core.screen.Route;
import org.ae2bc.core.screen.RouteGroup;
import org.ae2bc.part.input.Pp2mpInputMenu;
import org.ae2bc.part.output.Pp2mpOutputMenu;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerMenu;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Pp2mpPagedScreen<T extends AEBaseMenu> extends AEBaseScreen<T> {
    // 左侧三个分页按钮加指南按钮，含 AE2 工具栏间距和边框共需 94px。
    private static final int MIN_PAGE_HEIGHT = 94;
    private static final ResourceLocation OUTPUT_PAGE_ICON = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "textures/gui/btn/output_btn.png");
    private static final ResourceLocation RESOURCE_INTAKE_PAGE_ICON = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "textures/gui/btn/resource_intake_btn.png");
    private static final ResourceLocation UNIT_PAGE_ICON = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "textures/gui/btn/unit_manager_btn.png");
    private static final ResourceLocation TASK_ALLOCATION_PRIORITY_ICON = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "textures/gui/btn/priority.png");
    private static final Map<Object, Route> RETURN_ROUTES = new java.util.WeakHashMap<>();
    private static final Map<Class<?>, Route> LAST_ROUTES = new HashMap<>();
    private final int maxPageHeight;
    private final Map<Route, Pp2mpPage> pages = new LinkedHashMap<>();
    private final Map<RouteGroup, ScaledIconButton> pageGroupButtons = new LinkedHashMap<>();
    private final Map<Route, TextButton> unitSubPageButtons = new LinkedHashMap<>();
    private final Map<String, AbstractWidget> pageWidgets = new LinkedHashMap<>();
    private final Map<String, IconButton> rightToolbarButtons = new LinkedHashMap<>();
    private final RightToolbarPanel rightToolbar = new RightToolbarPanel();
    private final Pp2mpPageContext pageContext;
    private ScaledIconButton taskAllocationButton;
    private ScaledIconButton taskSplitButton;
    private ScaledIconButton blockingButton;
    private Supplier<GeneralPageConfig> blockingConfig;
    private BooleanSupplier blockingEditable;
    private Route currentRoute = Route.GENERAL;
    private boolean pagesInitialized;
    public Pp2mpPagedScreen(T menu, Inventory inventory, Component title, ScreenStyle style, Pp2mpPage... pages) {
        super(menu, inventory, title, style);
        ScreenHeader.addTo(this.widgets);
        this.setTextContent(TEXT_ID_DIALOG_TITLE, title);
        this.pageContext = new Pp2mpPageContext(this);
        this.maxPageHeight = Math.max(MIN_PAGE_HEIGHT,
                Arrays.stream(pages).mapToInt(Pp2mpPage::height).max().orElse(166));

        // 注册页面、分页按钮
        for (Pp2mpPage page : pages) {
            this.registerPage(page);
        }
        this.currentRoute = Pp2mpPagedScreen.restoreLastRoute(menu.getClass(), this.pages);
        if (menu.isReturnedFromSubScreen()) {
            Route route = RETURN_ROUTES.remove(menu.getTarget());
            if (route != null && this.pages.containsKey(route)) {
                this.currentRoute = route;
            }
        }
        this.registerPageButtons(pages);

        // 注册右面板功能栏
        this.registerRightToolbar();
    }

    public static void rememberReturnRoute(Object target, Route route) {
        if (route != null) {
            RETURN_ROUTES.put(target, route);
        }
    }

    /* 按菜单类型记录本次客户端会话最后停留的分页。 */
    static void rememberLastRoute(Class<?> menuType, Route route) {
        if (route != null) {
            LAST_ROUTES.put(menuType, route);
        }
    }

    /* 仅恢复当前界面实际拥有的分页，不存在时回到通用分页。 */
    static Route restoreLastRoute(Class<?> menuType, Map<Route, Pp2mpPage> pages) {
        Route route = LAST_ROUTES.get(menuType);
        return route != null && pages.containsKey(route) ? route : Route.GENERAL;
    }

    @Override
    protected void init() {
        if (!this.pagesInitialized) {
            pages.values().forEach(page -> page.init(pageContext));
            this.pagesInitialized = true;
        }

        // 固定顶部位置，父类 init() 会根据界面高度计算顶部位置。
        super.imageHeight = this.maxPageHeight;
        super.init();
        Pp2mpPage page = this.pages.get(this.currentRoute);
        if (page != null) {
            super.imageHeight = Math.max(MIN_PAGE_HEIGHT, page.height());
        }

        pages.values().forEach(Pp2mpPage::layout);
        this.updateRouteVisibility();
        this.updatePageSelection();
        this.updateTaskAllocationButton();
        this.updateTaskSplitButton();
        this.updateBlockingButton();
        this.layoutUnitSubPageButtons();
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        Pp2mpPage page = pages.get(currentRoute);
        if (page != null) {
            page.update();
        }
        this.updatePageSelection();
        this.updateTaskAllocationButton();
        this.updateTaskSplitButton();
        this.updateBlockingButton();
    }

    /* 切换路由 */
    protected final void switchRoute(Route route) {
        if (route == null || !this.pages.containsKey(route)) {
            return;
        }

        this.currentRoute = route;
        Pp2mpPagedScreen.rememberLastRoute(this.menu.getClass(), this.currentRoute);
        this.updateRouteVisibility();
        this.updatePageSelection();
        this.repositionElements();
    }

    /* 根据当前路由同步左侧分组和顶部子分页的选中状态。 */
    private void updatePageSelection() {
        this.pageGroupButtons.forEach((group, button) -> button.setSelected(group == this.currentRoute.group));
        this.unitSubPageButtons.forEach((route, button) -> {
            button.active = true;
            button.setSelected(route.equals(this.currentRoute));
        });
    }

    protected final void registerPage(Pp2mpPage page) {
        this.pages.put(page.getRoute(), page);
    }

    /* 注册分页按钮 */
    private void registerPageButtons(Pp2mpPage[] pages) {
        ScaledIconButton generalBtn = new ScaledIconButton(
                Icon.COG, 1.0f, ignored -> this.switchRoute(Route.GENERAL)
        );
        generalBtn.setMessage(Component.translatable("gui.ae2_batchcraft.page.general"));
        this.pageGroupButtons.put(RouteGroup.GENERAL, generalBtn);
        super.addToLeftToolbar(generalBtn);

        // 输出端分页分组
        if (Arrays.stream(pages).anyMatch(page -> RouteGroup.OUTPUT.equals(page.getRoute().group))) {
            ScaledIconButton outputBtn = new ScaledIconButton(
                    OUTPUT_PAGE_ICON, 0.8f, ignored -> this.switchRoute(Route.OUTPUT)
            );
            outputBtn.setMessage(Component.translatable("gui.ae2_batchcraft.page.output"));
            this.pageGroupButtons.put(RouteGroup.OUTPUT, outputBtn);
            super.addToLeftToolbar(outputBtn);
        }

        // 资源接收端分页分组
        if (Arrays.stream(pages).anyMatch(page -> RouteGroup.RESOURCE_INTAKE.equals(page.getRoute().group))) {
            ScaledIconButton resourceIntakeBtn = new ScaledIconButton(
                    RESOURCE_INTAKE_PAGE_ICON, 0.8f, ignored -> this.switchRoute(Route.RESOURCE_INTAKE)
            );
            resourceIntakeBtn.setMessage(Component.translatable("gui.ae2_batchcraft.page.resource_intake"));
            this.pageGroupButtons.put(RouteGroup.RESOURCE_INTAKE, resourceIntakeBtn);
            super.addToLeftToolbar(resourceIntakeBtn);
        }

        // 单元分页分组
        if (Arrays.stream(pages).anyMatch(page -> RouteGroup.UNIT.equals(page.getRoute().group))) {
            ScaledIconButton unitBtn = new ScaledIconButton(
                    UNIT_PAGE_ICON, 0.8f, ignored -> this.switchRoute(Route.UNIT)
            );
            unitBtn.setMessage(Component.translatable("gui.ae2_batchcraft.page.unit"));
            this.pageGroupButtons.put(RouteGroup.UNIT, unitBtn);
            super.addToLeftToolbar(unitBtn);

            this.registerUnitSubPageButton(Route.UNIT, "unit_subpage_general", "gui.ae2_batchcraft.page.sub_page.unit");
            this.registerUnitSubPageButton(Route.UNIT_BREAK, "unit_subpage_break", "gui.ae2_batchcraft.page.sub_page.unit_break");
            this.registerUnitSubPageButton(Route.UNIT_REDSTONE, "unit_subpage_redstone", "gui.ae2_batchcraft.page.sub_page.unit_redstone");
        }
    }

    private void registerUnitSubPageButton(Route route, String id, String translationKey) {
        TextButton button = this.addPageWidget(id, new TextButton(
                Component.translatable(translationKey),
                ignore -> this.switchRoute(route)
        ), TextButton.class);
        this.unitSubPageButtons.put(route, button);
    }

    /* 注册右侧功能栏 */
    private void registerRightToolbar() {
        super.widgets.add("rightToolbar", rightToolbar);
        switch (this.menu) {
            case Pp2mpInputMenu inputMenu -> {
                this.addResetTaskButton("gui.ae2_batchcraft.reset_task.tooltip.input",
                        "gui.ae2_batchcraft.reset_task.confirm.input", inputMenu::resetTaskState);
                this.taskAllocationButton = this.addToRightToolbar(new ScaledIconButton(
                        Icon.SCHEDULING_ROUND_ROBIN,
                        0.8f,
                        ignored -> inputMenu.cycleTaskAllocationMode()
                ));
                this.taskAllocationButton.setMessage(Component.translatable(
                        "gui.ae2_batchcraft.task_allocation_mode.round_robin"
                ));
                this.taskSplitButton = this.addToRightToolbar(new ScaledIconButton(
                        Icon.PRIORITY,
                        0.8f,
                        ignored -> inputMenu.cycleTaskSplitMode()
                ));
                this.addBlockingButton(() -> inputMenu.getConfig().general(), () -> true,
                        enabled -> inputMenu.configChange(InputConfig.builder(inputMenu.getConfig())
                                .blocking(enabled).build()));
            }
            case Pp2mpOutputMenu outputMenu -> {
                this.addResetTaskButton("gui.ae2_batchcraft.reset_task.tooltip.output",
                        "gui.ae2_batchcraft.reset_task.confirm.output", outputMenu::resetTaskState);
                this.addBlockingButton(() -> outputMenu.getConfig().general(), () -> !outputMenu.isSyncSwitch(),
                        enabled -> {
                            OutputConfig config = outputMenu.getConfig();
                            outputMenu.configChange(new OutputConfig(
                                    config.general().withBlocking(enabled), config.output()));
                        });
            }
            case Pp2mpUnitManagerMenu managerMenu -> {
                this.addResetTaskButton("gui.ae2_batchcraft.reset_task.tooltip.unit",
                        "gui.ae2_batchcraft.reset_task.confirm.unit", managerMenu::resetTaskState);
                this.addBlockingButton(() -> managerMenu.getConfig().general(), () -> !managerMenu.isSyncSwitch(),
                        enabled -> {
                            UnitConfig config = managerMenu.getConfig();
                            managerMenu.configChange(new UnitConfig(
                                    config.general().withBlocking(enabled), config.unit()));
                        });
            }
            default -> {
            }
        }
    }

    private void addBlockingButton(Supplier<GeneralPageConfig> config, BooleanSupplier editable,
                                   Consumer<Boolean> change) {
        this.blockingConfig = config;
        this.blockingEditable = editable;
        this.blockingButton = this.addToRightToolbar(new ScaledIconButton(
                Icon.BLOCKING_MODE_YES, 0.8f, ignored -> change.accept(!config.get().blocking())));
    }

    private void updateBlockingButton() {
        if (this.blockingButton == null) {
            return;
        }
        boolean enabled = this.blockingConfig.get().blocking();
        this.blockingButton.setAe2Icon(enabled ? Icon.BLOCKING_MODE_YES : Icon.BLOCKING_MODE_NO);
        this.blockingButton.active = this.blockingEditable.getAsBoolean();
        this.blockingButton.setMessage(Component.translatable(
                "gui.ae2_batchcraft.blocking_mode." + (enabled ? "enabled" : "disabled")));
    }

    private void updateTaskAllocationButton() {
        if (this.taskAllocationButton == null || !(this.menu instanceof Pp2mpInputMenu inputMenu)) {
            return;
        }
        TaskAllocationMode mode = inputMenu.getTaskAllocationMode();
        switch (mode) {
            case ROUND_ROBIN -> this.taskAllocationButton.setAe2Icon(Icon.SCHEDULING_ROUND_ROBIN);
            case RANDOM -> this.taskAllocationButton.setAe2Icon(Icon.SCHEDULING_RANDOM);
            case PRIORITY -> this.taskAllocationButton.setCustomIcon(TASK_ALLOCATION_PRIORITY_ICON);
        }
        this.taskAllocationButton.setMessage(Component.translatable(
                "gui.ae2_batchcraft.task_allocation_mode." + mode.name
        ));
    }

    private void updateTaskSplitButton() {
        if (this.taskSplitButton == null || !(this.menu instanceof Pp2mpInputMenu inputMenu)) {
            return;
        }
        TaskSplitMode mode = inputMenu.getTaskSplitMode();
        this.taskSplitButton.setMessage(Component.translatable(
                "gui.ae2_batchcraft.task_split_mode." + mode.name
        ));
    }

    private void addResetTaskButton(String tooltipKey, String warningKey, Runnable resetAction) {
        ScaledIconButton button = new ScaledIconButton(Icon.SCHEDULING_DEFAULT, 0.9f, ignored ->
                TaskResetConfirmation.open(this, Component.translatable(warningKey), resetAction));
        button.setMessage(Component.translatable(tooltipKey));
        this.addToRightToolbar(button);
    }

    /* 更新路由可见 */
    private void updateRouteVisibility() {
        this.pages.forEach((route, page) -> page.setVisible(route == this.currentRoute));
        this.setTextContent("page_title",
                Component.translatable("gui.ae2_batchcraft.page." + this.currentRoute.id()));
        this.setTextHidden("page_title", this.currentRoute.group == RouteGroup.UNIT);

        boolean unitPageVisible = currentRoute.group == RouteGroup.UNIT;
        unitSubPageButtons.values().forEach(button -> button.visible = unitPageVisible);

//        rightToolbarButtons.values().forEach(button -> button.visible = true);
    }

    /* 按实际子分页数量分配整行宽度，并保留两侧边距。 */
    private void layoutUnitSubPageButtons() {
        int count = this.unitSubPageButtons.size();
        if (count == 0) {
            return;
        }
        int x = this.leftPos + 6;
        int y = this.topPos + 20;
        int height = 14;
        int spacing = 1;
        int availableWidth = this.imageWidth - 12 - (count - 1) * spacing;
        int width = availableWidth / count;
        int remainder = availableWidth % count;

        for (TextButton button : this.unitSubPageButtons.values()) {
            int buttonWidth = width;
            if (remainder > 0) {
                buttonWidth++;
                remainder--;
            }
            button.setX(x);
            button.setY(y);
            button.setWidth(buttonWidth);
            button.setHeight(height);
            x += buttonWidth + spacing;
        }
    }

    private void currentPageCommit() {
        Pp2mpPage page = pages.get(currentRoute);
        if (page != null) {
            page.commit();
        }
    }

    @Override
    public void onClose() {
        this.currentPageCommit();
        Pp2mpPagedScreen.rememberLastRoute(this.menu.getClass(), this.currentRoute);
        super.onClose();
    }

    @Override
    public void removed() {
        this.currentPageCommit();
        Pp2mpPagedScreen.rememberLastRoute(this.menu.getClass(), this.currentRoute);
        super.removed();
    }

    public final <B extends IconButton> B addToRightToolbar(B button) {
        rightToolbarButtons.put("rightToolbar_" + rightToolbarButtons.size(), button);
        rightToolbar.addButton(button);
        return button;
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        BackgroundGenerator.draw(this.imageWidth, this.imageHeight, graphics, offsetX, offsetY);
        Pp2mpPage page = this.pages.get(this.currentRoute);
        if (page != null) {
            page.drawBackground(graphics, this.font, offsetX, offsetY);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        Pp2mpPage page = this.pages.get(this.currentRoute);
        if (page != null) {
            page.drawForeground(graphics, this.font, 0, 0);
        }
    }

    public final int screenImageWidth() {
        return this.imageWidth;
    }

    public final ScreenStyle getScreenStyle() {
        return super.getStyle();
    }

    public final int getScreenLeftPos() {
        return this.leftPos;
    }

    public final int getScreenTopPos() {
        return this.topPos;
    }

    public final AE2Button addButton(String id, Component message, Runnable onPress) {
        AbstractWidget existing = this.pageWidgets.get(id);
        if (existing instanceof AE2Button button) {
            return button;
        }
        AE2Button button = new ScrollingAE2Button(message, ignored -> onPress.run());
        this.widgets.add(id, button);
        this.pageWidgets.put(id, button);
        return button;
    }

    public final <W extends AbstractWidget> W addPageWidget(String id, W widget, Class<W> clazz) {
        AbstractWidget existing = this.pageWidgets.get(id);
        if (existing != null) {
            if (!widget.getClass().isInstance(existing)) {
                throw new IllegalArgumentException("Widget id is already registered with another type: " + id);
            }
            return clazz.cast(existing);
        }
        this.widgets.add(id, widget);
        this.pageWidgets.put(id, widget);
        return widget;
    }
}

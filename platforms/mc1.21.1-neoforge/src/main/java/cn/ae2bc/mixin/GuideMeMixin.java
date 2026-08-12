package cn.ae2bc.mixin;

import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.guide.GuideLocalization;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Pseudo
@Mixin(targets = "guideme.internal.MutableGuide", remap = false)
public abstract class GuideMeMixin {
    @Inject(method = "getParsedPage", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2bc$selectLocalizedGuideMePage(ResourceLocation pageId,
                                                    CallbackInfoReturnable<ParsedGuidePage> cir) {
        if (!pageId.getNamespace().equals(Ae2bcMod.MOD_ID)) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var language = minecraft.getLanguageManager().getSelected();
        if (!GuideLocalization.isChinese(language)) {
            return;
        }

        var normalizedLanguage = language.replace('-', '_').toLowerCase(Locale.ROOT);
        if ("zh_cn".equals(normalizedLanguage)) {
            return;
        }

        var resourceManager = minecraft.getResourceManager();
        var localizedResource = ResourceLocation.fromNamespaceAndPath(
                Ae2bcMod.MOD_ID, GuideLocalization.localizedPath(language, pageId.getPath()));
        var resource = resourceManager.getResource(localizedResource).orElse(null);
        if (resource == null) {
            localizedResource = ResourceLocation.fromNamespaceAndPath(
                    Ae2bcMod.MOD_ID, GuideLocalization.localizedPath("zh_cn", pageId.getPath()));
            resource = resourceManager.getResource(localizedResource).orElse(null);
        }
        if (resource == null) {
            Ae2bcMod.LOGGER.warn("Missing GuideMe localized guide resource for language {}", language);
            return;
        }

        try (var input = resource.open()) {
            Ae2bcMod.LOGGER.info("Using localized GuideMe page {} for language {} from {}",
                    pageId, language, localizedResource);
            cir.setReturnValue(PageCompiler.parse(
                    resource.sourcePackId(), normalizedLanguage, pageId, input));
        } catch (Exception e) {
            Ae2bcMod.LOGGER.warn("Failed to load GuideMe localized guide resource {}", localizedResource, e);
        }
    }
}

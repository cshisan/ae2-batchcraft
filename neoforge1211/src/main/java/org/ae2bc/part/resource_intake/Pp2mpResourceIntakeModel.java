package org.ae2bc.part.resource_intake;

import appeng.api.parts.PartModels;
import net.minecraft.resources.ResourceLocation;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.part.Pp2mpPartModels;

import java.util.List;

public final class Pp2mpResourceIntakeModel {
    public static final Pp2mpPartModels MODELS = new Pp2mpPartModels(
            ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "part/pp2mp_resource_intake")
    );

    private Pp2mpResourceIntakeModel() {
    }

    public static void register() {
        List<ResourceLocation> models = MODELS.getModels().stream()
                .flatMap(model -> model.getModels().stream())
                .toList();
        PartModels.registerModels(models);
    }
}

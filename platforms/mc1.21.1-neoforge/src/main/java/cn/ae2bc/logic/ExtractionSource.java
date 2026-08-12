package cn.ae2bc.logic;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One extraction storage and the key types through which it was resolved. */
public record ExtractionSource(MEStorage storage, Set<AEKeyType> supportedTypes) {
    public ExtractionSource {
        Objects.requireNonNull(storage, "storage");
        supportedTypes = Set.copyOf(Objects.requireNonNull(supportedTypes, "supportedTypes"));
    }

    public static List<ExtractionSource> fromTypeMap(Map<AEKeyType, MEStorage> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        Map<MEStorage, Set<AEKeyType>> typesByStorage = IdentityGrouping.invert(sources);
        List<ExtractionSource> result = new ArrayList<>(typesByStorage.size());
        typesByStorage.forEach((storage, types) -> result.add(new ExtractionSource(storage, types)));
        return result;
    }
}

package org.ae2bc.core.placer;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Accessors(fluent = true)
public class Pos {
    private final int x;
    private final int y;
    private final int z;
}


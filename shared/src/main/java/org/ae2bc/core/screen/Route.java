package org.ae2bc.core.screen;

public enum Route {
    GENERAL(RouteGroup.GENERAL),
    OUTPUT(RouteGroup.OUTPUT),
    RESOURCE_INTAKE(RouteGroup.RESOURCE_INTAKE),
    UNIT(RouteGroup.UNIT),
    UNIT_REDSTONE(RouteGroup.UNIT),
    UNIT_BREAK(RouteGroup.UNIT),
    ;

    public final RouteGroup group;

    Route(RouteGroup group) {
        this.group = group;
    }

    public String id() {
        return this.name().toLowerCase();
    }
}

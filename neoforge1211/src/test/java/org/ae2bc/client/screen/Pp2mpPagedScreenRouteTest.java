package org.ae2bc.client.screen;

import org.ae2bc.core.screen.Route;
import org.ae2bc.client.screen.page.Pp2mpPage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumMap;
import java.util.Map;

class Pp2mpPagedScreenRouteTest {
    @Test
    void restoresLastRouteForTheSameMenuType() {
        Map<Route, Pp2mpPage> pages = Pp2mpPagedScreenRouteTest.pages(Route.GENERAL, Route.OUTPUT);
        Pp2mpPagedScreen.rememberLastRoute(InputMenuType.class, Route.OUTPUT);

        Assertions.assertEquals(
                Route.OUTPUT,
                Pp2mpPagedScreen.restoreLastRoute(InputMenuType.class, pages)
        );
    }

    @Test
    void fallsBackToGeneralWhenLastRouteIsUnavailable() {
        Map<Route, Pp2mpPage> pages = Pp2mpPagedScreenRouteTest.pages(Route.GENERAL);
        Pp2mpPagedScreen.rememberLastRoute(OutputMenuType.class, Route.OUTPUT);

        Assertions.assertEquals(
                Route.GENERAL,
                Pp2mpPagedScreen.restoreLastRoute(OutputMenuType.class, pages)
        );
    }

    private static Map<Route, Pp2mpPage> pages(Route... routes) {
        Map<Route, Pp2mpPage> pages = new EnumMap<>(Route.class);
        for (Route route : routes) {
            pages.put(route, Mockito.mock(Pp2mpPage.class));
        }
        return pages;
    }

    private static final class InputMenuType {
    }

    private static final class OutputMenuType {
    }
}

package com.npcwars.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RouteTest {

    @Test
    void namesAreCaseInsensitiveAndUnique() {
        RouteManager manager = new RouteManager();
        Route route = manager.create("Arena-1", "world");
        assertNotNull(route);
        assertNull(manager.create("arena-1", "world"));
        assertSame(route, manager.get("ARENA-1"));
        assertTrue(manager.delete("arena-1"));
        assertFalse(manager.delete("arena-1"));
        assertNull(manager.get("arena-1"));
    }

    @Test
    void invalidNamesAreRejected() {
        RouteManager manager = new RouteManager();
        assertNull(manager.create("has space", "world"));
        assertNull(manager.create("", "world"));
        assertNull(manager.create("x".repeat(25), "world"));
        assertNull(manager.create("bad.name", "world"));
    }

    @Test
    void waypointsKeepOrderAndRemoveByOneBasedIndex() {
        Route route = new Route("r", "world");
        route.add(new Route.Point(0, 64, 0));
        route.add(new Route.Point(10, 64, 0));
        route.add(new Route.Point(10, 64, 10));
        assertEquals(3, route.size());
        assertEquals(20.0, route.length(), 1e-9);
        assertFalse(route.remove(0));
        assertFalse(route.remove(4));
        assertTrue(route.remove(2));
        assertEquals(10.0, route.points().get(1).x());
        assertEquals(2, route.size());
    }

    @Test
    void changesNotifyTheListenerSoDataIsSaved() {
        RouteManager manager = new RouteManager();
        int[] saves = {0};
        manager.setChangeListener(() -> saves[0]++);
        manager.create("a", "world");
        manager.changed();
        manager.delete("a");
        assertEquals(3, saves[0]);
    }

    @Test
    void routesSurviveASaveAndLoad() {
        RouteManager original = new RouteManager();
        Route route = original.create("loop", "world_nether");
        route.add(new Route.Point(1.5, 70, -3.25));
        route.add(new Route.Point(100, 12.5, 200));
        original.create("empty", "world");

        YamlConfiguration yaml = new YamlConfiguration();
        RouteStorage.save(original, yaml);
        // through text, as data.yml really is
        YamlConfiguration reread = new YamlConfiguration();
        try {
            reread.loadFromString(yaml.saveToString());
        } catch (org.bukkit.configuration.InvalidConfigurationException ex) {
            throw new AssertionError(ex);
        }

        RouteManager loaded = new RouteManager();
        RouteStorage.load(loaded, reread, Logger.getAnonymousLogger());
        assertEquals(2, loaded.all().size());
        Route back = loaded.get("loop");
        assertEquals("world_nether", back.worldName());
        assertEquals(2, back.size());
        assertEquals(1.5, back.points().get(0).x(), 1e-9);
        assertEquals(-3.25, back.points().get(0).z(), 1e-9);
        assertEquals(12.5, back.points().get(1).y(), 1e-9);
        assertEquals(0, loaded.get("empty").size());
    }

    @Test
    void badStoredDataIsSkippedNotFatal() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("routes.good.world", "world");
        yaml.set("routes.good.points", java.util.List.of("1,2,3", "oops", "1,2", "NaN,1,1", "4,5,6"));
        yaml.set("routes.noworld.points", java.util.List.of("1,2,3"));
        yaml.set("routes.bad name.world", "world");
        RouteManager manager = new RouteManager();
        RouteStorage.load(manager, yaml, Logger.getAnonymousLogger());
        assertEquals(1, manager.all().size());
        assertEquals(2, manager.get("good").size());
    }

    @Test
    void pointParsingIsStrict() {
        assertNotNull(RouteStorage.parsePoint(" 1.5 , -2 , 3e2 "));
        assertNull(RouteStorage.parsePoint(null));
        assertNull(RouteStorage.parsePoint("1,2"));
        assertNull(RouteStorage.parsePoint("a,b,c"));
        assertNull(RouteStorage.parsePoint("Infinity,1,1"));
    }
}

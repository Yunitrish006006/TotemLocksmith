package dev.totem.locksmith.client;

import dev.totem.locksmith.menu.LocksmithManagementMenu;
import dev.totem.locksmith.menu.LocksmithMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Registers the screen through Fabric Menu API's runtime class-tweaked bridge on 26.2. */
final class LocksmithManagementScreenRegistration {
    private LocksmithManagementScreenRegistration() {
    }

    static void register() {
        try {
            Class<?> constructorType = Class.forName("net.minecraft.client.gui.screens.MenuScreens$ScreenConstructor");
            Method register = MenuScreens.class.getDeclaredMethod("register", MenuType.class, constructorType);
            register.setAccessible(true);
            Object constructor = Proxy.newProxyInstance(
                    constructorType.getClassLoader(),
                    new Class<?>[]{constructorType},
                    factory()
            );
            register.invoke(null, LocksmithMenus.MANAGEMENT, constructor);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register the Locksmith management screen", exception);
        }
    }

    private static InvocationHandler factory() {
        return (proxy, method, arguments) -> switch (method.getName()) {
            case "create" -> new LocksmithManagementScreen(
                    (LocksmithManagementMenu) arguments[0],
                    (Inventory) arguments[1],
                    (Component) arguments[2]
            );
            case "toString" -> "Totem Locksmith management screen factory";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw new UnsupportedOperationException(
                    "Unexpected MenuScreens factory method: " + method.getName());
        };
    }
}

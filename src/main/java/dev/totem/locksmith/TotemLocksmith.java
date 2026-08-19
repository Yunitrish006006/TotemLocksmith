package dev.totem.locksmith;

import dev.totem.locksmith.command.LocksmithCommands;
import dev.totem.locksmith.component.LocksmithDataComponents;
import dev.totem.locksmith.config.LocksmithConfig;
import dev.totem.locksmith.integration.FabricTransferProtection;
import dev.totem.locksmith.manual.LocksmithManual;
import dev.totem.locksmith.manual.LocksmithManualRecipeSync;
import dev.totem.locksmith.menu.LocksmithManagementInteraction;
import dev.totem.locksmith.menu.LocksmithMenus;
import dev.totem.locksmith.persistence.LockMarkerAttachments;
import dev.totem.locksmith.registry.LocksmithGameRules;
import dev.totem.locksmith.registry.LocksmithItems;
import dev.totem.locksmith.service.LocksmithAuthority;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TotemLocksmith implements ModInitializer {
    public static final String MOD_ID = "totem-locksmith";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LocksmithConfig.reload();
        LocksmithGameRules.register();
        LocksmithDataComponents.register();
        LocksmithItems.register();
        LocksmithMenus.register();
        LockMarkerAttachments.register();
        FabricTransferProtection.register();
        LocksmithManualRecipeSync.register();
        LocksmithManual.register();
        LocksmithManagementInteraction.register();
        LocksmithAuthority.register();
        LocksmithCommands.register();
        LOGGER.info("Totem Locksmith initialized with fixed-Hopper network authority");
    }
}

package dev.totem.locksmith.client;

import dev.totem.locksmith.domain.AccessMode;
import dev.totem.locksmith.domain.AutomationMode;
import dev.totem.locksmith.domain.MemberRole;
import dev.totem.locksmith.menu.LocksmithManagementMenu;
import dev.totem.locksmith.menu.LocksmithManagementOpenData;
import dev.totem.locksmith.registry.LocksmithItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Compact, item-first front end for the server-authoritative lock control plane. */
public final class LocksmithManagementScreen extends AbstractContainerScreen<LocksmithManagementMenu> {
    private static final int PANEL_WIDTH = 286;
    private static final int PANEL_HEIGHT = 224;
    private static final int TAB_Y = 34;
    private static final int TAB_W = 82;
    private static final int ROW_H = 22;
    private static final int VISIBLE_ROWS = 5;

    private Tab tab = Tab.ACCESS;
    private int memberScroll;
    private int candidateScroll;
    private int keyScroll;

    public LocksmithManagementScreen(LocksmithManagementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        inventoryLabelY = 10_000;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        drawPanel(graphics, mouseX, mouseY);
        super.extractContents(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // All labels are rendered in absolute panel coordinates.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubled);
        }
        double x = event.x();
        double y = event.y();

        for (int i = 0; i < Tab.values().length; i++) {
            if (inside(x, y, leftPos + 10 + i * (TAB_W + 5), topPos + TAB_Y, TAB_W, 18)) {
                tab = Tab.values()[i];
                return true;
            }
        }

        return switch (tab) {
            case ACCESS -> clickAccess(x, y) || super.mouseClicked(event, doubled);
            case MEMBERS -> clickMembers(x, y) || super.mouseClicked(event, doubled);
            case KEYS -> clickKeys(x, y) || super.mouseClicked(event, doubled);
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int delta = verticalAmount < 0 ? 1 : -1;
        LocksmithManagementOpenData snapshot = menu.snapshot();
        if (tab == Tab.MEMBERS) {
            if (mouseX < leftPos + PANEL_WIDTH / 2.0) {
                memberScroll = clampScroll(memberScroll + delta, snapshot.members().size());
            } else {
                candidateScroll = clampScroll(candidateScroll + delta, snapshot.candidates().size());
            }
            return true;
        }
        if (tab == Tab.KEYS) {
            keyScroll = clampScroll(keyScroll + delta, snapshot.keys().size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        LocksmithManagementOpenData snapshot = menu.snapshot();
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xFF4A4035);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + PANEL_WIDTH - 2, topPos + PANEL_HEIGHT - 2, 0xFFE7DDC8);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + PANEL_WIDTH - 6, topPos + 30, 0xFFB59669);

        graphics.item(new ItemStack(LocksmithItems.PADLOCK), leftPos + 11, topPos + 10);
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.title"),
                leftPos + 32, topPos + 9, 0xFF382B20, false);
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.owner", snapshot.ownerName()),
                leftPos + 32, topPos + 20, 0xFF675342, false);
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.network",
                        snapshot.logicalContainerCount(), snapshot.connectorCount()),
                leftPos + 175, topPos + 14, 0xFF675342, false);

        for (int i = 0; i < Tab.values().length; i++) {
            Tab candidate = Tab.values()[i];
            drawButton(graphics, leftPos + 10 + i * (TAB_W + 5), topPos + TAB_Y, TAB_W, 18,
                    Component.translatable(candidate.key), candidate == tab,
                    inside(mouseX, mouseY, leftPos + 10 + i * (TAB_W + 5), topPos + TAB_Y, TAB_W, 18), true);
        }

        switch (tab) {
            case ACCESS -> drawAccess(graphics, mouseX, mouseY, snapshot);
            case MEMBERS -> drawMembers(graphics, mouseX, mouseY, snapshot);
            case KEYS -> drawKeys(graphics, mouseX, mouseY, snapshot);
        }
    }

    private void drawAccess(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                            LocksmithManagementOpenData snapshot) {
        int y = topPos + 61;
        int modeColor = snapshot.physicalKeysRequired() ? 0xFF81502A : 0xFF2F7145;
        graphics.fill(leftPos + 10, y, leftPos + PANEL_WIDTH - 10, y + 30,
                snapshot.physicalKeysRequired() ? 0xFFFFE7C7 : 0xFFDDF3E4);
        graphics.text(font, Component.translatable(snapshot.physicalKeysRequired()
                        ? "gui.totem.locksmith.management.immersive"
                        : "gui.totem.locksmith.management.convenient"),
                leftPos + 17, y + 5, modeColor, false);
        graphics.text(font, Component.translatable(snapshot.physicalKeysRequired()
                        ? "gui.totem.locksmith.management.immersive_hint"
                        : "gui.totem.locksmith.management.convenient_hint"),
                leftPos + 17, y + 17, 0xFF66594D, false);

        y += 39;
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.access_mode"),
                leftPos + 12, y, 0xFF493A2E, false);
        y += 13;
        for (int i = 0; i < AccessMode.values().length; i++) {
            int col = i % 2;
            int row = i / 2;
            int bx = leftPos + 12 + col * 132;
            int by = y + row * 25;
            boolean selected = i == snapshot.accessModeOrdinal();
            boolean enabled = snapshot.ownerActor();
            drawButton(graphics, bx, by, 122, 20,
                    Component.translatable("gui.totem.locksmith.access." + AccessMode.values()[i].name().toLowerCase()),
                    selected, inside(mouseX, mouseY, bx, by, 122, 20), enabled);
        }

        y += 58;
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.automation_mode"),
                leftPos + 12, y, 0xFF493A2E, false);
        y += 13;
        for (int i = 0; i < AutomationMode.values().length; i++) {
            int bx = leftPos + 12 + i * 87;
            boolean selected = i == snapshot.automationModeOrdinal();
            boolean enabled = snapshot.ownerActor();
            drawButton(graphics, bx, y, 79, 20,
                    Component.translatable("gui.totem.locksmith.automation." + AutomationMode.values()[i].name().toLowerCase()),
                    selected, inside(mouseX, mouseY, bx, y, 79, 20), enabled);
        }

        int dangerY = topPos + PANEL_HEIGHT - 29;
        if (snapshot.ownerActor()) {
            drawButton(graphics, leftPos + PANEL_WIDTH - 111, dangerY, 99, 18,
                    Component.translatable("gui.totem.locksmith.management.remove_lock"), false,
                    inside(mouseX, mouseY, leftPos + PANEL_WIDTH - 111, dangerY, 99, 18), true, true);
        }
    }

    private void drawMembers(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                             LocksmithManagementOpenData snapshot) {
        int top = topPos + 63;
        int divider = leftPos + 143;
        graphics.fill(divider, top, divider + 1, topPos + PANEL_HEIGHT - 12, 0xFFB8AA92);
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.members"),
                leftPos + 12, top, 0xFF493A2E, false);
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.online_players"),
                divider + 9, top, 0xFF493A2E, false);

        List<LocksmithManagementOpenData.MemberView> members = snapshot.members();
        if (members.isEmpty()) {
            graphics.text(font, Component.translatable("gui.totem.locksmith.management.no_members"),
                    leftPos + 12, top + 24, 0xFF806F61, false);
        } else {
            for (int row = 0; row < VISIBLE_ROWS; row++) {
                int index = memberScroll + row;
                if (index >= members.size()) break;
                drawMemberRow(graphics, mouseX, mouseY, snapshot, members.get(index), index,
                        leftPos + 10, top + 17 + row * ROW_H);
            }
        }

        List<LocksmithManagementOpenData.PlayerView> candidates = snapshot.candidates();
        if (candidates.isEmpty()) {
            graphics.text(font, Component.translatable("gui.totem.locksmith.management.no_candidates"),
                    divider + 9, top + 24, 0xFF806F61, false);
        } else {
            for (int row = 0; row < VISIBLE_ROWS; row++) {
                int index = candidateScroll + row;
                if (index >= candidates.size()) break;
                drawCandidateRow(graphics, mouseX, mouseY, candidates.get(index), index,
                        divider + 7, top + 17 + row * ROW_H);
            }
        }
    }

    private void drawMemberRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               LocksmithManagementOpenData snapshot,
                               LocksmithManagementOpenData.MemberView member, int index, int x, int y) {
        graphics.fill(x, y, x + 128, y + 19, 0xFFF4EEE1);
        String name = trim(member.name(), 54);
        graphics.text(font, name, x + 4, y + 6, 0xFF40352C, false);
        MemberRole role = safeRole(member.roleOrdinal());
        int roleX = x + 60;
        boolean canEditRole = snapshot.ownerActor() || role != MemberRole.MANAGER;
        drawButton(graphics, roleX, y + 2, 47, 15,
                Component.translatable("gui.totem.locksmith.role." + role.name().toLowerCase()),
                false, inside(mouseX, mouseY, roleX, y + 2, 47, 15), canEditRole);
        int removeX = x + 110;
        drawButton(graphics, removeX, y + 2, 15, 15, Component.literal("×"), false,
                inside(mouseX, mouseY, removeX, y + 2, 15, 15), canEditRole, true);
    }

    private void drawCandidateRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  LocksmithManagementOpenData.PlayerView candidate, int index, int x, int y) {
        graphics.fill(x, y, x + 126, y + 19, 0xFFF4EEE1);
        graphics.text(font, trim(candidate.name(), 82), x + 4, y + 6, 0xFF40352C, false);
        int addX = x + 105;
        drawButton(graphics, addX, y + 2, 18, 15, Component.literal("+"), false,
                inside(mouseX, mouseY, addX, y + 2, 18, 15), true);
    }

    private void drawKeys(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                          LocksmithManagementOpenData snapshot) {
        int top = topPos + 63;
        graphics.item(new ItemStack(LocksmithItems.KEY_BLANK), leftPos + 12, top);
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.issue_hint"),
                leftPos + 34, top + 1, 0xFF493A2E, false);
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.issue_hint_2"),
                leftPos + 34, top + 12, 0xFF806F61, false);
        drawButton(graphics, leftPos + 197, top, 76, 19,
                Component.translatable("gui.totem.locksmith.management.issue_key"), false,
                inside(mouseX, mouseY, leftPos + 197, top, 76, 19), true);

        int listTop = top + 32;
        graphics.text(font, Component.translatable("gui.totem.locksmith.management.active_keys", snapshot.keys().size()),
                leftPos + 12, listTop, 0xFF493A2E, false);
        List<LocksmithManagementOpenData.KeyView> keys = snapshot.keys();
        if (keys.isEmpty()) {
            graphics.text(font, Component.translatable("gui.totem.locksmith.management.no_keys"),
                    leftPos + 12, listTop + 22, 0xFF806F61, false);
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = keyScroll + row;
            if (index >= keys.size()) break;
            LocksmithManagementOpenData.KeyView key = keys.get(index);
            int y = listTop + 16 + row * ROW_H;
            graphics.fill(leftPos + 10, y, leftPos + PANEL_WIDTH - 10, y + 19, 0xFFF4EEE1);
            graphics.item(new ItemStack(LocksmithItems.BOUND_KEY), leftPos + 13, y + 1);
            graphics.text(font, trim(key.label(), 110), leftPos + 34, y + 6, 0xFF40352C, false);
            String shortId = key.keyId().toString().substring(0, 8);
            graphics.text(font, shortId, leftPos + 145, y + 6, 0xFF807161, false);
            int revokeX = leftPos + 225;
            drawButton(graphics, revokeX, y + 2, 47, 15,
                    Component.translatable("gui.totem.locksmith.management.revoke"), false,
                    inside(mouseX, mouseY, revokeX, y + 2, 47, 15), true, true);
        }

        if (snapshot.ownerActor()) {
            int y = topPos + PANEL_HEIGHT - 29;
            drawButton(graphics, leftPos + 12, y, 106, 18,
                    Component.translatable("gui.totem.locksmith.management.rotate_keys"), false,
                    inside(mouseX, mouseY, leftPos + 12, y, 106, 18), true, true);
        }
    }

    private boolean clickAccess(double x, double y) {
        LocksmithManagementOpenData snapshot = menu.snapshot();
        int modesY = topPos + 113;
        if (snapshot.ownerActor()) {
            for (int i = 0; i < AccessMode.values().length; i++) {
                int bx = leftPos + 12 + (i % 2) * 132;
                int by = modesY + (i / 2) * 25;
                if (inside(x, y, bx, by, 122, 20)) {
                    sendButton(LocksmithManagementMenu.ACCESS_BASE + i);
                    return true;
                }
            }
            int automationY = topPos + 184;
            for (int i = 0; i < AutomationMode.values().length; i++) {
                int bx = leftPos + 12 + i * 87;
                if (inside(x, y, bx, automationY, 79, 20)) {
                    sendButton(LocksmithManagementMenu.AUTOMATION_BASE + i);
                    return true;
                }
            }
            if (inside(x, y, leftPos + PANEL_WIDTH - 111, topPos + PANEL_HEIGHT - 29, 99, 18)) {
                sendButton(LocksmithManagementMenu.REMOVE_LOCK);
                return true;
            }
        }
        return false;
    }

    private boolean clickMembers(double x, double y) {
        LocksmithManagementOpenData snapshot = menu.snapshot();
        int top = topPos + 80;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = memberScroll + row;
            if (index >= snapshot.members().size()) break;
            int rowY = top + row * ROW_H;
            MemberRole role = safeRole(snapshot.members().get(index).roleOrdinal());
            boolean editable = snapshot.ownerActor() || role != MemberRole.MANAGER;
            if (editable && inside(x, y, leftPos + 70, rowY + 2, 47, 15)) {
                sendButton(LocksmithManagementMenu.MEMBER_ROLE_BASE + index);
                return true;
            }
            if (editable && inside(x, y, leftPos + 120, rowY + 2, 15, 15)) {
                sendButton(LocksmithManagementMenu.MEMBER_REMOVE_BASE + index);
                return true;
            }
        }

        int divider = leftPos + 143;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = candidateScroll + row;
            if (index >= snapshot.candidates().size()) break;
            int rowY = top + row * ROW_H;
            if (inside(x, y, divider + 112, rowY + 2, 18, 15)) {
                sendButton(LocksmithManagementMenu.CANDIDATE_ADD_BASE + index);
                return true;
            }
        }
        return false;
    }

    private boolean clickKeys(double x, double y) {
        LocksmithManagementOpenData snapshot = menu.snapshot();
        int top = topPos + 63;
        if (inside(x, y, leftPos + 197, top, 76, 19)) {
            sendButton(LocksmithManagementMenu.BIND_KEY);
            return true;
        }
        int listTop = top + 48;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = keyScroll + row;
            if (index >= snapshot.keys().size()) break;
            int rowY = listTop + row * ROW_H;
            if (inside(x, y, leftPos + 225, rowY + 2, 47, 15)) {
                sendButton(LocksmithManagementMenu.KEY_REVOKE_BASE + index);
                return true;
            }
        }
        if (snapshot.ownerActor()
                && inside(x, y, leftPos + 12, topPos + PANEL_HEIGHT - 29, 106, 18)) {
            sendButton(LocksmithManagementMenu.ROTATE_KEYS);
            return true;
        }
        return false;
    }

    private void sendButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            Component label, boolean selected, boolean hovered, boolean enabled) {
        drawButton(graphics, x, y, width, height, label, selected, hovered, enabled, false);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                            Component label, boolean selected, boolean hovered, boolean enabled, boolean danger) {
        int border;
        int fill;
        int text;
        if (!enabled) {
            border = 0xFF9E9588;
            fill = 0xFFC8C0B3;
            text = 0xFF877F73;
        } else if (danger) {
            border = hovered ? 0xFF8A2D25 : 0xFF9B5149;
            fill = hovered ? 0xFFD66B5F : 0xFFE1A49D;
            text = 0xFF4D1713;
        } else if (selected) {
            border = 0xFF4F754F;
            fill = 0xFFBBD6B4;
            text = 0xFF29432A;
        } else {
            border = hovered ? 0xFF806443 : 0xFFA39178;
            fill = hovered ? 0xFFF2D8AA : 0xFFD8C8AA;
            text = 0xFF493A2E;
        }
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        graphics.centeredText(font, label, x + width / 2, y + Math.max(3, (height - 8) / 2), text);
    }

    private String trim(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private static MemberRole safeRole(int ordinal) {
        MemberRole[] roles = MemberRole.values();
        return ordinal >= 0 && ordinal < roles.length ? roles[ordinal] : MemberRole.USER;
    }

    private static int clampScroll(int value, int count) {
        return Math.max(0, Math.min(value, Math.max(0, count - VISIBLE_ROWS)));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum Tab {
        ACCESS("gui.totem.locksmith.management.tab.access"),
        MEMBERS("gui.totem.locksmith.management.tab.members"),
        KEYS("gui.totem.locksmith.management.tab.keys");

        private final String key;
        Tab(String key) { this.key = key; }
    }
}

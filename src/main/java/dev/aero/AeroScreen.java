package dev.aero;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Native menus display the server's state; only the server grants supplies. */
public final class AeroScreen extends Screen {
    private static final int TEXT = 0xffebf2ea;
    private static final int MUTED = 0xffbdcfce;
    private static final int FLOW = 0xffdbfde9;
    private static final String[] RULES = {
        "Right-click a valve to turn it clockwise. Match its ports to its neighbours. Only connected tiles light up.",
        "Blue water enters from the west of the northwest tile. Connect it to the east outlet of the southeast tile. Restore three flows, then use the exit compass.",
        "M opens the map. G returns you home. Continue an unfinished journey from the compass console.",
        "Specimens are optional. Gather their cuttings and plant them at home. Workshop bundles are real inventory items. Place and mine in your garden; your building persists."
    };
    private static final String[] MATERIALS = {"white", "aqua", "blue", "glass", "green", "light"};
    private static final String[] BUNDLES = {
        "32 Porcelain", "32 Aqua", "32 Sapphire",
        "32 Glass", "32 Foliage", "8 Lights"
    };

    private final String panel;
    private final List<ButtonWidget> materialButtons = new ArrayList<>();
    private final List<ButtonWidget> journeyButtons = new ArrayList<>();
    private ButtonWidget expandButton;
    private int left;
    private int top;
    private int contentWidth;
    private int scroll;
    private int guideHeight;
    private boolean unfinished;
    private Text mapTooltip;

    public AeroScreen(String panel) {
        super(Text.literal(switch (panel) {
            case "navigator" -> "Next journey";
            case "workshop" -> "Garden workshop";
            case "map" -> "Journey map";
            default -> "Field guide";
        }));
        this.panel = panel;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(440, width - 32);
        left = (width - contentWidth) / 2;
        top = Math.max(16, Math.min(32, (height - 244) / 2));
        materialButtons.clear();
        journeyButtons.clear();
        expandButton = null;
        JsonObject state = view();
        unfinished = flag(state, "active") && !flag(state, "completed");
        if (panel.equals("navigator")) initNavigator();
        if (panel.equals("workshop")) initWorkshop();
        guideHeight = 0;
        for (String rule : RULES) guideHeight += textRenderer.wrapLines(Text.literal(rule), contentWidth).size() * 12 + 5;
        guideHeight += 90;
        scroll = Math.min(scroll, maxScroll());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
        updateButtons(state);
    }

    private void initNavigator() {
        if (unfinished) {
            journeyButtons.add(actionButton("Continue journey", "resume", left, top + 64, contentWidth));
            journeyButtons.add(actionButton("Leave unfinished journey", "abandon", left, top + 92, contentWidth));
            return;
        }
        for (int route = 0; route < AeroGame.THEMES.length; route++) {
            journeyButtons.add(actionButton(AeroGame.THEMES[route], "route:" + route,
                    left, top + 54 + route * 52, Math.min(140, contentWidth / 3)));
        }
    }

    private void initWorkshop() {
        int bundleWidth = (contentWidth - 10) / 2;
        for (int i = 0; i < MATERIALS.length; i++) {
            materialButtons.add(actionButton(BUNDLES[i] + " / 2 credits", "material:" + MATERIALS[i],
                    left + (i % 2) * (bundleWidth + 10), top + 52 + (i / 2) * 28, bundleWidth));
        }
        expandButton = actionButton("Expand garden / 12 credits", "expand", left, top + 172, contentWidth);
    }

    private ButtonWidget actionButton(String label, String action, int x, int y, int buttonWidth) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> AeroClient.action(action))
                .dimensions(x, y, buttonWidth, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        JsonObject state = view();
        boolean pending = flag(state, "active") && !flag(state, "completed");
        if (panel.equals("navigator") && unfinished != pending) {
            clearAndInit();
            return;
        }
        updateButtons(state);
    }

    private void updateButtons(JsonObject state) {
        for (ButtonWidget button : journeyButtons) button.active = !flag(state, "building");
        for (ButtonWidget button : materialButtons) button.active = number(state, "credits", 0) >= 2;
        if (expandButton != null) expandButton.active = number(state, "credits", 0) >= 12
                && !flag(state, "building");
    }

    @Override
    public void renderBackground(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, 0xaa091018);
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        renderBackground(draw, mouseX, mouseY, delta);
        draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, top, TEXT);
        JsonObject state = view();
        mapTooltip = null;
        switch (panel) {
            case "navigator" -> renderNavigator(draw, state);
            case "workshop" -> renderWorkshop(draw, state);
            case "map" -> renderMap(draw, state, mouseX, mouseY);
            default -> renderGuide(draw, state);
        }
        super.render(draw, mouseX, mouseY, delta);
        if (mapTooltip != null) draw.drawTooltip(textRenderer, mapTooltip, mouseX, mouseY);
    }

    private void renderNavigator(DrawContext draw, JsonObject state) {
        if (flag(state, "building")) {
            draw.drawTextWithShadow(textRenderer, "Preparing the journey: " + number(state, "percent", 0) + "%",
                    left, top + 28, MUTED);
        } else if (unfinished) {
            paragraph(draw, "Your current journey is waiting. Leaving it keeps the finds already gathered.",
                    left, top + 28, contentWidth, MUTED);
        } else {
            draw.drawTextWithShadow(textRenderer, "Choose the kind of place you want to explore.", left, top + 28, MUTED);
        }
        if (!unfinished) {
            String[][] descriptions = {
                {"Wide water rooms and stepping crossings", "Three 3 × 3 flow circuits"},
                {"Raised gardens and connecting stairs", "Three 3 × 3 flow circuits"},
                {"Low galleries and quiet passages", "Three 4 × 4 flow circuits"}
            };
            int x = left + Math.min(140, contentWidth / 3) + 12;
            for (int i = 0; i < descriptions.length; i++) {
                paragraph(draw, descriptions[i][0], x, top + 56 + i * 52, left + contentWidth - x, TEXT);
                draw.drawTextWithShadow(textRenderer, descriptions[i][1], x, top + 80 + i * 52, MUTED);
            }
        }
    }

    private void renderWorkshop(DrawContext draw, JsonObject state) {
        draw.drawTextWithShadow(textRenderer, "Credits: " + number(state, "credits", 0), left, top + 28, TEXT);
        int size = number(state, "gardenSize", 18);
        draw.drawTextWithShadow(textRenderer, "Garden: " + size + " × " + size + " blocks", left, top + 144, TEXT);
        draw.drawTextWithShadow(textRenderer, "Expansion adds 10 blocks along each axis.",
                left, top + 156, MUTED);
    }

    private void renderGuide(DrawContext draw, JsonObject state) {
        int bodyTop = top + 28;
        int bodyBottom = height - 44;
        draw.enableScissor(left, bodyTop, left + contentWidth, bodyBottom);
        int y = bodyTop - scroll;
        for (String rule : RULES) y = paragraph(draw, rule, left, y, contentWidth, TEXT) + 5;
        draw.drawTextWithShadow(textRenderer, "Discoveries", left, y + 3, TEXT);
        Set<Integer> discovered = indices(state, "discoveries");
        y += 20;
        int columnWidth = contentWidth / 2;
        for (int i = 0; i < AeroGame.SPECIES.length; i++) {
            String name = discovered.contains(i) ? AeroGame.SPECIES[i] : "Undiscovered";
            draw.drawTextWithShadow(textRenderer, (i + 1) + ". " + name,
                    left + (i / 6) * columnWidth, y + (i % 6) * 12,
                    discovered.contains(i) ? FLOW : MUTED);
        }
        draw.disableScissor();
        int available = bodyBottom - bodyTop;
        if (guideHeight > available) {
            int thumbHeight = Math.max(12, available * available / guideHeight);
            int thumbY = bodyTop + scroll * (available - thumbHeight) / maxScroll();
            draw.fill(left + contentWidth + 5, bodyTop, left + contentWidth + 6, bodyBottom, 0xff617576);
            draw.fill(left + contentWidth + 4, thumbY, left + contentWidth + 7, thumbY + thumbHeight, MUTED);
        }
    }

    private void renderMap(DrawContext draw, JsonObject state, int mouseX, int mouseY) {
        if (!flag(state, "active") || !state.has("rooms")) {
            paragraph(draw, "Choose a journey at the compass console to begin a new map.", left, top + 32, contentWidth, MUTED);
            return;
        }
        Set<Integer> visited = indices(state, "visited");
        Set<Integer> solved = indices(state, "solved");
        Set<Integer> sampled = indices(state, "sampled");
        int current = flag(state, "sector") ? number(state, "room", -1) : -1;
        if (current >= 0) visited.add(current);
        JsonObject[] rooms = new JsonObject[9];
        JsonArray data = state.getAsJsonArray("rooms");
        for (var element : data) {
            JsonObject room = element.getAsJsonObject();
            int id = number(room, "id", -1);
            if (id >= 0 && id < rooms.length) rooms[id] = room;
        }
        int gap = 14;
        int cellWidth = (Math.min(contentWidth, 360) - gap * 2) / 3;
        int cellHeight = Math.max(28, Math.min(44, (height - top - 138) / 3));
        int startX = width / 2 - (3 * cellWidth + 2 * gap) / 2;
        int startY = top + 36;
        draw.drawCenteredTextWithShadow(textRenderer,
                "North ↑" + (current >= 0 ? " / You: room " + (current + 1) : ""), width / 2, top + 20, MUTED);
        for (int id = 0; id < 9; id++) {
            if (rooms[id] == null) continue;
            int x = startX + (id % 3) * (cellWidth + gap);
            int y = startY + (id / 3) * (cellHeight + gap);
            int links = number(rooms[id], "links", 0);
            if (id % 3 < 2 && (links & 2) != 0 && (visited.contains(id) || visited.contains(id + 1)))
                draw.drawHorizontalLine(x + cellWidth, x + cellWidth + gap, y + cellHeight / 2, MUTED);
            if (id < 6 && (links & 4) != 0 && (visited.contains(id) || visited.contains(id + 3)))
                draw.drawVerticalLine(x + cellWidth / 2, y + cellHeight, y + cellHeight + gap, MUTED);
            draw.drawBorder(x, y, cellWidth, cellHeight, current == id ? FLOW : 0xff718b8c);
            String kind = rooms[id].get("kind").getAsString();
            String name = visited.contains(id) ? roomName(kind) : "?";
            draw.drawTextWithShadow(textRenderer, (id + 1) + ". " + name,
                    x + 5, y + 5, TEXT);
            String status = !visited.contains(id) ? "Unexplored"
                    : solved.contains(id) ? "Restored"
                    : sampled.contains(id) ? "Gathered"
                    : kind.equals("FLOW") ? "Flow needed"
                    : kind.equals("SPECIMEN") ? "Optional find" : "Visited";
            draw.drawTextWithShadow(textRenderer, status, x + 5, y + 17, solved.contains(id) ? FLOW : MUTED);
            if (visited.contains(id) && kind.equals("SPECIMEN") && mouseX >= x && mouseX < x + cellWidth
                    && mouseY >= y && mouseY < y + cellHeight) {
                int species = number(rooms[id], "species", -1);
                Set<Integer> discovered = indices(state, "discoveries");
                String label = species >= 0 && species < AeroGame.SPECIES.length && discovered.contains(species)
                        ? AeroGame.SPECIES[species] : "Optional specimen";
                mapTooltip = Text.literal(label);
            }
        }
        int footer = startY + 3 * cellHeight + 2 * gap + 10;
        draw.drawTextWithShadow(textRenderer, "Flows restored: " + solved.size() + " / 3", startX, footer, FLOW);
        draw.drawTextWithShadow(textRenderer, "Optional finds gathered: " + sampled.size(), startX, footer + 12, MUTED);
    }

    private static String roomName(String kind) {
        return switch (kind) {
            case "ENTRY" -> "Entry";
            case "FLOW" -> "Flow";
            case "STEPPING" -> "Crossing";
            case "SPECIMEN" -> "Specimen";
            case "OVERLOOK" -> "Overlook";
            default -> "Room";
        };
    }

    private int paragraph(DrawContext draw, String text, int x, int y, int textWidth, int color) {
        for (var line : textRenderer.wrapLines(Text.literal(text), textWidth)) {
            draw.drawTextWithShadow(textRenderer, line, x, y, color);
            y += 12;
        }
        return y;
    }

    private int maxScroll() {
        return Math.max(0, guideHeight - (height - 44 - top - 28));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if ((panel.equals("guide") || !List.of("navigator", "workshop", "map").contains(panel)) && maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (vertical * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static JsonObject view() {
        return AeroClient.view == null ? new JsonObject() : AeroClient.view;
    }

    private static boolean flag(JsonObject state, String key) {
        return state.has(key) && !state.get(key).isJsonNull() && state.get(key).getAsBoolean();
    }

    private static int number(JsonObject state, String key, int fallback) {
        return state.has(key) && !state.get(key).isJsonNull() ? state.get(key).getAsInt() : fallback;
    }

    private static Set<Integer> indices(JsonObject state, String key) {
        Set<Integer> result = new HashSet<>();
        if (state.has(key) && state.get(key).isJsonArray()) for (var value : state.getAsJsonArray(key)) result.add(value.getAsInt());
        return result;
    }
}

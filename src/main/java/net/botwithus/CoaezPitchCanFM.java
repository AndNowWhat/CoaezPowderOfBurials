package net.botwithus;

import net.botwithus.api.game.hud.Dialog;
import net.botwithus.api.game.hud.Hud;
import net.botwithus.api.game.hud.inventories.Backpack;
import net.botwithus.api.game.hud.inventories.Bank;
import net.botwithus.internal.scripts.ScriptDefinition;
import net.botwithus.rs3.events.impl.ChatMessageEvent;
import net.botwithus.rs3.game.Area;
import net.botwithus.rs3.game.Client;
import net.botwithus.rs3.game.hud.interfaces.Component;
import net.botwithus.rs3.game.hud.interfaces.Interfaces;
import net.botwithus.rs3.game.inventories.Equipment;
import net.botwithus.rs3.game.inventories.InventoryContainer;
import net.botwithus.rs3.game.login.LoginManager;
import net.botwithus.rs3.game.minimenu.MiniMenu;
import net.botwithus.rs3.game.minimenu.actions.ComponentAction;
import net.botwithus.rs3.game.movement.Movement;
import net.botwithus.rs3.game.queries.builders.characters.NpcQuery;
import net.botwithus.rs3.game.queries.builders.components.ComponentQuery;
import net.botwithus.rs3.game.queries.results.EntityResultSet;
import net.botwithus.rs3.game.scene.entities.characters.npc.Npc;
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer;
import net.botwithus.rs3.game.vars.VarManager;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.script.LoopingScript;
import net.botwithus.rs3.script.config.ScriptConfig;
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery;
import net.botwithus.rs3.game.scene.entities.object.SceneObject;
import net.botwithus.rs3.game.actionbar.ActionBar;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CoaezPitchCanFM extends LoopingScript {

    BotState botState = BotState.IDLE;
    private Random random = new Random();
    private List<Coordinate> crossPattern = new ArrayList<>();
    private int currentCoordIndex = 0;
    private boolean coordsGenerated = false;
    private boolean reverseOrder = false;
    public final CoaezGraphicsContext sgc;
    private ScriptConfig config;

    private static final int MAGIC_LOGS_ID = 1513;
    private static final int BOB_REMAINING_TIME_VARBIT = 6055;
    private static final int MIN_BOB_TIME_THRESHOLD = 5; // minutes
    private static final int BOB_INVENTORY_ID = 530;
    private int playerPresetNumber = 2;

    public static final net.botwithus.rs3.game.Coordinate FIREMAKING_LOCATION =
            new net.botwithus.rs3.game.Coordinate(2234, 3311, 1);
    private static final Area BANK_AREA = new Area.Rectangular(
            new net.botwithus.rs3.game.Coordinate(2235, 3315, 1),
            new net.botwithus.rs3.game.Coordinate(2240, 3320, 1)
    );
    public static final net.botwithus.rs3.game.Coordinate OBELISK_LOCATION =
            new net.botwithus.rs3.game.Coordinate(2195, 3330, 1);

    private final InventoryContainer bobInventory = InventoryContainer.of(BOB_INVENTORY_ID);

    public int getPlayerPresetNumber() {
        return playerPresetNumber;
    }

    public void setPlayerPresetNumber(int playerPresetNumber) {
        this.playerPresetNumber = playerPresetNumber;
    }

    enum BotState {
        IDLE,
        CHECK_FAMILIAR,
        CHECK_SUMMONING_POINTS,
        RESTORE_SUMMONING_POINTS,
        SUMMON_FAMILIAR,
        LOAD_BOB_PRESET,
        LOAD_INVENTORY_PRESET,
        REGULAR_FIREMAKING,
        START_MINIGAME,
        CLIMB_DOWN,
        GENERATE_COORDS,
        MAKING_FIRES,
        RESET_PATTERN,
        TELEPORT_BACK,
        STOPPED
    }

    static class Coordinate {
        int x, y;
        boolean visited;

        public Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
            this.visited = false;
        }

        public net.botwithus.rs3.game.Coordinate toGameCoordinate() {
            return new net.botwithus.rs3.game.Coordinate(x, y, 0);
        }

        public boolean isWithinArea(Area area) {
            return area.contains(toGameCoordinate());
        }

        @Override
        public String toString() {
            return "Coordinate(x=" + x + ", y=" + y + ")";
        }
    }

    public CoaezPitchCanFM(String s, ScriptConfig scriptConfig, ScriptDefinition scriptDefinition) {
        super(s, scriptConfig, scriptDefinition);
        this.config = scriptConfig;
        subscribe(ChatMessageEvent.class, this::onChatMessage);
        println("Script initialized and ready to start");
        this.sgc = new CoaezGraphicsContext(this.getConsole(), this);

    }

    @Override
    public void onLoop() {
        if (!isActive()) {
            return;
        }
        LocalPlayer player = Client.getLocalPlayer();
        if (player == null) return;

        if(sgc.hasStateChanged()){
            sgc.saveConfig();
        }

        if (botState == BotState.IDLE) {
            println("Checking for minigame NPC...");
            EntityResultSet<Npc> miniGameNpcs = NpcQuery.newQuery().byType(14831).option("Start-session").results();
            if(!miniGameNpcs.isEmpty()) {
                println("Minigame NPC found, transitioning to START_MINIGAME state");
                botState = BotState.START_MINIGAME;
            } else {
                println("No minigame NPC found, transitioning to regular firemaking mode");
                botState = BotState.CHECK_FAMILIAR;
            }
        }

        switch (botState) {
            case CHECK_FAMILIAR:
                println("Checking familiar time remaining...");
                int remainingTime = VarManager.getVarbitValue(BOB_REMAINING_TIME_VARBIT);
                println("Familiar time remaining: " + remainingTime + " minutes");
                if (remainingTime < MIN_BOB_TIME_THRESHOLD) {
                    println("Familiar time low, checking summoning points");
                    botState = BotState.CHECK_SUMMONING_POINTS;
                } else {
                    println("Familiar time sufficient, proceeding to load BoB");
                    botState = BotState.LOAD_BOB_PRESET;
                }
                break;

            case CHECK_SUMMONING_POINTS:
                println("Checking summoning points...");
                int currentPoints = player.getSummoningPoints();
                println("Current summoning points: " + currentPoints);
                if (currentPoints < 100) {
                    println("Summoning points too low, need to restore");
                    botState = BotState.RESTORE_SUMMONING_POINTS;
                } else {
                    println("Summoning points sufficient, proceeding to summon familiar");
                    botState = BotState.SUMMON_FAMILIAR;
                }
                break;

            case RESTORE_SUMMONING_POINTS:
                println("Restoring summoning points...");
                if (!player.getCoordinate().equals(OBELISK_LOCATION)) {
                    println("Teleporting to Ithell...");
                    if (Equipment.interact("Attuned crystal teleport seed", "Activate")) {
                        println("Waiting for teleport interface...");
                        Execution.delayUntil(5000, () -> Interfaces.isOpen(720));

                        if (Interfaces.isOpen(720)) {
                            println("Selecting Ithell teleport...");
                            MiniMenu.interact(ComponentAction.DIALOGUE.getType(), 0, -1, 47185955);
                            Execution.delayUntil(5000, () -> !Interfaces.isOpen(720));

                            println("Walking to obelisk...");
                            Movement.walkTo(OBELISK_LOCATION.getX(), OBELISK_LOCATION.getY(), false);
                            Execution.delayUntil(15000, () ->
                                    player.getCoordinate().equals(OBELISK_LOCATION));
                        }
                    }
                }

                if (player.getCoordinate().equals(OBELISK_LOCATION)) {
                    println("Interacting with obelisk...");
                    EntityResultSet<SceneObject> results = SceneObjectQuery.newQuery()
                            .name("Small obelisk")
                            .option("Renew-points")
                            .results();

                    SceneObject obelisk = results.nearest();
                    if (obelisk != null && obelisk.interact("Renew-points")) {
                        println("Waiting for points to restore...");
                        Execution.delayUntil(5000, () -> player.getSummoningPoints() >= 100);
                        Execution.delay(random.nextInt(1200,1800));
                        println("Points restored, returning to bank");
                        botState = BotState.TELEPORT_BACK;
                    }
                }
                break;

            case SUMMON_FAMILIAR:
                println("Attempting to summon Pack Mammoth...");
                if (Backpack.contains("Pack mammoth pouch")) {
                    println("Found mammoth pouch, attempting to summon");
                    if (Backpack.interact("Pack mammoth pouch", "Summon")) {
                        println("Summoning mammoth...");
                        Execution.delayUntil(5000, () ->
                                VarManager.getVarbitValue(BOB_REMAINING_TIME_VARBIT) > MIN_BOB_TIME_THRESHOLD);
                        println("Mammoth summoned successfully");
                        botState = BotState.LOAD_BOB_PRESET;
                    }
                } else {
                    println("ERROR: No mammoth pouch found! Stopping script.");
                    botState = BotState.STOPPED;
                }
                break;

            case LOAD_BOB_PRESET:
                println("Loading BoB preset...");
                if (!Bank.isOpen()) {
                    println("Opening bank...");
                    Bank.open();
                    Execution.delayUntil(5000, Bank::isOpen);
                }
                if (Bank.isOpen()) {
                    println("Loading BoB preset 19");
                    if (Bank.loadPreset(19)) {
                        println("Waiting for BoB to be filled...");
                        Execution.delayUntil(3000, this::isBoBInventoryFull);
                        Execution.delay(random.nextInt(600,800));
                        println("BoB loaded successfully");
                        botState = BotState.LOAD_INVENTORY_PRESET;
                    }
                }
                break;

            case LOAD_INVENTORY_PRESET:
                println("Loading inventory preset...");
                Execution.delay(random.nextInt(600,800));
                if (!Bank.isOpen()) {
                    Bank.open();
                    Execution.delayUntil(3000, Bank::isOpen);
                    println("Loading inventory preset 2");
                    if (Bank.loadPreset(playerPresetNumber)) {
                        println("Waiting for inventory to be filled...");
                        Execution.delayUntil(3000, () ->
                                Backpack.contains(MAGIC_LOGS_ID));
                        Execution.delay(random.nextInt(600,800));
                        println("Inventory loaded successfully");
                        if(!Backpack.contains(MAGIC_LOGS_ID) || (Backpack.contains("Pitch can"))) {
                            println("No logs or pitch can after loading preset, stoping");
                            stopScript();
                        }
                        botState = BotState.REGULAR_FIREMAKING;
                    }
                }
                break;

            case REGULAR_FIREMAKING:
                if (!player.getCoordinate().equals(FIREMAKING_LOCATION)) {
                    println("Walking to initial firemaking location: " + FIREMAKING_LOCATION);
                    Movement.walkTo(FIREMAKING_LOCATION.getX(), FIREMAKING_LOCATION.getY(), false);
                    Execution.delayUntil(8000, () ->
                            player.getServerCoordinate().equals(FIREMAKING_LOCATION));
                } else {
                    while (isActive()) {
                        int logCount = Backpack.getCount(MAGIC_LOGS_ID);
                        println("Current log count: " + logCount);

                        if (logCount == 0) {
                            if (bobInventory.contains(MAGIC_LOGS_ID)) {
                                println("Inventory empty, getting logs from BoB");
                                Execution.delay(random.nextInt(300, 400));
                                MiniMenu.interact(ComponentAction.COMPONENT.getType(), 1, -1, 93716518);
                                Execution.delay(random.nextInt(300, 400));
                                continue;
                            } else {
                                println("No logs in inventory or BoB, going to bank");
                                botState = BotState.TELEPORT_BACK;
                                break;
                            }
                        }

                        println("Lighting fire with Magic logs...");
                        if (ActionBar.useItem("Magic logs", "Light")) {
                            Execution.delay(600);
                        }
                    }
                }
                break;

            case TELEPORT_BACK:
                println("Attempting to teleport back to bank...");
                if (Equipment.contains("Attuned crystal teleport seed")) {
                    println("Found teleport seed, activating...");
                    if (Equipment.interact("Attuned crystal teleport seed", "Activate")) {
                        println("Waiting for teleport interface...");
                        Execution.delayUntil(5000, () -> Interfaces.isOpen(720));

                        if (Interfaces.isOpen(720)) {
                            println("Selecting Trahaearn teleport...");
                            ComponentQuery query = ComponentQuery.newQuery(720)
                                    .text("Trahaearn (Prifddinas)", String::contains);
                            Component teleportOption = query.results().first();

                            if (teleportOption != null) {
                                println("Teleporting to Trahaearn...");
                                MiniMenu.interact(ComponentAction.DIALOGUE.getType(), 0, -1, 47185964);
                                Execution.delayUntil(3000, () ->
                                        BANK_AREA.contains(Client.getLocalPlayer().getCoordinate()));
                                Execution.delay(random.nextInt(1200,1800));
                                println("Successfully arrived at bank");
                                botState = BotState.CHECK_FAMILIAR;
                            }
                        }
                    }
                } else {
                    println("ERROR: No teleport seed found! Stopping script.");
                    botState = BotState.STOPPED;
                }
                break;

            case START_MINIGAME:
                println("Initiating minigame start sequence...");
                EntityResultSet<Npc> npcResults = NpcQuery.newQuery().byType(14831).option("Start-session").results();
                if (!npcResults.isEmpty()) {
                    Npc instructor = npcResults.first();
                    if (instructor != null) {
                        println("Interacting with instructor...");
                        instructor.interact("Start-session");
                        Execution.delayUntil(3000, () -> Interfaces.isOpen(1191));

                        if (Interfaces.isOpen(1191)) {
                            println("Handling first dialog...");
                            handleDialog();
                            Execution.delay(random.nextInt(600,800));
                        }
                        if (Interfaces.isOpen(1184)) {
                            println("Handling second dialog...");
                            handleDialog();
                            Execution.delay(random.nextInt(600,800));
                        }
                        if (Interfaces.isOpen(1209)) {
                            println("Dialog complete, proceeding to climb down");
                            botState = BotState.CLIMB_DOWN;
                        }
                    }
                }
                break;

            case CLIMB_DOWN:
                println("Looking for stairs to climb down...");
                EntityResultSet<SceneObject> stairsResults = SceneObjectQuery.newQuery().id(66205).option("Climb").results();
                if (!stairsResults.isEmpty()) {
                    SceneObject stairs = stairsResults.nearest();
                    if (stairs != null) {
                        println("Found stairs, climbing down...");
                        stairs.interact("Climb");
                        Execution.delayUntil(8000, () -> player.getAnimationId() != -1);
                        Execution.delayUntil(8000, () -> player.getAnimationId() == -1);
                        println("Successfully climbed down");
                        botState = BotState.GENERATE_COORDS;
                    }
                }
                break;

            case GENERATE_COORDS:
                println("Generating cross pattern coordinates...");
                if (!coordsGenerated) {
                    generateCrossPattern(player.getCoordinate().getX(), player.getCoordinate().getY());
                    coordsGenerated = true;
                    println("Coordinates generated, pattern size: " + crossPattern.size());
                    botState = BotState.MAKING_FIRES;
                }
                break;

            case MAKING_FIRES:
                if (currentCoordIndex < crossPattern.size()) {
                    Coordinate currentCoord = crossPattern.get(currentCoordIndex);
                    println("Processing coordinate " + (currentCoordIndex + 1) + "/" + crossPattern.size() +
                            " at position (" + currentCoord.x + ", " + currentCoord.y + ")");

                    if (!currentCoord.visited) {
                        EntityResultSet<SceneObject> existingFires = SceneObjectQuery.newQuery()
                                .id(66132)
                                .on(new net.botwithus.rs3.game.Coordinate(currentCoord.x, currentCoord.y, 0))
                                .results();

                        if (!existingFires.isEmpty()) {
                            println("Fire already exists at current coordinate, moving to next");
                            currentCoord.visited = true;
                            currentCoordIndex++;
                        } else {
                            println("Walking to coordinate...");
                            Movement.walkTo(currentCoord.x, currentCoord.y, false);
                            Execution.delayUntil(8000, () ->
                                    player.getServerCoordinate().getX() == currentCoord.x &&
                                            player.getServerCoordinate().getY() == currentCoord.y);

                            println("Making fire at current position");
                            if (ActionBar.useItem("Pitch can", "Make-fire")) {
                                currentCoord.visited = true;
                                Execution.delay(random.nextInt(400,600));
                                currentCoordIndex++;
                            }
                        }
                    }
                } else {
                    println("Completed current pattern, resetting...");
                    botState = BotState.RESET_PATTERN;
                }
                break;

            case RESET_PATTERN:
                println("Resetting pattern sequence...");
                if (Interfaces.isOpen(1184)) {
                    println("Handling reset dialog...");
                    handleDialog();
                    Execution.delayUntil(600, () -> !Interfaces.isOpen(1184));
                }

                currentCoordIndex = 0;
                reverseOrder = !reverseOrder;

                if (reverseOrder) {
                    println("Reversing pattern order for next run");
                    List<Coordinate> reversed = new ArrayList<>(crossPattern);
                    java.util.Collections.reverse(reversed);
                    crossPattern = reversed;
                } else {
                    println("Restoring original pattern order");
                    List<Coordinate> original = new ArrayList<>(crossPattern);
                    java.util.Collections.reverse(original);
                    crossPattern = original;
                }

                println("Resetting visited flags for all coordinates");
                for (Coordinate coord : crossPattern) {
                    coord.visited = false;
                }
                println("Pattern reset complete, returning to making fires");
                botState = BotState.MAKING_FIRES;
                break;

            case STOPPED:
                println("Bot has been stopped.");
                stopScript();
                break;
        }
    }

    private boolean isBoBInventoryFull() {
        boolean isFull = bobInventory.contains(MAGIC_LOGS_ID, 32);
        println("Checking BoB inventory: " + (isFull ? "full" : "not full"));
        return isFull;
    }

    private void handleMinigame() {
        println("Handling minigame state check...");
        if (botState != BotState.START_MINIGAME && botState != BotState.CLIMB_DOWN && !Interfaces.isOpen(1209)) {
            println("Conditions met for minigame restart, resetting state");
            botState = BotState.START_MINIGAME;
            coordsGenerated = false;
            currentCoordIndex = 0;
            crossPattern.clear();
            println("State reset complete for minigame");

        }
    }

    private void generateCrossPattern(int playerX, int playerY) {
        println("Generating cross pattern from position (" + playerX + ", " + playerY + ")");
        List<Coordinate> pattern = new ArrayList<>();

        println("Generating first half of cross pattern");
        for (int i = 0; i < 7; i++) {
            int offsetX = 6 - (i * 2);
            int offsetY = -6 - (i * 2);
            pattern.add(new Coordinate(playerX + offsetX, playerY + offsetY));
            println("Added coordinate at offset (" + offsetX + ", " + offsetY + ")");
        }

        println("Generating second half of cross pattern");
        for (int i = 0; i < 7; i++) {
            int offsetX = 6 - (i * 2);
            int offsetY = -18 + (i * 2);
            pattern.add(new Coordinate(playerX + offsetX, playerY + offsetY));
            println("Added coordinate at offset (" + offsetX + ", " + offsetY + ")");
        }

        crossPattern = pattern;
        println("Cross pattern generation complete with " + pattern.size() + " coordinates");
    }

    private void handleDialog() {
        println("Handling dialog interaction");
        Dialog.select();
        println("Dialog selection completed");
    }

    private void stopScript() {
        println("Executing script shutdown sequence");
        println("Final state: " + botState);
        println("Script stopped successfully");
        LoginManager.setAutoLogin(false);
        Hud.logout();
        this.setActive(false);
    }

    private void onChatMessage(ChatMessageEvent event) {
        String message = event.getMessage();
        println("Chat message received: " + message);

    }

    @Override
    public CoaezGraphicsContext getGraphicsContext() {
        return sgc;
    }

    public ScriptConfig getConfig() {
        return config;
    }

}
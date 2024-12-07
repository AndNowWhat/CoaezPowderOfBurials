package net.botwithus;

import net.botwithus.rs3.imgui.ImGui;
import net.botwithus.rs3.imgui.ImGuiWindowFlag;
import net.botwithus.rs3.script.ScriptConsole;
import net.botwithus.rs3.script.ScriptGraphicsContext;
import net.botwithus.rs3.script.config.ScriptConfig;

import static net.botwithus.rs3.script.ScriptConsole.println;

public class CoaezGraphicsContext extends ScriptGraphicsContext {

    private final CoaezPitchCanFM coaezPitchCanFM;
    private int lastPlayerPreset;

    public CoaezGraphicsContext(ScriptConsole scriptConsole,CoaezPitchCanFM coaezPitchCanFM) {
        super(scriptConsole);
        this.coaezPitchCanFM = coaezPitchCanFM;
        loadConfig();
        lastPlayerPreset = coaezPitchCanFM.getPlayerPresetNumber();
    }
    public boolean hasStateChanged() {
        boolean changed = false;

        if (lastPlayerPreset != coaezPitchCanFM.getPlayerPresetNumber()) {
            lastPlayerPreset = coaezPitchCanFM.getPlayerPresetNumber();
            changed = true;
        }

        return changed;
    }

    public void saveConfig() {
        ScriptConfig config = coaezPitchCanFM.getConfig();

        if (config != null) {
            config.addProperty("playerPreset", String.valueOf(coaezPitchCanFM.getPlayerPresetNumber()));
            config.addProperty("botState", coaezPitchCanFM.botState.toString());
            config.save();
        }
    }

    public void loadConfig() {
        ScriptConfig config = coaezPitchCanFM.getConfig();

        if (config != null) {
            config.load();

            String playerPresetValue = config.getProperty("playerPreset");
            if (playerPresetValue != null) {
                coaezPitchCanFM.setPlayerPresetNumber(Integer.parseInt(playerPresetValue));
            }

            String botStateValue = config.getProperty("botState");
            if (botStateValue != null) {
                coaezPitchCanFM.botState = CoaezPitchCanFM.BotState.valueOf(botStateValue);
            }

            println("Script state loaded.");
        }
    }

    @Override
    public void drawOverlay() {
    }

    public void drawSettings() {
        if (ImGui.Begin("Coaez PitchCan FM Settings", ImGuiWindowFlag.AlwaysAutoResize.getValue())) {
            ImGui.Text("Preset Settings");
            ImGui.Separator();
            int playerPreset = coaezPitchCanFM.getPlayerPresetNumber();
            coaezPitchCanFM.setPlayerPresetNumber(ImGui.InputInt("Player Inventory Preset", playerPreset));

            ImGui.Separator();
            ImGui.Text("Current State: " + coaezPitchCanFM.botState.toString());

            if (hasStateChanged()) {
                saveConfig();
            }

            ImGui.End();
        }
    }
}
package dev.reduxion.core;

import dev.reduxion.ui.UiScreen;
import java.nio.file.Path;
import java.util.List;

/** Everything the common code needs from a concrete game version. */
public interface Platform {
    String gameVersion();
    Path configDir();
    /** true on versions with item model definitions (1.21.4+). */
    boolean modernItems();
    void openScreen(UiScreen screen);
    void closeScreen();
    void reloadResources();
    boolean inWorld();
    /** Uploads (or replaces) a GUI image. */
    void image(String key, int w, int h, int[] argb);
    void dropImage(String key);
    /** Drops cached armor/trim textures. */
    void resetTextures();
    Held held();
    /**
     * The item on the entity the player is looking at — a player, mob, armor stand, item frame or item display.
     * Returns null when nothing suitable is under the crosshair. Reads only what the client already knows.
     */
    Held lookedAt();
    void toast(String title, String text);
    void click();
    boolean russian();
    List<String> itemIds();
    String clipboard();
    String keyName();
    /**
     * Gives the player the item a rule targets, with the matching components, so the replacement can be
     * seen at once. Returns a short status message (success or why it could not be given).
     */
    String giveItem(dev.reduxion.pack.Rule rule);

    /** What the server tells the client about the held item. Nothing from any resource pack. */
    final class Held {
        public String item = "";
        public Integer cmd;
        public List<String> cmdStrings = List.of();
        public List<Boolean> cmdFlags = List.of();
        public String itemModel;
        public String assetId;
        public String trimPattern, trimMaterial;
        public boolean armor;
    }
}

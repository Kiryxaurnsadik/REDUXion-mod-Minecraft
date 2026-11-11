package dev.reduxion.core;

/** Two-language strings: Russian when the game language is Russian, English otherwise. */
public final class L {
    private L() {}
    public static boolean ru = true;
    public static String t(String russian, String english) { return ru ? russian : english; }
}

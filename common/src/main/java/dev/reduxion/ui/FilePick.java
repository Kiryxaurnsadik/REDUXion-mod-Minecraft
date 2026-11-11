package dev.reduxion.ui;

import java.nio.file.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Native "open file" dialog (the same library the game uses). Returns null when cancelled or unavailable. */
final class FilePick {
    private FilePick() {}

    static Path open(String title, String description, String... patterns) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(patterns.length);
            for (String p : patterns) filters.put(stack.UTF8(p));
            filters.flip();
            String r = TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, description, false);
            if (r == null || r.isEmpty()) return null;
            Path p = Paths.get(r);
            return Files.isRegularFile(p) ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }
}

/*
 * This file is part of Hearthborn Client.
 *
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 *
 * Licensed under the GNU Lesser General Public License
 * version 3.0 (LGPL-3.0).
 *
 * See the COPYING file for details.
 */

package hearthborn.io;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Where the client keeps its local records: the {@code data} folder itself, with no extra level inside it.
 *
 * @author Jagy2014
 */
public final class DataDirectory {
    private static final Set<Path> moved = new HashSet<>();

    /** Records live directly in this folder. Earlier versions kept them one level deeper, in a hearthborn folder. */
    public static synchronized Path path() {
        Path directory = Paths.get(System.getProperty("hearthborn.data.directory", "data"));
        if(moved.add(directory.toAbsolutePath().normalize())) adopt(directory);
        return directory;
    }

    /** Moves records out of the old data/hearthborn folder. A name that already exists is left where it is. */
    private static void adopt(Path directory) {
        Path legacy = directory.resolve("hearthborn");
        if(!Files.isDirectory(legacy)) return;
        try(DirectoryStream<Path> entries = Files.newDirectoryStream(legacy)) {
            for(Path entry : entries) {
                Path target = directory.resolve(entry.getFileName().toString());
                if(Files.exists(target)) continue;
                Files.move(entry, target);
            }
        } catch(IOException e) {
            System.err.println("Could not move saved records out of " + legacy + ": " + e.getMessage());
            return;
        }
        try {
            Files.deleteIfExists(legacy.resolve(".DS_Store"));
            Files.deleteIfExists(legacy);
        } catch(IOException leftover) {
            // The folder still holds files that already exist one level up; leave them for the owner.
        }
    }

    private DataDirectory() {}
}

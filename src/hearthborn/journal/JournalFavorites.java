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

package hearthborn.journal;

import hearthborn.io.DataDirectory;
import hearthborn.io.LocalStore;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Local profile data, independent of the language, character and installed data snapshot. */
final class JournalFavorites {
    private static final String FAVORITES = "game-journal-favorites";

    static synchronized List<String> saveFavorite(String id, boolean saved) {
        Path directory = DataDirectory.path();
        // Serialize read-modify-write even if two client processes share the same profile.
        try {
            Files.createDirectories(directory);
            try(FileChannel channel = FileChannel.open(directory.resolve("game-journal.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
                ArrayList<String> ids = favorites();
                boolean changed = saved ? !ids.contains(id) && ids.add(id) : ids.remove(id);
                if(!changed) return ids;
                LocalStore.write(FAVORITES, ids);
                return favorites();
            }
        } catch(IOException error) {
            System.err.println("Could not update the game journal: " + error.getMessage());
            return favorites();
        }
    }

    static ArrayList<String> favorites() {
        Object saved = LocalStore.read(FAVORITES);
        Set<String> ids = new LinkedHashSet<>();
        if(saved instanceof List) {
            for(Object id : (List<?>)saved) {
                if(id instanceof String) ids.add((String)id);
            }
        }
        return new ArrayList<>(ids);
    }

    private JournalFavorites() {}
}

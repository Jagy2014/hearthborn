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

package hearthborn.gamedata;

import hearthborn.io.DataDirectory;
import hearthborn.io.DeveloperMode;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Process-wide knowledge snapshots. Neither startup nor readers wait for the network. */
public final class GameDataEngine {
    private static final String BUILTIN = "builtin/manifest.json";
    private static final CompletableFuture<GameDataRepository> initial = new CompletableFuture<>();
    private static volatile GameDataRepository latest;
    private static boolean started;

    public static synchronized void start() {
        if(started || !DeveloperMode.gameJournal()) return;
        started = true;
        Thread worker = new Thread(GameDataEngine::load, "Hearthborn game data");
        worker.setDaemon(true);
        worker.start();
    }

    /** Wait only for the first local snapshot; subsequent callers see the latest validated one. */
    public static CompletableFuture<GameDataRepository> snapshot() {
        if(!DeveloperMode.gameJournal()) return CompletableFuture.completedFuture(null);
        start();
        return initial.handle((local, error) -> {
            GameDataRepository repository = latest;
            if(repository != null) return repository;
            if(error != null) throw new CompletionException(error);
            return local;
        });
    }

    /** For consumers that can refresh when a new snapshot is ready. May be null at startup. */
    public static GameDataRepository current() {
        return latest;
    }

    private static void load() {
        GameDataCache cache = new GameDataCache(DataDirectory.path().resolve("game-data"));
        try {
            latest = cache.load();
        } catch(IOException error) {
            log(error);
        }
        try {
            if(latest == null) latest = bundled();
            initial.complete(latest);
        } catch(IOException error) {
            initial.completeExceptionally(error);
            log(error);
        }
        if(!Boolean.parseBoolean(System.getProperty("hearthborn.gamedata.updates", "true"))) return;
        try {
            latest = cache.update(new GitHubGameData(), latest);
        } catch(IOException error) {
            log(error);
        }
    }

    private static GameDataRepository bundled() throws IOException {
        URL manifest = GameDataEngine.class.getResource(BUILTIN);
        if(manifest == null) throw new IOException("Packaged hafen-data manifest is missing");
        try {
            URI location = manifest.toURI();
            if("file".equals(location.getScheme()))
                return GameDataRepository.load(Paths.get(location).getParent());
            if(!"jar".equals(location.getScheme()))
                throw new IOException("Unsupported packaged hafen-data location");
            String[] jar = location.toString().split("!/", 2);
            Path archive = Paths.get(URI.create(jar[0].substring("jar:".length())));
            FileSystem files = FileSystems.newFileSystem(archive, (ClassLoader)null);
            try {
                // Keep the read-only provider open so consumers can also open packaged artwork.
                return GameDataRepository.load(files.getPath("/" + jar[1]).getParent());
            } catch(IOException | RuntimeException error) {
                files.close();
                throw error;
            }
        } catch(URISyntaxException error) {
            throw new IOException("Invalid packaged hafen-data location", error);
        }
    }

    private static void log(IOException error) {
        if(DeveloperMode.enabled("debug-logs"))
            System.err.println("Hearthborn game data: " + error.getMessage());
    }

    private GameDataEngine() {}
}

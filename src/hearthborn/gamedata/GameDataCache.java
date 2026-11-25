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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Readers open immutable snapshots. Publishing atomically changes only the pointer file. */
final class GameDataCache {
    private static final String SHA = "[0-9a-f]{40}";
    private final Path root;
    private final Path pointer;
    private Snapshot loaded;

    interface Source {
        String head() throws IOException;
        DataManifest manifest(String commit) throws IOException;
        void archive(String commit, Path target) throws IOException;
    }

    private static final class Snapshot {
        final String name;
        final GameDataRepository repository;

        Snapshot(String name, GameDataRepository repository) {
            this.name = name;
            this.repository = repository;
        }

        boolean isCommit(String commit) {
            return name.startsWith("snapshot-" + commit + "-");
        }
    }

    GameDataCache(Path root) {
        this.root = root;
        this.pointer = root.resolve("current");
    }

    GameDataRepository load() throws IOException {
        loaded = read();
        return loaded == null ? null : loaded.repository;
    }

    GameDataRepository update(Source source, GameDataRepository visible) throws IOException {
        Files.createDirectories(root);
        try(FileChannel channel = FileChannel.open(root.resolve("update.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock()) {
            if(lock == null) return visible; // Another client owns the update.
            Snapshot active = read();
            GameDataRepository local = active == null ? visible : active.repository;
            try {
                String commit = DataValidator.matching(source.head(), "hafen-data commit", SHA);
                if(active != null && active.isCommit(commit)) return local;
                DataManifest manifest = source.manifest(commit);
                if(active != null && manifest.dataVersion <= local.dataVersion)
                    return local;
                return install(source, commit, manifest, active);
            } catch(IOException failure) {
                // A different process may have updated the cache before we got the lock.
                if(local != null && (visible == null || local.dataVersion > visible.dataVersion)) return local;
                throw failure;
            }
        } catch(OverlappingFileLockException busy) {
            return visible;
        }
    }

    private Snapshot read() throws IOException {
        if(!Files.isRegularFile(pointer)) return null;
        if(Files.size(pointer) > 512) return null;
        for(String name : Files.readAllLines(pointer, StandardCharsets.UTF_8)) {
            if(!name.matches("snapshot-" + SHA + "-[0-9]+")) continue;
            if(loaded != null && loaded.name.equals(name)) return loaded;
            try {
                return new Snapshot(name, GameDataRepository.load(root.resolve(name)));
            } catch(IOException invalid) {
                // Try the previous validated snapshot, then the packaged data.
            }
        }
        return null;
    }

    private GameDataRepository install(Source source, String commit, DataManifest manifest, Snapshot previous)
        throws IOException {
        Path archive = Files.createTempFile(root, "download-", ".zip");
        Path candidate = null;
        boolean installed = false;
        try {
            source.archive(commit, archive);
            candidate = Files.createTempDirectory(root, "snapshot-" + commit + "-");
            GameDataArchive.unpack(archive, candidate, commit);
            GameDataRepository repository = GameDataRepository.load(candidate);
            if(repository.dataVersion != manifest.dataVersion || repository.schemaVersion != manifest.schemaVersion)
                throw new IOException("hafen-data archive does not match its manifest");

            List<String> names = new ArrayList<>();
            names.add(candidate.getFileName().toString());
            if(previous != null) names.add(previous.name);
            publish(names);
            installed = true;
            return repository;
        } finally {
            discard(archive);
            if(!installed && candidate != null) discard(candidate);
        }
    }

    private void publish(List<String> names) throws IOException {
        Path temporary = Files.createTempFile(root, "current-", ".tmp");
        try {
            Files.write(temporary, names, StandardCharsets.UTF_8);
            Files.move(temporary, pointer, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            discard(temporary);
        }
    }

    private static void discard(Path path) {
        try {
            if(Files.isDirectory(path)) {
                List<Path> descendants;
                try(Stream<Path> walk = Files.walk(path)) {
                    descendants = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                }
                for(Path entry : descendants) Files.deleteIfExists(entry);
            } else {
                Files.deleteIfExists(path);
            }
        } catch(IOException ignored) {
            // Failure to clean staging files must not change which snapshot is active.
        }
    }
}

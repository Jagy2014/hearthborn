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

import haven.GameUI;
import haven.Coord;
import haven.GobIcon;
import haven.PUtils;
import haven.ResCache;
import haven.Resource;
import haven.Window;
import hearthborn.gamedata.Entity;
import hearthborn.gamedata.GameDataEngine;
import hearthborn.io.DeveloperMode;
import hearthborn.gamedata.GameDataRepository;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.imageio.ImageIO;

/** Journal entry point. The data engine synchronizes independently of the window. */
public final class GameJournal {
    private GameJournal() {}

    public static void open(GameUI gui) {
        open(gui, null, null);
    }

    /** Open a card by its stable data ID, independent of its display language. */
    public static void openEntry(GameUI gui, String id) {
        open(gui, null, id);
    }

    private static void open(GameUI gui, Lookup lookup, String id) {
        if(gui == null || !DeveloperMode.gameJournal()) return;
        JournalWindow window = gui.getchild(JournalWindow.class);
        if(window == null) {
            Lookup data = lookup == null ? new Lookup() : lookup;
            window = gui.add(new JournalWindow(data.initial, data::current));
            window.fit(true);
        }
        if(id != null) window.openEntry(id);
        // Windows above the map already use a higher z-order; raise the journal above them too.
        int z = window.z;
        for(Window sibling : gui.children(Window.class)) {
            if(sibling.visible()) z = Math.max(z, sibling.z);
        }
        window.z(z);
        window.raise();
        gui.setfocus(window);
    }

    /** One asynchronous lookup for an external browser, reusable across its selections. */
    public static final class Lookup {
        private final CompletableFuture<GameDataRepository> preview;
        private final CompletableFuture<GameDataRepository> initial;

        public Lookup() {
            if(!DeveloperMode.gameJournal()) {
                initial = preview = CompletableFuture.completedFuture(null);
                return;
            }
            preview = CompletableFuture.supplyAsync(() -> {
                Path directory = localDirectory();
                if(directory == null) return null;
                try {
                    return GameDataRepository.load(directory);
                } catch(IOException error) {
                    throw new CompletionException(error);
                }
            });
            initial = preview.thenCompose(local -> local == null
                ? GameDataEngine.snapshot() : CompletableFuture.completedFuture(local));
        }

        private GameDataRepository current() {
            if(!preview.isDone() || preview.isCompletedExceptionally()) return null;
            GameDataRepository local = preview.getNow(null);
            return local == null ? GameDataEngine.current() : local;
        }

        /** Unknown or ambiguous resources must not open an unrelated card. */
        public String entryId(String resource) {
            GameDataRepository repository = current();
            if(repository == null) return null;
            List<Entity> matches = repository.findByResource(resource);
            return matches.size() == 1 ? matches.get(0).id : null;
        }

        public void openResource(GameUI gui, String resource) {
            String id = entryId(resource);
            if(id != null) open(gui, this, id);
        }
    }

    private static Path localDirectory() {
        String override = System.getProperty("hearthborn.journal.directory");
        if(override != null) return override.trim().isEmpty() ? null : Paths.get(override);
        for(Path path : new Path[] {Paths.get("hafen-data"), Paths.get("..", "hafen-data"), Paths.get("..", "..", "hafen-data")}) {
            if(Files.isRegularFile(path.resolve("manifest.json"))) return path;
        }
        return null;
    }

    static final class CardImage {
        final BufferedImage pixels;
        final boolean icon;

        CardImage(BufferedImage pixels, boolean icon) {
            this.pixels = pixels;
            this.icon = icon;
        }
    }

    static CardImage image(GameDataRepository repo, Entity entity) {
        BufferedImage illustration = readImage(repo.image(entity));
        if(illustration != null) return new CardImage(illustration, false);
        BufferedImage icon = entryIcon(repo, entity);
        return icon == null ? null : new CardImage(icon, true);
    }

    static BufferedImage rowImage(GameDataRepository repo, Entity entity, int size) {
        BufferedImage image = entryIcon(repo, entity);
        if(image == null) image = readImage(repo.image(entity));
        if(image == null) return null;
        double scale = (double)size / Math.max(image.getWidth(), image.getHeight());
        Coord target = Coord.of(Math.max(1, (int)Math.round(scale * image.getWidth())),
            Math.max(1, (int)Math.round(scale * image.getHeight())));
        return target.x == image.getWidth() && target.y == image.getHeight() ? image
            : PUtils.convolve(image, target, GobIcon.filter);
    }

    private static BufferedImage entryIcon(GameDataRepository repo, Entity entity) {
        BufferedImage icon = readImage(repo.icon(entity));
        if(icon != null) return icon;
        for(String id : entity.resourceIds) {
            try {
                Resource.Image image = LocalImages.pool.loadwait(id).layer(Resource.imgc);
                if(image != null) return image.img;
            } catch(Resource.BadResourceException ignored) {
                // Uncached game resources must not trigger a download.
            }
        }
        return null;
    }

    private static BufferedImage readImage(Path path) {
        if(path == null) return null;
        try(InputStream input = Files.newInputStream(path)) {
            return ImageIO.read(input);
        } catch(IOException ignored) {
            return null;
        }
    }

    private static class LocalImages {
        static final Resource.Pool pool = create();

        private static Resource.Pool create() {
            Resource.Pool pool = new Resource.Pool(Resource.local());
            if(ResCache.global != null) pool.add(new Resource.CacheSource(ResCache.global));
            return pool;
        }
    }
}

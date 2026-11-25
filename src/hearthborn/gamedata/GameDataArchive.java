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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Extract only the files understood by the data engine, with limits before decoding. */
final class GameDataArchive {
    private static final long MAX_BYTES = 192L * 1024 * 1024;
    private static final long MAX_FILE = 16L * 1024 * 1024;
    private static final int MAX_ENTRIES = 65_000;
    private static final int MAX_PIXELS = 4 * 1024 * 1024;
    private static final String SLUG = "[a-z][a-z0-9-]*";

    static void unpack(Path archive, Path target, String commit) throws IOException {
        String prefix = "hafen-data-" + commit + "/";
        long remaining = MAX_BYTES;
        try(ZipFile zip = new ZipFile(archive.toFile())) {
            if(zip.size() > MAX_ENTRIES) throw new IOException("Too many files in hafen-data archive");
            for(Enumeration<? extends ZipEntry> files = zip.entries(); files.hasMoreElements();) {
                ZipEntry entry = files.nextElement();
                String name = entry.getName();
                if(!name.startsWith(prefix)) throw new IOException("Unexpected hafen-data archive root");
                String relative = name.substring(prefix.length());
                if(entry.isDirectory() || !datasetFile(relative)) continue;

                long size = entry.getSize();
                if(size < 0 || size > MAX_FILE || size > remaining)
                    throw new IOException("hafen-data extraction limit exceeded");
                remaining -= size;

                // Only portable allowlisted paths reach the filesystem. Never interpret ZIP metadata as a link.
                Path destination = target.resolve(relative);
                Files.createDirectories(destination.getParent());
                extract(zip, entry, destination);
                if(relative.endsWith(".png")) validateImage(destination);
            }
        }
    }

    private static boolean datasetFile(String path) throws IOException {
        if(path.equals("manifest.json")) return true;
        if((path.startsWith("data/") || path.startsWith("translations/")) && path.endsWith(".json")) {
            if(!path.matches("data/categories\\.json|(?:data|translations/(?:en|ru))/" + SLUG + "/" + SLUG + "\\.json")) {
                if(path.matches("translations/[a-z][a-z0-9-]*/" + SLUG + "/" + SLUG + "\\.json"))
                    return false; // Unknown locale.
                throw new IOException("Invalid hafen-data JSON path: " + path);
            }
            return true;
        }
        if(path.startsWith("assets/") && path.endsWith(".png")) {
            if(!path.matches("assets/(?:icons|images)/(?:" + SLUG + "/)*" + SLUG + "\\.png"))
                throw new IOException("Invalid hafen-data asset path: " + path);
            return true;
        }
        return false;
    }

    private static void extract(ZipFile zip, ZipEntry entry, Path target) throws IOException {
        CRC32 crc = new CRC32();
        long remaining = entry.getSize();
        try(InputStream in = zip.getInputStream(entry);
            OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            int count;
            while((count = in.read(buffer)) != -1) {
                remaining -= count;
                if(remaining < 0) throw new IOException("Incorrect archive entry size");
                crc.update(buffer, 0, count);
                out.write(buffer, 0, count);
            }
        }
        if(remaining != 0 || crc.getValue() != entry.getCrc())
            throw new IOException("Corrupt hafen-data archive entry: " + entry.getName());
    }

    private static void validateImage(Path path) throws IOException {
        ImageReader png = ImageIO.getImageReadersByFormatName("PNG").next();
        try(InputStream input = Files.newInputStream(path);
            MemoryCacheImageInputStream image = new MemoryCacheImageInputStream(input)) {
            png.setInput(image, true, true);
            if((long)png.getWidth(0) * png.getHeight(0) > MAX_PIXELS)
                throw new IOException("hafen-data image is too large: " + path.getFileName());
            BufferedImage decoded = png.read(0);
            decoded.flush();
        } finally {
            png.dispose();
        }
    }

    private GameDataArchive() {}
}

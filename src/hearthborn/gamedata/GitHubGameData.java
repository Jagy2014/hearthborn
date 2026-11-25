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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only access to the public hafen-data repository. */
final class GitHubGameData implements GameDataCache.Source {
    private static final String REPO = "Jagy2014/hafen-data";

    public String head() throws IOException {
        return text("https://api.github.com/repos/" + REPO + "/commits/main", "application/vnd.github.sha", 128)
            .trim();
    }

    public DataManifest manifest(String commit) throws IOException {
        String json = text("https://raw.githubusercontent.com/" + REPO + "/" + commit + "/manifest.json", null, 4096);
        return DataManifest.parse(JsonReader.read(new StringReader(json)));
    }

    public void archive(String commit, Path target) throws IOException {
        try(OutputStream output = Files.newOutputStream(target)) {
            fetch("https://codeload.github.com/" + REPO + "/zip/" + commit, null, output, 64 * 1024 * 1024);
        }
    }

    private static String text(String address, String accept, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        fetch(address, accept, output, limit);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void fetch(String address, String accept, OutputStream target, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)URI.create(address).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "Hearthborn-Game-Data");
        if(accept != null) connection.setRequestProperty("Accept", accept);
        try {
            int status = connection.getResponseCode();
            if(status != HttpURLConnection.HTTP_OK) throw new IOException("hafen-data: HTTP " + status);
            if(connection.getContentLengthLong() > limit) throw new IOException("hafen-data response is too large");
            try(InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                long deadline = System.nanoTime() + 45_000_000_000L;
                int count;
                while((count = input.read(buffer)) != -1) {
                    limit -= count;
                    if(limit < 0) throw new IOException("hafen-data response is too large");
                    if(System.nanoTime() - deadline > 0) throw new IOException("hafen-data download timed out");
                    target.write(buffer, 0, count);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}

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

package hearthborn.assets;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Access to authored resources copied from the root assets directory.
 *
 * @author Jagy2014
 */
public final class Assets {
    private static final String ROOT = "/hearthborn/assets/";

    public static String path(String relative) {
        return ROOT + relative;
    }

    public static InputStream open(String relative) throws IOException {
        InputStream stream = Assets.class.getResourceAsStream(path(relative));
        if(stream == null)
            throw new FileNotFoundException(path(relative));
        return stream;
    }

    public static BufferedImage image(String relative) throws IOException {
        try(InputStream stream = open(relative)) {
            BufferedImage image = ImageIO.read(stream);
            if(image == null)
                throw new IOException("Invalid image: " + path(relative));
            return image;
        }
    }

    private Assets() {}
}

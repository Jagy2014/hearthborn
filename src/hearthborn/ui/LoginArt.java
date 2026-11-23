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

package hearthborn.ui;

import haven.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Artwork for sign-in and character selection.
 *
 * @author Jagy2014
 */
public final class LoginArt {
    private static final Plate characters = new Plate("/hearthborn/assets/icons/screens/characters.png");

    private LoginArt() {}

    /** The visible painting excludes letterboxing on wide or tall displays. */
    public static Area bounds(Coord screen) {
        return bounds(screen, characters);
    }

    public static Area characterBounds(Coord screen) {
        return bounds(screen, characters);
    }

    private static Area bounds(Coord screen, Plate plate) {
        BufferedImage picture=plate.picture;
        Coord size=picture==null?screen:fit(Coord.of(picture.getWidth(),picture.getHeight()),screen);
        return Area.sized(screen.sub(size).div(2),size);
    }

    public static void draw(GOut g, Coord sz) {
        draw(g, sz, characters);
    }

    public static void drawCharacters(GOut g, Coord sz) {
        draw(g, sz, characters);
    }

    public static void drawLoading(GOut g, Coord screen) {
        Area artwork=characterBounds(screen);
        g.aimage(LoadingCaption.texture(),artwork.ul.add(artwork.sz().div(2)),.5,.5);
    }

    private static void draw(GOut g, Coord sz, Plate plate) {
        g.chcolor(0, 0, 0, 255);
        g.frect(Coord.z, sz);
        g.chcolor();
        Tex tex = plate.fitted(sz);
        if(tex != null)
            g.image(tex, sz.sub(tex.sz()).div(2));
    }

    private static final class Plate {
        final BufferedImage picture;
        Tex fitted;
        Coord fittedTo = Coord.z;

        Plate(String path) {
            picture = load(path);
        }

        Tex fitted(Coord bounds) {
            if(picture == null || bounds.x < 1 || bounds.y < 1)
                return null;
            Coord box = fit(Coord.of(picture.getWidth(), picture.getHeight()), bounds);
            if(fitted != null && box.equals(fittedTo))
                return fitted;
            if(fitted != null)
                fitted.dispose();
            BufferedImage scaled = new BufferedImage(box.x, box.y, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(picture, 0, 0, box.x, box.y, null);
            graphics.dispose();
            fitted = new TexI(scaled);
            fittedTo = box;
            return fitted;
        }
    }

    private static BufferedImage load(String path) {
        try(InputStream in = LoginArt.class.getResourceAsStream(path)) {
            if(in == null)
                return null;
            return ImageIO.read(in);
        } catch(Exception e) {
            System.err.println("Could not load Hearthborn login image: " + e.getMessage());
            return null;
        }
    }

    private static Coord fit(Coord image, Coord bounds) {
        double scale = Math.min(bounds.x / (double)image.x, bounds.y / (double)image.y);
        return Coord.of(Math.max(1, (int)Math.round(image.x * scale)), Math.max(1, (int)Math.round(image.y * scale)));
    }
}

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
import hearthborn.L10n;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Clock hover: game date, real time until the next boundary, and where you stand.
 *
 * @author Jagy2014
 */
public final class CalendarInfo {
    private static final String[] SEASONS = {"Spring","Summer","Autumn","Winter"};
    private static final int[] DAYS = {30,105,30,15};
    private static final int YEAR = 180;
    /** Game-time daylight used for the countdown: 06:00 until 20:00. */
    static final int DAY_MINUTE = 6 * 60, NIGHT_MINUTE = 20 * 60;
    private static final String[] PHASES = {"New Moon","Waxing Crescent Moon","First Quarter Moon","Waxing Gibbous Moon",
        "Full Moon","Waning Gibbous Moon","Last Quarter Moon","Waning Crescent Moon"};
    private static final Color INK = new Color(236, 224, 196);
    private String previous = "", province = "-", realm = "-";
    private Tex tex;
    private FramedTip tip;

    public Object tooltip(UI ui, GameUI gui) {
        if(ui == null || ui.sess == null || ui.sess.glob == null || ui.sess.glob.ast == null) return null;
        remember(ui, gui);
        String value = text(ui.sess.glob.ast, WorldIdentity.name(ui.sess), null, province, realm);
        String corner = playersLine(ServerStats.players());
        String key = corner + "\n" + value;
        if(!key.equals(previous)) {
            if(tex != null) tex.dispose();
            tex = new TexI(lines(value, corner));
            tip = new FramedTip(tex);
            previous = key;
        }
        return tip;
    }

    public void dispose() {if(tex != null) tex.dispose();}

    /** Place the clock summary to the right of the pointer, or to the left when the right edge is too close. */
    public static Coord tooltipPosition(Coord pointer, Coord size, Coord screen) {
        Coord edge = UI.scale(16, 16);
        int gap = UI.scale(18);
        int right = pointer.x + gap;
        int x = right + size.x <= screen.x - edge.x ? right : pointer.x - size.x - gap;
        int y = pointer.y - UI.scale(8);
        return Coord.of(Utils.clip(x, edge.x, Math.max(edge.x, screen.x - edge.x - size.x)),
            Utils.clip(y, edge.y, Math.max(edge.y, screen.y - edge.y - size.y)));
    }

    /** The tooltip body. The online count is {@link #playersLine}, drawn in the top right corner, and ping is shown elsewhere. */
    public static String text(Astronomy a, String world, String detail, String province, String realm) {
        if(a == null) return "";
        int season = season(a);
        int seconds = (int)Math.floor(a.dt * 86400.0);
        if(seconds < 0) seconds = 0;
        if(seconds > 86399) seconds = 86399;
        String clock = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        String date = String.format(Locale.ROOT, "%02d.%02d.%s", (int)Math.floor(a.md) + 1, (int)Math.floor(a.ym) + 1, year((int)Math.floor(a.years) + 1));
        String untilDay = L10n.format(a.night ? "%.1f h RL till day time" : "%.1f h RL till night time", boundaryHours(a));
        int next = (season + 1) % SEASONS.length;
        String untilSeason = L10n.format("%.1f RL till " + SEASONS[next], seasonRealDays(a));
        int phase = Math.floorMod((int)Math.round(a.mp * PHASES.length), PHASES.length);
        return worldLine(world, detail) + "\n" +
            L10n.format("Day %d (%s), %s (%s)", absoluteDay(a), date, clock, untilDay) + "\n" +
            L10n.format("%s, Day %d of %d (%s)", L10n.tr(SEASONS[season]), seasonDay(a), DAYS[season], untilSeason) + "\n" +
            L10n.tr(PHASES[phase]) + "\n" +
            "\n" +
            L10n.format("Province: %s", blank(province)) + "\n" +
            L10n.format("Realm: %s", blank(realm));
    }

    public static String playersLine(String players) {return L10n.format("Players Online: %s", blank(players));}

    static String worldLine(String world, String detail) {
        boolean named = present(world);
        boolean extra = present(detail) && !detail.equals(world);
        if(named && extra) return world + " / " + detail;
        if(named) return world;
        if(present(detail)) return detail;
        return "-";
    }

    static int absoluteDay(Astronomy a) {
        int season = season(a);
        int before = 0;
        for(int i = 0; i < season; i++) before += DAYS[i];
        return (int)Math.floor(a.years) * YEAR + before + seasonIndex(a);
    }

    static int seasonDay(Astronomy a) {
        int season = season(a);
        return Math.min(DAYS[season], seasonIndex(a) + 1);
    }

    static double seasonRealDays(Astronomy a) {
        double progress = Math.max(0, Math.min(1, a.sp));
        return (1.0 - progress) * DAYS[season(a)] / StudyProgress.GAME_SECONDS_PER_REAL_SECOND;
    }

    static double boundaryHours(Astronomy a) {
        int minute = (int)Math.floor(a.dt * 24 * 60);
        int second = (int)Math.floor(a.dt * 86400.0);
        if(second < 0) second = 0;
        second %= 60;
        int target = a.night ? DAY_MINUTE : NIGHT_MINUTE;
        int left = target - minute;
        if(left < 0) left += 24 * 60;
        double gameSeconds = left * 60.0 - second;
        if(gameSeconds < 0) gameSeconds += 86400;
        return gameSeconds / StudyProgress.GAME_SECONDS_PER_REAL_SECOND / 3600.0;
    }

    /** Real hours until the next dusk - the next time Star Gaze can start (again). */
    public static double hoursUntilNextNightStart(Astronomy a) {
        if(a == null) return Double.POSITIVE_INFINITY;
        if(!a.night) return boundaryHours(a);
        double daySeconds = (NIGHT_MINUTE - DAY_MINUTE) * 60.0;
        return boundaryHours(a) + daySeconds / StudyProgress.GAME_SECONDS_PER_REAL_SECOND / 3600.0;
    }

    private static int season(Astronomy a) {return Math.max(0, Math.min(DAYS.length - 1, a.is));}

    private static int seasonIndex(Astronomy a) {
        int season = season(a);
        return (int)Math.floor(Math.max(0, Math.min(0.999999, a.sp)) * DAYS[season]);
    }

    private static String year(int year) {
        return year < 100 ? String.format(Locale.ROOT, "%02d", year) : Integer.toString(year);
    }

    private static boolean present(String value) {return value != null && !value.trim().isEmpty();}
    private static String blank(String value) {return present(value) ? value : "-";}

    private void remember(UI ui, GameUI gui) {
        String named = PlaceNames.province(ui);
        if(present(named)) province = named;
        String claimed = PlaceNames.realm(ui);
        if(present(claimed)) realm = claimed;
        else try {
            String found = realm(gui);
            if(found != null && !found.isEmpty()) realm = found;
        } catch(Loading ignored) {}
    }

    private static String realm(GameUI gui) {
        if(gui == null || gui.buffs == null) return null;
        String found = "";
        for(Buff buff : gui.buffs.children(Buff.class)) {
            Resource res = buff.resource();
            if(res == null || !"gfx/hud/buffs/realm".equals(res.name)) continue;
            Resource.Tooltip base = res.layer(Resource.tooltip);
            for(ItemInfo info : buff.info()) {
                if(!(info instanceof ItemInfo.Name)) continue;
                String original = ((ItemInfo.Name)info).str.text;
                if(!present(original)) continue;
                if(base == null || !original.equals(base.original)) return original;
            }
            if(base != null && present(base.original) && !base.original.equalsIgnoreCase("Realm"))
                return base.original;
            found = "";
        }
        return found;
    }

    /** Body lines on the left, and the online count in the top right corner beside the first line. */
    private static BufferedImage lines(String value, String corner) {
        Text.Foundry font = new Text.Foundry(Text.serif, 10).aa(true);
        String[] rows = value.split("\\n", -1);
        Text[] rendered = new Text[rows.length];
        int width = 1;
        for(int i = 0; i < rows.length; i++) {
            rendered[i] = font.render(rows[i].isEmpty() ? " " : rows[i], INK);
            width = Math.max(width, rendered[i].sz().x);
        }
        Text players = font.render(corner, INK);
        int gap = UI.scale(18);
        width = Math.max(width, rendered[0].sz().x + gap + players.sz().x);
        BufferedImage image = TexI.mkbuf(Coord.of(width, Math.max(1, font.height() * rows.length)));
        Graphics g = image.getGraphics();
        int y = 0;
        for(Text row : rendered) {
            g.drawImage(row.img, 0, y, null);
            y += font.height();
        }
        g.drawImage(players.img, width - players.sz().x, 0, null);
        g.dispose();
        return image;
    }
}

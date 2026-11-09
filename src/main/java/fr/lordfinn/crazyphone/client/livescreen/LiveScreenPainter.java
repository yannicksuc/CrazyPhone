package fr.lordfinn.crazyphone.client.livescreen;

import fr.lordfinn.crazyphone.utils.PhoneScreenApp;

/**
 * Paints a {@link LiveScreenView} into a tiny ARGB pixel grid, in the phone's own pixel-art style: the same
 * pale cyan screen, slate ink and yellow header as the phone GUI and the item textures.
 *
 * The grid is {@value #WIDTH}x{@value #HEIGHT}: exactly 4x4 canvas pixels per texel of the phone's screen
 * on its 32px item texture (8x12 texels, see crazyphone.png), so the live screen lines up with the item's
 * own pixel grid and stays crisp. The two bottom corners are the bezel's rounded corners on the texture and
 * are left transparent (0), which also means "not drawn".
 *
 * Pure Java on purpose (no Minecraft class at all): it can't fail on a version difference, and it is cheap
 * to run again whenever the view changes (a few thousand int writes).
 */
public final class LiveScreenPainter {
    public static final int WIDTH = 32;
    public static final int HEIGHT = 48;

    // Palette, taken from the phone's own textures (phone-background.png, crazyphone.png,
    // crazyphone-header-background.png, CrazyPhoneColors).
    // Screen bands: the phone GUI's own screen colours (phone-background.png), not a brighter cyan.
    static final int SCREEN = 0xFFC2F1F3;
    static final int SCREEN_MID = 0xFFB4EDF0;
    static final int SCREEN_DEEP = 0xFFA9E8EC;
    static final int GLARE = 0xFFE0F6F5;
    static final int TEAL = 0xFF58D8D1;
    static final int NIGHT_TOP = 0xFF1E2A44;
    static final int MOON_CRATER = 0xFF96B9C8;
    static final int CLOCK_SHADE_NIGHT = 0xFF0C1222;
    static final int CLOCK_SHADE_TWILIGHT = 0xFFE59A62;
    static final int NIGHT_MID = 0xFF19223A;
    static final int NIGHT_BOTTOM = 0xFF14161A;
    static final int DUSK_TOP = 0xFFFFB86B;
    static final int DUSK_MID = 0xFFD98A7E;
    static final int DUSK_BOTTOM = 0xFF7E5BA8;
    static final int INK = 0xFF3A4456;
    static final int INK_SOFT = 0xFF4C5974;
    static final int LINE = 0xFF9FB4C4;
    static final int WHITE = 0xFFFFFFFF;
    static final int YELLOW = 0xFFFFD100;
    static final int YELLOW_SHADE = 0xFFE0A800;
    static final int ACCENT = 0xFFFFC107;
    // Message bubbles, as in the conversation screen (MessageWidget): mine blue, the others near white.
    static final int BUBBLE_MINE = 0xFF0084FF;
    static final int BUBBLE_MINE_LINE = 0xFF8CC8FF;
    static final int GREEN = 0xFF2ECC71;
    static final int GREEN_DARK = 0xFF1E9E55;
    static final int GREEN_LIGHT = 0xFFA8EFC4;
    static final int RED = 0xFFE74C3C;
    static final int BLUE = 0xFF4A90E2;
    static final int SKY = 0xFF7EC8F0;
    static final int GRASS = 0xFF72C266;
    static final int VIEWFINDER = 0xFF101318;
    static final int VIEW_SKY = 0xFF1C2B40;
    static final int VIEW_HILL = 0xFF1D3326;
    static final int SKIN = 0xFFE0AC69;
    static final int SKIN_SHADE = 0xFFC48A4F;
    static final int HAIR = 0xFF6B4A2B;
    static final int SHIRT = 0xFF2BB5B5;

    // Free columns kept on each side of a line of text, and the width of the "..." drawn after a cut name.
    private static final int TEXT_MARGIN = 1;
    private static final int ELLIPSIS_WIDTH = 5;
    // 3x5 font: digits, A-Z and a few symbols. Each glyph is 5 rows of 3 bits, top row first.
    private static final String SMALL_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-_:.+?!";
    private static final String[] SMALL = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111",
            "010101111101101", "110101110101110", "011100100100011", "110101101101110", "111100110100111",
            "111100110100100", "011100101101011", "101101111101101", "111010010010111", "001001001101010",
            "101101110101101", "100100100100111", "101111111101101", "110101101101101", "010101101101010",
            "110101110100100", "010101101110011", "110101110101101", "011100010001110", "111010010010010",
            "101101101101111", "101101101101010", "101101111111101", "101101010101101", "101101010010010",
            "111001010100111", "000000111000000", "000000000000111", "000010000010000", "000000000000010",
            "000010111010000", "111001011000010", "010010010000010"
    };
    // 4x7 font for the big clock / call timer digits, 7 rows of 4 bits.
    private static final String[] BIG = {
            "0110100110011001100110010110", "0010011000100010001000100111", "0110100100010010010010001111",
            "1110000100010110000100011110", "1001100110011111000100010001", "1111100011100001000110010110",
            "0110100010001110100110010110", "1111000100100010010001000100", "0110100110010110100110010110",
            "0110100110010111000100010110"
    };

    // 7x7 icons ('#' = drawn), used in the app header and scaled up elsewhere.
    // 8 x 8 (even, like the 32 px screen and the round buttons it sits in), symmetric on its diagonal.
    private static final String[] ICON_PHONE = {
            ".##.....",
            "###.....",
            "###.....",
            "##......",
            ".##.....",
            "..##.###",
            "...#####",
            "....###."};
    private static final String[] ICON_HOME = {
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            ".##.##.",
            ".##.##.",
            "......."};
    private static final String[] ICON_PERSON = {
            "..###..",
            "..###..",
            "..###..",
            ".......",
            ".#####.",
            "#######",
            "#######"};
    private static final String[] ICON_CHAT = {
            "#######",
            "#.....#",
            "#.###.#",
            "#.....#",
            "#######",
            ".##....",
            ".#....."};
    // Shutter button on the top right, like a real camera; the body (8 px) is what gets centered.
    private static final String[] ICON_CAMERA = {
            ".....##.",
            "########",
            "##....##",
            "##.##.##",
            "##....##",
            "########"};
    // A framed landscape: a 2 x 2 sun and a mountain, 8 x 7.
    private static final String[] ICON_PHOTO = {
            "########",
            "#......#",
            "#...##.#",
            "#...##.#",
            "#.#....#",
            "####..##",
            "########"};
    private static final String[] ICON_LOCK = {
            "..###..",
            ".#...#.",
            ".#...#.",
            "#######",
            "###.###",
            "###.###",
            "#######"};
    // 8 x 8: teeth on all four sides and a round hole, so it reads as a gear (the 7 px one widened looked
    // like a rectangle).
    private static final String[] ICON_GEAR = {
            ".#.##.#.",
            "########",
            ".##..##.",
            "##....##",
            "##....##",
            ".##..##.",
            "########",
            ".#.##.#."};
    // A ballot box: the rectangular voting paper going into the slot, 8 x 8.
    private static final String[] ICON_BALLOT = {
            "..####..",
            "..#..#..",
            "..#..#..",
            "##....##",
            "########",
            "#......#",
            "#......#",
            "########"};
    private static final String[] ICON_DOTS = {
            "##.##.##",
            "##.##.##"};
    // Minecraft's square moon with its craters (c), in the sun's 6 x 6 shape with the corners cut.
    private static final String[] MOON = {
            ".####.",
            "#c####",
            "####c#",
            "##cc##",
            "##cc##",
            ".####."};
    private static final String[] SIGN_ANSWER = {
            "##....",
            "##....",
            "##....",
            ".##...",
            "..####",
            "...###"};
    private static final String[] SIGN_HANG_UP = {
            "######",
            "##..##"};
    private static final String[] ICON_ENVELOPE = {
            "#######",
            "##...##",
            "#.#.#.#",
            "#..#..#",
            "#######"};
    // Generic 8x8 Minecraft-style face for call tiles: deliberately nobody's real skin (privacy, and no
    // texture read-back needed). h = hair, s = skin, d = skin shade, e = eye, m = mouth.
    private static final String[] FACE = {
            "hhhhhhhh",
            "hhhhhhhh",
            "hssssssh",
            "seessees",
            "ssssssss",
            "sssddsss",
            "ssmmmmss",
            "ssssssss"};

    private final int[] px = new int[WIDTH * HEIGHT];

    private LiveScreenPainter() {
    }

    /** Paints {@code view} into a fresh grid (row-major, ARGB, 0 = transparent / not drawn). */
    public static int[] paint(LiveScreenView view) {
        LiveScreenPainter p = new LiveScreenPainter();
        switch (view.mode()) {
            case LOCK -> p.lock(view);
            case RINGING -> p.ringing(view);
            case DIALING -> p.dialing(view);
            case IN_CALL -> p.inCall(view);
            case CAMERA -> p.camera(view);
            case APP -> p.app(view);
        }
        p.bezelCorners();
        return p.px;
    }

    // ---- screens --------------------------------------------------------------------------------------

    private void lock(LiveScreenView v) {
        wallpaper(v.dayPhase());
        // Dark text on the light day and dusk wallpapers, light text on the night one.
        int ink = v.dayPhase() == LiveScreenView.NIGHT ? SCREEN : INK;
        int soft = v.dayPhase() == LiveScreenView.NIGHT ? LINE : INK_SOFT;
        statusBar(soft);
        int minutes = Math.floorMod(v.clockMinutes(), 24 * 60);
        String clock = two(minutes / 60) + ":" + two(minutes % 60);
        int clockY = 9;
        int width = bigWidth(clock);
        int x = (WIDTH - width) / 2;
        // The same 1 px shade under the clock on every wallpaper (it was only drawn by day), straight below so
        // the clock stays centered.
        int shade = switch (v.dayPhase()) {
            case LiveScreenView.NIGHT -> CLOCK_SHADE_NIGHT;
            case LiveScreenView.TWILIGHT -> CLOCK_SHADE_TWILIGHT;
            default -> TEAL;
        };
        bigText(clock, x, clockY + 1, shade);
        bigText(clock, x, clockY, ink);
        // Sun or moon under the clock.
        if (v.dayPhase() == LiveScreenView.NIGHT) {
            // A crescent in the same 6 x 6 box as the sun (columns 13 to 18).
            for (int row = 0; row < MOON.length; row++)
                for (int col = 0; col < MOON[row].length(); col++) {
                    char c = MOON[row].charAt(col);
                    if (c != '.')
                        px(13 + col, 18 + row, c == 'c' ? MOON_CRATER : SCREEN);
                }
        } else {
            int sun = v.dayPhase() == LiveScreenView.DAY ? YELLOW : ACCENT;
            // A 6 px disc (corners cut), centered on the screen's middle (columns 13 to 18).
            roundRect(13, 18, 6, 6, sun);
        }
        if (v.unread() > 0) {
            String count = v.unread() > 9 ? "9+" : Integer.toString(v.unread());
            int w = 7 + 2 + smallWidth(count) + 2 + 2;
            int bx = (WIDTH - w) / 2;
            int by = 28;
            roundRect(bx, by, w, 9, YELLOW);
            hline(bx + 1, by + 8, w - 2, YELLOW_SHADE);
            icon(ICON_ENVELOPE, bx + 2, by + 2, INK);
            smallText(count, bx + 2 + 7 + 2, by + 2, INK);
        }
        homeBar(soft);
    }

    private void ringing(LiveScreenView v) {
        gradient(GREEN_LIGHT, SCREEN, SCREEN);
        int cx = 16, cy = 16;
        // Expanding rings, one step per animation frame.
        int r = 9 + v.frame() % 3;
        ringOutline(cx, cy, r, v.frame() % 3 == 2 ? SCREEN_DEEP : TEAL);
        disc(cx, cy, 8, GREEN);
        // Always centered: the growing rings show it rings (the icon used to shake 1 px left and right).
        icon(ICON_PHONE, cx - 4, cy - 4, WHITE);
        // Rows: rings down to 27, the name 28-32, the buttons 34-43.
        if (!v.caller().isEmpty())
            fittedText(v.caller(), 28, INK);
        else
            dots(29, v.frame(), INK_SOFT);
        // Mirror images of each other on the screen (columns 4-13 and 18-27).
        callButton(9, 39, RED, false);
        callButton(23, 39, GREEN, true);
        homeBar(INK_SOFT);
    }

    private void dialing(LiveScreenView v) {
        gradient(SCREEN, SCREEN_MID, SCREEN_DEEP);
        int cx = 16, cy = 15;
        disc(cx, cy, 8, TEAL);
        icon(ICON_PHONE, cx - 4, cy - 4, WHITE);
        // Rows: disc 7-22, the name 24-28, the dots 30-32, the button 34-43, the home bar 45: one free row
        // between each.
        if (!v.caller().isEmpty())
            fittedText(v.caller(), 24, INK);
        dots(v.caller().isEmpty() ? 27 : 31, v.frame(), INK_SOFT);
        callButton(16, 39, RED, false);
        homeBar(INK_SOFT);
    }

    private void inCall(LiveScreenView v) {
        gradient(SCREEN, SCREEN_MID, SCREEN_DEEP);
        header(GREEN, GREEN_DARK, ICON_PHONE, WHITE);
        String timer;
        if (v.callSeconds() < 0) {
            timer = "--:--";
        } else if (v.callSeconds() < 3600) {
            timer = two(v.callSeconds() / 60) + ":" + two(v.callSeconds() % 60);
        } else {
            int hours = Math.min(9, v.callSeconds() / 3600);
            timer = hours + ":" + two((v.callSeconds() / 60) % 60);
        }
        if (v.callSeconds() < 0)
            smallText(timer, (WIDTH - smallWidth(timer)) / 2, 12, INK);
        else
            bigText(timer, (WIDTH - bigWidth(timer)) / 2, 10, INK);
        // At most 3 faces fit (3 x 8 px + 2 gaps of 2 px = columns 2-29); more people show as "+N" below them.
        int people = Math.max(1, v.participants());
        int n = Math.min(3, people);
        int tile = 8, gap = 2;
        int total = n * tile + (n - 1) * gap;
        int tx = (WIDTH - total) / 2;
        for (int i = 0; i < n; i++)
            face(i, tx + i * (tile + gap), 18);
        if (people > 3) {
            // Rows 28-32, where the voice bars would be.
            String more = "+" + Math.min(99, people - 3);
            smallText(more, (WIDTH - smallWidth(more) + 1) / 2, 28, INK_SOFT);
        } else {
            // Voice level bars, gently animated: rows 28-32, between the faces (rows 18-26) and the button
            // (rows 34-43), one free row on each side. Five 2 px bars, 1 px apart: columns 9-22, centered.
            int[] heights = {2, 4, 3, 5, 2};
            for (int i = 0; i < 5; i++) {
                int h = heights[(i + v.frame()) % 5];
                rect(9 + i * 3, 33 - h, 2, h, TEAL);
            }
        }
        callButton(16, 39, RED, false);
        homeBar(INK_SOFT);
    }

    private void camera(LiveScreenView v) {
        fill(VIEWFINDER);
        // A dim, generic scene (sky and a hill) so it reads as a viewfinder, never the real image.
        rect(2, 8, 28, 20, VIEW_SKY);
        for (int x = 2; x < 30; x++) {
            int top = 22 + (int) Math.round(3 * Math.sin((x + 4) / 5.0));
            rect(x, top, 1, 36 - top, VIEW_HILL);
        }
        rect(2, 28, 28, 8, VIEW_HILL);
        // Corner brackets, 2 px inside the picture (columns 2-29, rows 8-35) on all four sides.
        int l = 4, t = 10, rr = 27, b = 33;
        hline(l, t, 4, WHITE);
        vline(l, t, 4, WHITE);
        hline(rr - 3, t, 4, WHITE);
        vline(rr, t, 4, WHITE);
        hline(l, b, 4, WHITE);
        vline(l, b - 3, 4, WHITE);
        hline(rr - 3, b, 4, WHITE);
        vline(rr, b - 3, 4, WHITE);
        // Focus cross in the middle of the picture (rows 8-35: center between rows 21 and 22).
        focusCross(21, GLARE);
        // Recording dot (blinking) and "REC", centered as one group: dot 9-10, text 12-22.
        if (v.frame() % 2 == 0)
            rect(9, 2, 2, 2, RED);
        smallText("REC", 12, 1, v.frame() % 2 == 0 ? WHITE : LINE);
        // Shutter button.
        disc(16, 41, 3, WHITE);
        disc(16, 41, 2, LINE);
        rect(15, 40, 2, 2, WHITE);
        // The home bar is on every screen (rows 38-43 for the shutter, the bar on row 45).
        homeBar(LINE);
    }

    private void app(LiveScreenView v) {
        gradient(SCREEN, SCREEN_MID, SCREEN_DEEP);
        PhoneScreenApp app = v.app();
        header(YELLOW, YELLOW_SHADE, iconFor(app), INK);
        int f = v.frame();
        switch (app) {
            case HOME -> {
                appTile(3, 11, ACCENT, ICON_PERSON, INK);
                appTile(18, 11, TEAL, ICON_CAMERA, INK);
                appTile(3, 25, BLUE, ICON_PHOTO, WHITE);
                appTile(18, 25, GREEN, ICON_CHAT, WHITE);
            }
            case CONTACTS -> {
                int[] colors = {ACCENT, TEAL, BLUE, GREEN};
                for (int i = 0; i < 4; i++) {
                    int y = 11 + i * 8;
                    roundRect(3, y, 6, 6, colors[i]);
                    hline(11, y + 1, 14 - (i % 2) * 4, INK_SOFT);
                    hline(11, y + 3, 9 + (i % 3) * 2, LINE);
                }
            }
            case CONVERSATION -> {
                bubble(2, 11, 16, false);
                bubble(14, 18, 16, true);
                bubble(2, 25, 12, false);
                // "Someone is typing": the last bubble's dots.
                roundRect(2, 32, 13, 7, WHITE);
                for (int i = 0; i < 3; i++)
                    rect(4 + i * 3, 35 - (i == f % 3 ? 1 : 0), 2, 2, INK_SOFT);
                hline(2, 41, 28, WHITE);
            }
            case CAMERA -> {
                // A small viewfinder and the shutter button (it used to fall back to text lines).
                rect(3, 11, 26, 24, VIEWFINDER);
                rect(4, 12, 24, 13, VIEW_SKY);
                for (int x = 4; x < 28; x++) {
                    int top = 21 + (int) Math.round(2 * Math.sin((x + 2) / 4.0));
                    rect(x, top, 1, 34 - top, VIEW_HILL);
                }
                // Focus cross in the middle of the picture (rows 12-33).
                focusCross(22, GLARE);
                disc(16, 40, 3, WHITE);
                disc(16, 40, 2, LINE);
                rect(15, 39, 2, 2, f % 2 == 0 ? WHITE : LINE);
            }
            case PHOTOS -> {
                for (int row = 0; row < 3; row++)
                    for (int col = 0; col < 3; col++)
                        thumbnail(3 + col * 9, 11 + row * 10, (row * 3 + col + f) % 4);
            }
            case CALL -> {
                disc(16, 22, 8, GREEN);
                icon(ICON_PHONE, 12, 18, WHITE);
                dots(34, f, INK_SOFT);
            }
            case LOCKED -> {
                iconScaled(ICON_LOCK, 9, 12, 2, INK);
                for (int i = 0; i < 4; i++) {
                    boolean filled = i <= f % 4;
                    roundRect(5 + i * 6, 31, 4, 4, filled ? INK : LINE);
                }
            }
            case SETTINGS -> {
                iconScaled(ICON_GEAR, 8, 11, 2, INK_SOFT);
                hline(4, 29, 24, LINE);
                hline(4, 32, 18, LINE);
                roundRect(20, 35, 8, 4, f % 2 == 0 ? GREEN : LINE);
                hline(4, 36, 12, LINE);
            }
            case ELECTION -> {
                iconScaled(ICON_BALLOT, 8, 11, 2, INK);
                int[] votes = {14, 9, 18};
                int[] colors = {ACCENT, TEAL, BLUE};
                for (int i = 0; i < 3; i++)
                    rect(4, 29 + i * 4, Math.min(24, votes[i] + (i == f % 3 ? 1 : 0)), 2, colors[i]);
            }
            default -> {
                hline(4, 13, 24, LINE);
                hline(4, 17, 18, LINE);
                hline(4, 21, 22, LINE);
                hline(4, 25, 12, LINE);
            }
        }
        homeBar(INK_SOFT);
    }

    private static String[] iconFor(PhoneScreenApp app) {
        return switch (app) {
            case HOME -> ICON_HOME;
            case CONTACTS -> ICON_PERSON;
            case CONVERSATION -> ICON_CHAT;
            case CAMERA -> ICON_CAMERA;
            case PHOTOS -> ICON_PHOTO;
            case CALL -> ICON_PHONE;
            case LOCKED -> ICON_LOCK;
            case SETTINGS -> ICON_GEAR;
            case ELECTION -> ICON_BALLOT;
            default -> ICON_DOTS;
        };
    }

    // ---- building blocks -------------------------------------------------------------------------------

    private void wallpaper(int dayPhase) {
        switch (dayPhase) {
            case LiveScreenView.NIGHT -> {
                gradient(NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM);
                int[][] stars = {{4, 6}, {25, 4}, {9, 25}, {27, 22}, {20, 33}, {3, 38}};
                for (int[] s : stars)
                    px(s[0], s[1], SCREEN);
            }
            case LiveScreenView.TWILIGHT -> gradient(DUSK_TOP, DUSK_MID, DUSK_BOTTOM);
            default -> {
                gradient(SCREEN, SCREEN_MID, SCREEN_DEEP);
                // The diagonal glare band of the item texture, one texel (4 px) wide.
                for (int y = 0; y < HEIGHT; y++) {
                    // Straight diagonal, leaving the screen on the left (it used to be stopped at column 0
                    // and ran down the left edge, which looked like it turned).
                    hline(30 - y, y, 3, GLARE);
                }
            }
        }
    }

    /** Three horizontal bands rather than a smooth ramp: reads the same at this size, far fewer quads. */
    private void gradient(int top, int mid, int bottom) {
        rect(0, 0, WIDTH, 16, top);
        rect(0, 16, WIDTH, 16, mid);
        rect(0, 32, WIDTH, HEIGHT - 32, bottom);
    }

    private void header(int color, int shade, String[] icon, int iconColor) {
        rect(0, 0, WIDTH, 8, color);
        hline(0, 8, WIDTH, shade);
        String[] even = evenWidth(trim(icon));
        icon(even, (WIDTH - even[0].length()) / 2, (8 - even.length + 1) / 2, iconColor);
    }

    /**
     * The screen is 32 px wide, so an icon of odd width can never sit exactly in the middle (a 7 px icon was
     * half a pixel to the left, and it showed on the held phone). Doubles the middle column instead: the
     * icon keeps its symmetry and lands centered.
     */
    private static String[] evenWidth(String[] icon) {
        return icon[0].length() % 2 == 0 ? icon : widenMiddle(icon);
    }

    private void statusBar(int color) {
        // Signal bars, left.
        for (int i = 0; i < 3; i++)
            rect(2 + i * 2, 4 - i, 1, 1 + i, color);
        // Battery, right.
        rect(23, 2, 6, 3, color);
        rect(24, 3, 3, 1, color == INK_SOFT ? GREEN : GREEN_LIGHT);
        px(29, 3, color);
    }

    private void homeBar(int color) {
        hline(11, 45, 10, color);
    }

    /** 11 px tiles (odd, like the 7 px icons): the icon is centered on its own drawn pixels, so every
     * icon sits exactly in the middle of its tile. The two tile columns mirror each other on the screen. */
    private static final int TILE = 11;

    private void appTile(int x, int y, int color, String[] icon, int iconColor) {
        roundRect(x, y, TILE, TILE, color);
        hline(x + 1, y + TILE, TILE - 2, INK_SOFT);
        String[] fitted = oddSize(trim(icon));
        icon(fitted, x + (TILE - fitted[0].length()) / 2, y + (TILE - fitted.length) / 2, iconColor);
    }

    /** The icon without its empty border rows and columns. */
    private static String[] trim(String[] icon) {
        int top = icon.length, bottom = -1, left = icon[0].length(), right = -1;
        for (int r = 0; r < icon.length; r++)
            for (int c = 0; c < icon[r].length(); c++)
                if (icon[r].charAt(c) == '#') {
                    top = Math.min(top, r);
                    bottom = Math.max(bottom, r);
                    left = Math.min(left, c);
                    right = Math.max(right, c);
                }
        if (bottom < 0)
            return icon;
        String[] out = new String[bottom - top + 1];
        for (int r = top; r <= bottom; r++)
            out[r - top] = icon[r].substring(left, right + 1);
        return out;
    }

    /** Odd width and height, for centering in an odd-sized box: doubles the middle column / row if needed. */
    private static String[] oddSize(String[] icon) {
        String[] out = icon[0].length() % 2 == 0 ? widenMiddle(icon) : icon;
        if (out.length % 2 == 0) {
            int mid = out.length / 2;
            String[] taller = new String[out.length + 1];
            for (int r = 0, o = 0; r < out.length; r++) {
                taller[o++] = out[r];
                if (r == mid)
                    taller[o++] = out[r];
            }
            out = taller;
        }
        return out;
    }

    private static String[] widenMiddle(String[] icon) {
        int mid = icon[0].length() / 2;
        String[] out = new String[icon.length];
        for (int row = 0; row < icon.length; row++)
            out[row] = icon[row].substring(0, mid + 1) + icon[row].substring(mid);
        return out;
    }

    private void bubble(int x, int y, int w, boolean mine) {
        roundRect(x, y, w, 6, mine ? BUBBLE_MINE : WHITE);
        hline(x + 2, y + 2, w - 5, mine ? BUBBLE_MINE_LINE : LINE);
        hline(x + 2, y + 4, w / 2, mine ? BUBBLE_MINE_LINE : LINE);
    }

    private void thumbnail(int x, int y, int variant) {
        rect(x, y, 8, 8, WHITE);
        rect(x + 1, y + 1, 6, 4, variant == 2 ? DUSK_TOP : SKY);
        rect(x + 1, y + 5, 6, 2, GRASS);
        px(x + 2 + variant, y + 4, GRASS);
        px(x + 5, y + 2, YELLOW);
    }

    /** Call tile {@code index}: one of the game's default skins (see LiveScreenFaces), else the generic face. */
    private void face(int index, int x, int y) {
        int[] skin = LiveScreenFaces.face(index);
        if (skin != null) {
            for (int row = 0; row < 9; row++)
                for (int col = 0; col < 8; col++)
                    px(x + col, y + row, skin[row * 8 + col]);
            return;
        }
        for (int row = 0; row < 8; row++)
            for (int col = 0; col < 8; col++) {
                char c = FACE[row].charAt(col);
                int color = switch (c) {
                    case 'h' -> HAIR;
                    case 'e' -> INK;
                    case 'm' -> SKIN_SHADE;
                    case 'd' -> SKIN_SHADE;
                    default -> SKIN;
                };
                px(x + col, y + row, color);
            }
        hline(x, y + 8, 8, SHIRT);
    }

    /**
     * A focus cross: four 2 px thick arms around an empty center, symmetric on both axes. The center is
     * columns 15-16 (the middle of the screen) and rows cy, cy + 1.
     */
    private void focusCross(int cy, int color) {
        rect(11, cy, 3, 2, color);
        rect(18, cy, 3, 2, color);
        rect(15, cy - 4, 2, 3, color);
        rect(15, cy + 3, 2, 3, color);
    }

    /** A 10 px round button around (cx, cy) (pixels cx-5 to cx+4), its 6 px sign centered with a margin. */
    private void callButton(int cx, int cy, int color, boolean answer) {
        disc(cx, cy, 5, color);
        // A small handset to answer, the handset lying down to hang up, both 6 px wide, centered.
        String[] sign = answer ? SIGN_ANSWER : SIGN_HANG_UP;
        icon(sign, cx - 3, cy - sign.length / 2, WHITE);
    }

    private void dots(int y, int frame, int color) {
        for (int i = 0; i < 3; i++)
            rect(11 + i * 4, y - (i == frame % 3 ? 1 : 0), 2, 2, color);
    }

    /**
     * A name on one line, always whole characters inside the screen with a 1 px margin on each side: centered
     * if it fits, otherwise its first characters followed by an ellipsis. (It used to scroll one character per
     * frame through a fixed window, so most frames showed a name with its first letters missing, such as
     * "ORDFINN" for LORDFINN, with nothing telling it was cut.)
     */
    private void fittedText(String text, int y, int color) {
        String name = text.trim();
        int available = WIDTH - 2 * TEXT_MARGIN;
        int width = smallWidth(name);
        if (width <= available) {
            smallText(name, (WIDTH - width + 1) / 2, y, color);
            return;
        }
        // Characters are 4 px apart; keep room for a 1 px gap and the ellipsis.
        int keep = Math.max(1, (available - 1 - ELLIPSIS_WIDTH + 1) / 4);
        String head = name.substring(0, Math.min(keep, name.length())).stripTrailing();
        int total = smallWidth(head) + 1 + ELLIPSIS_WIDTH;
        int x = (WIDTH - total + 1) / 2;
        smallText(head, x, y, color);
        int ellipsisX = x + smallWidth(head) + 1;
        for (int i = 0; i < 3; i++)
            px(ellipsisX + i * 2, y + 4, color);
    }

    private void bezelCorners() {
        for (int y = HEIGHT - 4; y < HEIGHT; y++)
            for (int x = 0; x < 4; x++) {
                px[y * WIDTH + x] = 0;
                px[y * WIDTH + (WIDTH - 1 - x)] = 0;
            }
    }

    // ---- text ------------------------------------------------------------------------------------------

    static int smallWidth(String text) {
        return text.isEmpty() ? 0 : text.length() * 4 - 1;
    }

    private void smallText(String text, int x, int y, int color) {
        for (int i = 0; i < text.length(); i++) {
            int index = SMALL_CHARS.indexOf(Character.toUpperCase(text.charAt(i)));
            if (index < 0) {
                x += 4; // unknown or space: blank cell
                continue;
            }
            String bits = SMALL[index];
            for (int row = 0; row < 5; row++)
                for (int col = 0; col < 3; col++)
                    if (bits.charAt(row * 3 + col) == '1')
                        px(x + col, y + row, color);
            x += 4;
        }
    }

    static int bigWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++)
            width += Character.isDigit(text.charAt(i)) ? 5 : 2;
        return Math.max(0, width - 1);
    }

    private void bigText(String text, int x, int y, int color) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                String bits = BIG[c - '0'];
                for (int row = 0; row < 7; row++)
                    for (int col = 0; col < 4; col++)
                        if (bits.charAt(row * 4 + col) == '1')
                            px(x + col, y + row, color);
                x += 5;
            } else {
                if (c == ':') {
                    px(x, y + 2, color);
                    px(x, y + 4, color);
                } else if (c == '-') {
                    px(x, y + 3, color);
                }
                x += 2;
            }
        }
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    // ---- primitives ------------------------------------------------------------------------------------

    private void px(int x, int y, int color) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT)
            px[y * WIDTH + x] = color;
    }

    private void fill(int color) {
        java.util.Arrays.fill(px, color);
    }

    private void rect(int x, int y, int w, int h, int color) {
        for (int yy = Math.max(0, y); yy < Math.min(HEIGHT, y + h); yy++)
            for (int xx = Math.max(0, x); xx < Math.min(WIDTH, x + w); xx++)
                px[yy * WIDTH + xx] = color;
    }

    private void hline(int x, int y, int w, int color) {
        rect(x, y, w, 1, color);
    }

    private void vline(int x, int y, int h, int color) {
        rect(x, y, 1, h, color);
    }

    private void roundRect(int x, int y, int w, int h, int color) {
        rect(x + 1, y, w - 2, h, color);
        rect(x, y + 1, w, h - 2, color);
    }

    /**
     * Hand-drawn pixel-art circles, as widths per row (top to bottom): smooth, even steps, which a computed
     * circle does not give at these sizes. Diameters are even, so a circle around x = 16 sits exactly in the
     * middle of the 32 px screen.
     */
    private static final int[][] CIRCLES = {
            {},
            {},
            {2, 4, 4, 2},                                                   // 4
            {4, 6, 6, 6, 6, 4},                                             // 6
            {4, 6, 8, 8, 8, 8, 6, 4},                                       // 8
            {4, 8, 8, 10, 10, 10, 10, 8, 8, 4},                             // 10
            {},
            {},
            {6, 10, 12, 14, 14, 16, 16, 16, 16, 16, 16, 14, 14, 12, 10, 6}, // 16
            {6, 10, 12, 14, 16, 16, 18, 18, 18, 18, 18, 18, 16, 16, 14, 12, 10, 6}, // 18
            {8, 12, 14, 16, 16, 18, 18, 20, 20, 20, 20, 20, 20, 18, 18, 16, 16, 14, 12, 8}, // 20
            {8, 12, 14, 16, 18, 18, 20, 20, 22, 22, 22, 22, 22, 22, 20, 20, 18, 18, 16, 14, 12, 8}}; // 22

    /** Filled circle of radius r (diameter 2r) around (cx, cy): pixels cx-r to cx+r-1, cy-r to cy+r-1. */
    private void disc(int cx, int cy, int r, int color) {
        int[] rows = CIRCLES[r];
        for (int i = 0; i < rows.length; i++)
            hline(cx - rows[i] / 2, cy - r + i, rows[i], color);
    }

    /** One pixel wide outline of the same circle (the pixels of disc r that are not in disc r - 1). */
    private void ringOutline(int cx, int cy, int r, int color) {
        int[] outer = CIRCLES[r];
        int[] inner = CIRCLES[r - 1];
        for (int i = 0; i < outer.length; i++) {
            int y = cy - r + i;
            int j = i - 1;
            int in = j >= 0 && j < inner.length ? inner[j] : 0;
            int left = cx - outer[i] / 2, right = cx + outer[i] / 2 - 1;
            int innerLeft = cx - in / 2, innerRight = cx + in / 2 - 1;
            for (int x = left; x <= right; x++)
                if (in == 0 || x < innerLeft || x > innerRight)
                    px(x, y, color);
        }
    }

    private void icon(String[] icon, int x, int y, int color) {
        iconScaled(icon, x, y, 1, color);
    }

    private void iconScaled(String[] icon, int x, int y, int scale, int color) {
        for (int row = 0; row < icon.length; row++)
            for (int col = 0; col < icon[row].length(); col++)
                if (icon[row].charAt(col) == '#')
                    rect(x + col * scale, y + row * scale, scale, scale, color);
    }
}

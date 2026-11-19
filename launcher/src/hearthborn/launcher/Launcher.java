package hearthborn.launcher;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Small standalone desktop launcher; network and installation work stays off the UI thread. */
public final class Launcher {
    private final JFrame frame = new JFrame("Hearthborn Client Launcher");
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel amount = new JLabel(" "); // under the bar: text painted on the bar overlaps it
    private final JButton retry = new JButton(tr("Retry", "Повторить"));
    private final JButton versions = new JButton(tr("Versions…", "Версии…"));
    private final JButton newLauncher = new JButton(tr("New launcher…", "Новый лаунчер…"));
    /** True while the player is choosing a version; the start of the client waits. */
    private volatile boolean choosing, waiting;
    private volatile Updater.Installed picked;
    private volatile String latestTag;
    private final JButton folder = new JButton(tr("Open client folder", "Открыть папку клиента"));
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Updater updater;
    private final Path home;
    private Updater.Lock lock;
    private static final boolean RU = Locale.getDefault().getLanguage().equals("ru");
    private static String tr(String en, String ru) { return RU ? ru : en; }

    private Launcher(Updater updater, Path home) {
        this.home = home; this.updater = updater;
        Image icon = applicationIcon();
        if (icon != null) frame.setIconImage(icon);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 18)); content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        // Two lines like the title of the login screen: the game, then the client.
        JLabel game = new JLabel("Haven & Hearth"); game.setFont(game.getFont().deriveFont(Font.BOLD, 26f));
        JLabel client = new JLabel("Hearthborn Client Launcher"); client.setFont(client.getFont().deriveFont(Font.PLAIN, 16f));
        JPanel names = new JPanel(); names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS)); names.setOpaque(false);
        game.setAlignmentX(Component.LEFT_ALIGNMENT); client.setAlignmentX(Component.LEFT_ALIGNMENT); names.add(game); names.add(client);
        JPanel title = new JPanel(new BorderLayout(16, 0)); title.setOpaque(false);
        if (icon != null) { JLabel picture = new JLabel(logo(icon, 64)); title.add(picture, BorderLayout.WEST); }
        title.add(names, BorderLayout.CENTER);
        content.add(title, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(0, 12)); middle.add(status, BorderLayout.NORTH); middle.add(progress, BorderLayout.CENTER); middle.add(amount, BorderLayout.SOUTH);
        content.add(middle, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(newLauncher); buttons.add(folder); buttons.add(versions); buttons.add(retry);
        JButton close = new JButton(tr("Close", "Закрыть")); buttons.add(close); close.addActionListener(e -> System.exit(0));
        JLabel version = new JLabel(tr("Version ", "Версия ") + version()); version.setForeground(Color.GRAY);
        newLauncher.setVisible(false); newLauncher.addActionListener(e -> openLauncherPage());
        retry.setVisible(false); retry.addActionListener(e -> start());
        versions.setVisible(false); versions.addActionListener(e -> versionsClicked());
        folder.addActionListener(e -> { try { Files.createDirectories(home); Desktop.getDesktop().open(home.toFile()); } catch (IOException error) { fail(error); } });
        JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false); bottom.add(version, BorderLayout.WEST); bottom.add(buttons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH); frame.setContentPane(content); frame.setMinimumSize(new Dimension(580, 230));
        frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true); start();
    }
    private void start() {
        retry.setVisible(false); versions.setVisible(false); progress.setIndeterminate(true); amount.setText(" ");
        worker.submit(() -> {
            try {
                if (Runtime.version().feature() != 22) throw new IOException(tr("Use the packaged launcher with Java 22.", "Используйте загрузчик со встроенной Java 22."));
                if (lock == null) lock = updater.lock();
                run();
            } catch (Exception error) { log(error); SwingUtilities.invokeLater(() -> fail(error)); }
        });
    }
    private void run() throws Exception {
        setStatus(tr("Checking for updates…", "Проверка обновлений…"));
        Updater.Installed installed = null;
        try { installed = updater.installed(); } catch (IOException error) { log(error); }
        Updater.Release latest;
        try { latest = updater.latest(); }
        catch (IOException error) {
            if (installed == null) throw error;
            String message = tr("Could not check for updates. Start installed version ", "Не удалось проверить обновления. Запустить установленную версию ") + installed.release().version() + "?";
            if (!yes(message)) { finish(); return; }
            launch(installed, true); return;
        }
        latestTag = latest.tag();
        noteLauncher();
        boolean declined = installed != null && latest.tag().equals(updater.declined());
        if (!Updater.same(installed, latest) && !declined) {
            if (installed != null) {
                String message = tr("A new client version is available: ", "Доступна новая версия клиента: ") + latest.version()
                        + "\n" + tr("Installed: ", "Установлена: ") + installed.release().version();
                while (true) {
                    int[] answer = {2};
                    SwingUtilities.invokeAndWait(() -> answer[0] = JOptionPane.showOptionDialog(frame, message, "Hearthborn",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                            new String[]{tr("Download update", "Скачать обновление"), tr("Play installed version", "Играть в установленную версию"),
                                    tr("Other version…", "Другая версия…"), tr("Cancel", "Отмена")}, null));
                    if (answer[0] == 2) {
                        Updater.Installed[] chosen = {null};
                        SwingUtilities.invokeAndWait(() -> chosen[0] = choose());
                        if (chosen[0] == null) continue;
                        updater.select(chosen[0], latestTag);
                        launch(chosen[0], false); return;
                    }
                    if (answer[0] == 1) { launch(installed, true); return; }
                    if (answer[0] != 0) { finish(); return; }
                    break;
                }
            }
            setStatus(tr("Downloading client ", "Загрузка клиента ") + latest.version() + "…");
            SwingUtilities.invokeLater(() -> versions.setVisible(false));
            try { installed = updater.install(latest, this::progress); }
            catch (IOException error) {
                log(error);
                if (installed == null || !yes(tr("Update failed. Start the previous version?", "Не удалось обновить клиент. Запустить предыдущую версию?"))) throw error;
            }
            launch(installed, false); return;
        }
        launch(installed, true);
    }
    /** A newer launcher is named by the release manifest: say so once per version, and keep a button for it. */
    private void noteLauncher() throws Exception {
        String newest = updater.launcherLatest();
        if (!Updater.newerLauncher(newest, version())) return;
        SwingUtilities.invokeLater(() -> newLauncher.setVisible(true));
        if (newest.equals(updater.launcherNoticed())) return;
        updater.launcherNoticed(newest);
        String message = tr("A new version of the launcher is available: ", "Доступна новая версия лаунчера: ") + newest
                + "\n" + tr("Installed: ", "Установлена: ") + version()
                + "\n" + tr("The launcher is not updated by itself: download it and replace the old one.",
                        "Лаунчер сам не обновляется: скачайте его и замените старый.");
        int[] answer = {1};
        SwingUtilities.invokeAndWait(() -> answer[0] = JOptionPane.showOptionDialog(frame, message, "Hearthborn",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new String[]{tr("Open download page", "Открыть страницу загрузки"), tr("Later", "Позже")}, null));
        if (answer[0] == 0) openLauncherPage();
    }
    private void openLauncherPage() {
        try { Desktop.getDesktop().browse(updater.launcherPage()); } catch (IOException | UnsupportedOperationException error) { fail(error instanceof IOException io ? io : new IOException(updater.launcherPage().toString())); }
    }
    private boolean yes(String message) throws Exception {
        boolean[] yes = {false};
        SwingUtilities.invokeAndWait(() -> yes[0] = JOptionPane.showConfirmDialog(frame, message, "Hearthborn", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
        return yes[0];
    }
    /**
     * Starts the client. With {@code pause} the window stays up for a moment, with the "Versions…" button, when an older
     * version is installed too, so a bad update can be undone before the game opens.
     */
    private void launch(Updater.Installed installation, boolean pause) throws Exception {
        setStatus(tr("Starting client ", "Запуск клиента ") + installation.release().version() + "…");
        if (pause && updater.versions().size() > 1) {
            SwingUtilities.invokeLater(() -> versions.setVisible(true));
            waiting = true;
            try {
                long end = System.nanoTime() + 2_000_000_000L;
                while ((choosing || System.nanoTime() < end) && picked == null) Thread.sleep(50);
            } finally { waiting = false; }
            Updater.Installed other = picked; picked = null;
            if (other != null) { updater.select(other, latestTag); installation = other; setStatus(tr("Starting client ", "Запуск клиента ") + other.release().version() + "…"); }
        }
        Process client = updater.launch(installation);
        SwingUtilities.invokeLater(() -> { frame.setVisible(false); MacDock.hide(); });
        int code = client.waitFor();
        if (code != 0) {
            SwingUtilities.invokeLater(() -> { MacDock.show(); frame.setVisible(true); });
            throw new ClientExit(tr("The client exited with code ", "Клиент завершился с кодом ") + code + ".\n"
                    + tr("See the logs in game/logs and launcher.log in the client folder.", "Подробности: логи в game/logs и launcher.log в папке клиента."));
        }
        finish();
    }
    private void setStatus(String message) { SwingUtilities.invokeLater(() -> status.setText(message)); }
    private long lastProgress;
    private void progress(long done, long total) {
        long now = System.nanoTime();
        if (done != total && now - lastProgress < 100_000_000L) return;
        lastProgress = now;
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(false); progress.setValue((int)(done * 100 / total));
            amount.setText(String.format(Locale.ROOT, "%d%%  ·  %.1f / %.1f MB", done * 100 / total, done / 1048576.0, total / 1048576.0));
        });
    }
    /** The "Versions…" button: during the short pause it picks the version to start; after a failure it starts the one chosen. */
    private void versionsClicked() {
        choosing = true;
        Updater.Installed chosen;
        try { chosen = choose(); } finally { if (!waiting) choosing = false; }
        if (chosen == null) { choosing = false; return; }
        if (waiting) { picked = chosen; choosing = false; return; }
        choosing = false;
        retry.setVisible(false); versions.setVisible(false); progress.setIndeterminate(true);
        worker.submit(() -> {
            try { updater.select(chosen, latestTag); launch(chosen, false); }
            catch (Exception error) { log(error); SwingUtilities.invokeLater(() -> fail(error)); }
        });
    }
    /** Asks which installed version to start; null when cancelled. Runs on the UI thread. */
    private Updater.Installed choose() {
        java.util.List<Updater.Installed> found;
        Updater.Installed active = null;
        try { found = updater.versions(); active = updater.installed(); }
        catch (IOException error) { log(error); found = java.util.List.of(); }
        if (found.size() < 2) {
            JOptionPane.showMessageDialog(frame, tr("No other versions are installed.", "Других установленных версий нет."), "Hearthborn", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        final String current = active == null ? "" : active.release().version();
        record Option(Updater.Installed item, String label) { public String toString() { return label; } }
        Option[] options = found.stream().map(item -> new Option(item, item.release().version()
                + (item.release().version().equals(current) ? tr("  (current)", "  (текущая)") : ""))).toArray(Option[]::new);
        Object answer = JOptionPane.showInputDialog(frame, tr("Start this version:", "Запустить версию:"), "Hearthborn",
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return answer == null ? null : ((Option) answer).item();
    }
    private void fail(Exception error) {
        progress.setIndeterminate(false); progress.setValue(0); amount.setText(" "); retry.setVisible(true); versions.setVisible(true);
        status.setText(tr("Could not start Hearthborn.", "Не удалось запустить Hearthborn."));
        JOptionPane.showMessageDialog(frame, error.getMessage(), "Hearthborn", JOptionPane.ERROR_MESSAGE);
    }
    /** The game ended with an error code; the stack of the launcher itself says nothing about why. */
    private static final class ClientExit extends IOException { ClientExit(String message) { super(message); } }
    private void log(Exception error) {
        try {
            Files.createDirectories(home);
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(home.resolve("launcher.log"), StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                out.println(java.time.Instant.now());
                if (error instanceof ClientExit) out.println(error.getMessage().replace('\n', ' ')); else error.printStackTrace(out);
            }
        } catch (IOException ignored) { error.printStackTrace(); }
    }
    private void finish() throws IOException {
        if (lock != null) { lock.close(); lock = null; }
        worker.shutdown(); SwingUtilities.invokeLater(() -> { frame.dispose(); System.exit(0); });
    }
    /** The launcher's own version: written into launcher.properties by tools/build_launcher.py from launcher/VERSION. */
    static String version() {
        try (InputStream input = Launcher.class.getResourceAsStream("/launcher.properties")) {
            Properties config = new Properties();
            if (input != null) config.load(input);
            return config.getProperty("version", "dev");
        } catch (IOException error) { return "dev"; }
    }
    /** The application picture drawn from the full-size image at the window's own resolution, so it stays sharp on Retina. */
    private static Icon logo(Image image, int size) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(image, x, y, size, size, null);
                g2.dispose();
            }
        };
    }
    private static Image applicationIcon() {
        java.net.URL resource = Launcher.class.getResource("/icon.png");
        return resource == null ? null : new ImageIcon(resource).getImage();
    }
    private static void taskbarIcon() {
        Image icon = applicationIcon();
        if (icon == null || !Taskbar.isTaskbarSupported()) return;
        Taskbar taskbar = Taskbar.getTaskbar();
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.setIconImage(icon);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                taskbarIcon();
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Properties config = new Properties();
                try (InputStream input = Launcher.class.getResourceAsStream("/launcher.properties")) { if (input != null) config.load(input); }
                String repository = System.getProperty("hearthborn.repository", config.getProperty("repository", ""));
                for (String arg : args) {
                    if (arg.startsWith("--repository=")) repository = arg.substring("--repository=".length());
                    else throw new IllegalArgumentException("Unknown launcher argument: " + arg);
                }
                Path home = Path.of(System.getProperty("hearthborn.home", Updater.defaultHome().toString()));
                if(repository.isBlank()){
                    Properties saved=new Properties();Path setup=home.resolve("launcher.properties");
                    if(Files.isRegularFile(setup))try(Reader reader=Files.newBufferedReader(setup)){saved.load(reader);}
                    repository=saved.getProperty("repository", "");
                    if(repository.isBlank()) repository=Updater.PUBLIC;
                }
                String local = System.getProperty("hearthborn.local-repository", "");
                new Launcher(local.isBlank() ? new Updater(repository, home) : Updater.local(local, home), home);
            } catch (Exception error) {
                JOptionPane.showMessageDialog(null, error.getMessage(), "Hearthborn Launcher", JOptionPane.ERROR_MESSAGE); System.exit(1);
            }
        });
    }
}

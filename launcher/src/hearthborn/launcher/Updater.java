package hearthborn.launcher;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.security.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
import java.util.zip.*;

/** Release installation has no dependencies on the game or its mutable data. */
public final class Updater {
    public static final int FORMAT = 1;
    static final long MAX_DOWNLOAD = 512L * 1024 * 1024, MAX_EXPANDED = 2L * 1024 * 1024 * 1024;
    private final URI repository;
    private final Path home;
    private final boolean testTransport;

    public record Release(String version, String tag, String asset, String sha256, long size) {
        public Release {
            token(version); token(tag);
            if (!asset.equals("hearthborn-client.zip")) throw new IllegalArgumentException("Unexpected release asset");
            if (!sha256.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException("Invalid release SHA-256");
            if (size <= 0 || size > MAX_DOWNLOAD) throw new IllegalArgumentException("Invalid release size");
            sha256 = sha256.toLowerCase(Locale.ROOT);
        }
        String directory() { return version + "-" + sha256.substring(0, 12); }
    }
    public record Installed(Release release, Path directory) {}

    public Updater(String slug, Path home) {
        this(repository(slug), home, false);
    }
    Updater(URI repository, Path home, boolean testTransport) {
        this.repository = repository;
        this.home = home.toAbsolutePath().normalize();
        this.testTransport = testTransport;
    }
    /** Public client releases: https://github.com/Jagy2014/hearthborn/ */
    public static final String PUBLIC = "Jagy2014/hearthborn";
    /**
     * Developer check against a release folder served on this computer ({@code -Dhearthborn.local-repository}).
     * Only plain HTTP to 127.0.0.1 is accepted, so nothing here can reach another machine.
     */
    public static Updater local(String address, Path home) {
        URI uri = URI.create(address.endsWith("/") ? address : address + "/");
        if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()))
            throw new IllegalArgumentException("A local release folder must be http://127.0.0.1:PORT/");
        return new Updater(uri, home, true);
    }
    static URI repository(String slug) {
        String value = slug == null ? "" : slug.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        String prefix = "https://github.com/";
        if (value.regionMatches(true, 0, prefix, 0, prefix.length())) value = value.substring(prefix.length());
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}/hearthborn(-client)?"))
            throw new IllegalArgumentException("Set a GitHub repository: ACCOUNT/hearthborn");
        return URI.create("https://github.com/" + value + "/");
    }
    static void token(String token) {
        if (token == null || !token.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}") || token.contains(".."))
            throw new IllegalArgumentException("Invalid release version or tag");
    }
    public static Path defaultHome() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        Path user = Path.of(System.getProperty("user.home"));
        if (os.contains("mac")) return user.resolve("Library/Application Support/Hearthborn");
        if (os.contains("win")) {
            String base = System.getenv("LOCALAPPDATA");
            return base == null ? user.resolve("AppData/Local/Hearthborn") : Path.of(base).resolve("Hearthborn");
        }
        String base = System.getenv("XDG_DATA_HOME");
        return (base == null || base.isBlank() ? user.resolve(".local/share") : Path.of(base)).resolve("hearthborn");
    }
    public Lock lock() throws IOException {
        Files.createDirectories(home);
        FileChannel channel = FileChannel.open(home.resolve("launcher.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Hearthborn is already running.");
            return new Lock(channel, lock);
        } catch (OverlappingFileLockException error) { channel.close(); throw new IOException("Hearthborn is already running.",error); }
        catch (IOException | RuntimeException error) { channel.close(); throw error; }
    }
    public record Lock(FileChannel channel, FileLock lock) implements AutoCloseable {
        public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    }
    private HttpURLConnection connection(URI address) throws IOException {
        URI next = address;
        for (int redirects = 0; redirects <= 6; redirects++) {
            if (!"https".equals(next.getScheme()) && !(testTransport && "http".equals(next.getScheme()) && "127.0.0.1".equals(next.getHost())))
                throw new IOException("Downloads require HTTPS");
            HttpURLConnection connection = (HttpURLConnection) next.toURL().openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Hearthborn-Launcher/1");
            connection.setRequestProperty("Cache-Control", "no-cache");
            int status = connection.getResponseCode();
            if (status == 200) return connection;
            String target = connection.getHeaderField("Location");
            connection.disconnect();
            if (Set.of(301, 302, 303, 307, 308).contains(status) && target != null) { next = next.resolve(target); continue; }
            throw new IOException("Download failed: HTTP " + status);
        }
        throw new IOException("Too many download redirects");
    }
    public Release latest() throws IOException {
        HttpURLConnection connection = connection(repository.resolve("releases/latest/download/hearthborn-release.properties"));
        try (InputStream input = connection.getInputStream()) {
            byte[] bytes = input.readNBytes(65537);
            if (bytes.length > 65536) throw new IOException("Release manifest too large");
            Properties properties = new Properties();
            properties.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            Release release = parse(properties);
            launcherLatest = launcherVersion(properties.getProperty("launcher.latest"));
            return release;
        } catch (IllegalArgumentException error) {
            String message = error.getMessage() != null && error.getMessage().startsWith(NEEDS_NEWER)
                    ? NEEDS_NEWER + ". Download it from " + launcherPage() : "Invalid release manifest";
            throw new IOException(message, error);
        }
        finally { connection.disconnect(); }
    }
    static final String NEEDS_NEWER = "This release needs a newer launcher";
    /** The newest launcher version the last manifest named ({@code launcher.latest}, optional), or null. */
    private volatile String launcherLatest;
    public String launcherLatest() { return launcherLatest; }
    /** Where the launcher is downloaded: the page of the latest release, which carries the launcher archives. */
    public URI launcherPage() { return repository.resolve("releases/latest"); }
    /** A launcher version such as 1.2.0, or null when the text is not one. */
    static String launcherVersion(String text) {
        return text != null && text.trim().matches("\\d{1,4}(\\.\\d{1,4}){1,3}") ? text.trim() : null;
    }
    /** Whether {@code latest} is a newer launcher than {@code current}; false when either is not a plain number version (a development build). */
    public static boolean newerLauncher(String latest, String current) {
        String a = launcherVersion(latest), b = launcherVersion(current);
        if (a == null || b == null) return false;
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int left = i < x.length ? Integer.parseInt(x[i]) : 0, right = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (left != right) return left > right;
        }
        return false;
    }
    /** The launcher version the player was already told about, or null. */
    public String launcherNoticed() {
        Path file = home.resolve("launcher-notice.properties");
        if (!Files.isRegularFile(file)) return null;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) { properties.load(reader); return properties.getProperty("noticed"); }
        catch (IOException error) { return null; }
    }
    public void launcherNoticed(String version) throws IOException {
        Files.createDirectories(home);
        Properties properties = new Properties(); properties.setProperty("noticed", version);
        try (Writer writer = Files.newBufferedWriter(home.resolve("launcher-notice.properties"))) { properties.store(writer, "launcher update notice"); }
    }
    static Release parse(Properties properties) {
        if (!"1".equals(properties.getProperty("format"))) throw new IllegalArgumentException("Unsupported release manifest");
        int launcher = Integer.parseInt(properties.getProperty("launcher.min", "1"));
        if (launcher > FORMAT) throw new IllegalArgumentException(NEEDS_NEWER);
        if (!"22".equals(properties.getProperty("java.major"))) throw new IllegalArgumentException("Unsupported Java runtime");
        return new Release(properties.getProperty("version"), properties.getProperty("tag"),
                properties.getProperty("asset", ""), properties.getProperty("sha256", ""), Long.parseLong(properties.getProperty("size", "0")));
    }
    public Installed installed() throws IOException {
        Path state = home.resolve("installed.properties");
        if (!Files.isRegularFile(state)) return null;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(state)) { properties.load(reader); }
        try {
            Release release = parse(properties);
            Path directory = home.resolve("versions").resolve(release.directory());
            validate(directory);
            return new Installed(release, directory);
        } catch (IllegalArgumentException error) { throw new IOException("Invalid local installation record", error); }
    }
    public static boolean same(Installed installed, Release release) {
        return installed != null && installed.release.equals(release);
    }
    public Installed install(Release release, BiConsumer<Long, Long> progress) throws IOException {
        Files.createDirectories(home.resolve("versions"));
        Path transaction = Files.createTempDirectory(home.resolve("versions"), ".download-");
        try {
            Path archive = transaction.resolve("client.zip");
            download(release, archive, progress);
            Path extracted = transaction.resolve("client");
            extract(archive, extracted);
            validate(extracted);
            try (Writer writer = Files.newBufferedWriter(extracted.resolve(RELEASE_RECORD))) { properties(release).store(writer, "Hearthborn release"); }
            Path destination = home.resolve("versions").resolve(release.directory());
            if (Files.exists(destination)) {
                // An incomplete previous installation must not prevent a repair.
                Path retired = home.resolve("versions").resolve(".retired-" + UUID.randomUUID());
                Files.move(destination, retired);
                try { Files.move(extracted, destination); }
                catch (IOException error) { Files.move(retired, destination); throw error; }
                deleteTree(retired);
            } else Files.move(extracted, destination);
            writeState(properties(release));
            Files.setLastModifiedTime(destination, FileTime.from(Instant.now()));
            try { prune(home.resolve("versions"), destination, KEEP_VERSIONS); }
            catch (IOException ignored) { /* a leftover old version is harmless; the update itself succeeded */ }
            return new Installed(release, destination);
        } finally { deleteTree(transaction); }
    }
    /** Installed versions kept on disk, the active one included, so a bad update can be undone from the launcher. */
    static final int KEEP_VERSIONS = 3;
    /** Deletes the oldest version directories beyond {@code keep}; the active one is never removed. */
    static void prune(Path versions, Path active, int keep) throws IOException {
        List<Path> found = new ArrayList<>();
        try (var paths = Files.list(versions)) {
            paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    && path.getFileName().toString().matches("[A-Za-z0-9._-]+-[0-9a-f]{12}")).forEach(found::add);
        }
        found.sort(Comparator.comparing((Path path) -> {
            try { return Files.getLastModifiedTime(path); } catch (IOException error) { return FileTime.fromMillis(0); }
        }).reversed());
        Set<Path> keepers = new HashSet<>();
        if (found.contains(active)) keepers.add(active);
        for (Path path : found) if (keepers.size() < keep) keepers.add(path);
        for (Path path : found) if (!keepers.contains(path)) deleteTree(path);
    }
    /** Kept inside every version directory, so an older version can be selected again without the network. */
    static final String RELEASE_RECORD = "release.properties";
    private void writeState(Properties state) throws IOException {
        Path temporary = Files.createTempFile(home, ".installed-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) { state.store(writer, "Hearthborn installation"); }
            atomicMove(temporary, home.resolve("installed.properties"));
        } finally { Files.deleteIfExists(temporary); }
    }
    /** Installed versions that can be started again, newest first (versions installed by older launchers are not listed). */
    public List<Installed> versions() throws IOException {
        Path root = home.resolve("versions");
        List<Installed> found = new ArrayList<>();
        if (!Files.isDirectory(root)) return found;
        try (var paths = Files.list(root)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path record = path.resolve(RELEASE_RECORD);
                if (!Files.isRegularFile(record)) continue;
                try {
                    Properties properties = new Properties();
                    try (Reader reader = Files.newBufferedReader(record)) { properties.load(reader); }
                    Release release = parse(properties);
                    if (!path.getFileName().toString().equals(release.directory())) continue;
                    validate(path);
                    found.add(new Installed(release, path));
                } catch (IOException | RuntimeException skipped) { /* a damaged copy is not offered */ }
            }
        }
        found.sort(Comparator.comparing((Installed item) -> {
            try { return Files.getLastModifiedTime(item.directory); } catch (IOException error) { return FileTime.fromMillis(0); }
        }).reversed());
        return found;
    }
    /**
     * Makes an installed version the active one. {@code declined} is the newest release the player knows about and chose
     * not to take; the launcher then stops offering it until a different release appears.
     */
    public void select(Installed target, String declined) throws IOException {
        validate(target.directory);
        Properties state = properties(target.release);
        if (declined != null && !declined.equals(target.release.tag)) state.setProperty("declined", declined);
        writeState(state);
    }
    /** The release the player declined when going back to an older version, or null. */
    public String declined() {
        try {
            Path state = home.resolve("installed.properties");
            if (!Files.isRegularFile(state)) return null;
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(state)) { properties.load(reader); }
            return properties.getProperty("declined");
        } catch (IOException error) { return null; }
    }
    static Properties properties(Release release) {
        Properties state = new Properties();
        state.setProperty("format", "1"); state.setProperty("java.major", "22"); state.setProperty("launcher.min", "1");
        state.setProperty("version", release.version); state.setProperty("tag", release.tag);
        state.setProperty("asset", release.asset); state.setProperty("size", Long.toString(release.size)); state.setProperty("sha256", release.sha256);
        return state;
    }
    private void download(Release release, Path destination, BiConsumer<Long, Long> progress) throws IOException {
        URI url = repository.resolve("releases/download/" + release.tag + "/" + release.asset);
        HttpURLConnection connection = connection(url);
        try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
            MessageDigest digest = digest(); byte[] block = new byte[65536]; long count = 0; int size;
            while ((size = input.read(block)) >= 0) {
                count += size;
                if (count > release.size) throw new IOException("Release archive is larger than declared");
                output.write(block, 0, size); digest.update(block, 0, size); progress.accept(count, release.size);
            }
            if (count != release.size) throw new IOException("Release archive download is incomplete");
            if (!HexFormat.of().formatHex(digest.digest()).equals(release.sha256)) throw new IOException("Release archive SHA-256 mismatch");
        } finally { connection.disconnect(); }
    }
    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
    static void extract(Path archive, Path directory) throws IOException {
        Files.createDirectory(directory); long total = 0; int entries = 0;
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry; byte[] block = new byte[65536];
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (++entries > 10000 || name.contains("\\") || name.contains(":") || name.startsWith("/")) throw new IOException("Unsafe ZIP entry");
                Path target = directory.resolve(name).normalize();
                if (!target.startsWith(directory) || target.equals(directory)) throw new IOException("ZIP path escapes installation");
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    int size;
                    while ((size = input.read(block)) >= 0) {
                        total += size;
                        if (total > MAX_EXPANDED) throw new IOException("Release expands beyond the size limit");
                        output.write(block, 0, size);
                    }
                }
            }
        }
    }
    static void validate(Path directory) throws IOException {
        for (String name : List.of("hafen.jar", "hafen-panama.jar", "hafen-res.jar", "builtin-res.jar", "haven-config.properties", "COPYING"))
            if (!Files.isRegularFile(directory.resolve(name))) throw new IOException("Release is missing " + name);
        try (JarFile client = new JarFile(directory.resolve("hafen.jar").toFile())) {
            if (client.getManifest() == null || !"haven.Client".equals(client.getManifest().getMainAttributes().getValue("Main-Class")))
                throw new IOException("Invalid client JAR");
            String dependencies = client.getManifest().getMainAttributes().getValue("Class-Path");
            if (dependencies == null) throw new IOException("Missing client dependencies");
            for (String name : dependencies.trim().split("\\s+")) {
                if (!name.matches("[A-Za-z0-9._-]+\\.jar") || !Files.isRegularFile(directory.resolve(name)))
                    throw new IOException("Missing or unsafe client dependency: " + name);
            }
        }
    }
    public Process launch(Installed installation) throws IOException {
        validate(installation.directory);
        Path working = home.resolve("game"); Files.createDirectories(working.resolve("logs"));
        // Keep mutable files outside version directories and preserve user overrides.
        Path config = working.resolve("haven-config.properties");
        if (!Files.exists(config)) Files.copy(installation.directory.resolve("haven-config.properties"), config);
        ProcessBuilder process = new ProcessBuilder(command(installation));
        process.directory(working.toFile());
        process.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(working.resolve("logs/client.log").toFile()));
        return process.start();
    }
    public List<String> command(Installed installation) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        List<String> command = new ArrayList<>(List.of(java.toString(), "-Xms512m", "-Xmx2048m", "--enable-native-access=ALL-UNNAMED",
                "-Dsun.java2d.uiScale.enabled=false", "-Dhaven.prefspec=hearthborn-client",
                "-Dhearthborn.data.directory=" + home.resolve("game/data"), "-XX:ErrorFile=" + home.resolve("game/logs/jvm-crash-%p.log")));
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            command.add("-Dhaven.toolkit=cgl");
            command.addAll(appleSiliconFlags());
        }
        command.addAll(List.of("-jar", installation.directory.resolve("hafen.jar").toString()));
        return command;
    }
    /** Performance-core sizing and larger G1 regions for Apple Silicon; nothing on Intel Macs. */
    static List<String> appleSiliconFlags() {
        if (!System.getProperty("os.arch", "").contains("aarch64")) return List.of();
        List<String> flags = new ArrayList<>(List.of("-Xms1g", "-XX:G1HeapRegionSize=4m"));
        try {
            Process probe = new ProcessBuilder("/usr/sbin/sysctl", "-n", "hw.perflevel0.physicalcpu").redirectErrorStream(true).start();
            String line = new String(probe.getInputStream().readAllBytes()).trim();
            if (probe.waitFor() == 0 && Integer.parseInt(line) >= 2) flags.add("-XX:ActiveProcessorCount=" + Integer.parseInt(line));
        } catch (IOException | NumberFormatException | InterruptedException ignored) { }
        return flags;
    }
    static void atomicMove(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}

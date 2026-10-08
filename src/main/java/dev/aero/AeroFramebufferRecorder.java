package dev.aero;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

/** Opt-in renderer capture. Readback stays native; one bounded worker writes its images. */
public final class AeroFramebufferRecorder {
    private static final Gson JSON = new Gson();
    private volatile Session session, lastSession;

    private record Frame(long timestampNs, String file) { }

    private static final class Session {
        final String name;
        final Path directory;
        final long startNs = System.nanoTime(), startEpochMs = System.currentTimeMillis();
        final List<Frame> frames = new ArrayList<>();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        final AtomicInteger overflow = new AtomicInteger(), written = new AtomicInteger();
        final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(6), task -> {
                    Thread thread = new Thread(task, "aero-frame-writer");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        volatile boolean accepting = true, complete;
        long stopNs;
        int attempted, accepted, width, height;
        byte[] rgbBuffer = new byte[0];

        Session(String name, Path directory) { this.name = name; this.directory = directory; }
    }

    public void start(MinecraftClient mc, String name) throws IOException {
        if (session != null) throw new IllegalStateException("Previous framebuffer capture is still active or draining");
        if (!name.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("Capture name must be a safe lowercase slug");
        Path root = mc.runDirectory.toPath().resolve("aero-frames");
        Files.createDirectories(root);
        Path directory = root.resolve(name);
        Files.createDirectory(directory);
        session = new Session(name, directory);
        System.out.println("AERO_CAPTURE started name=" + name + " path=" + directory);
    }

    public void afterFrame(MinecraftClient mc) {
        Session capture = session;
        if (capture == null || !capture.accepting) return;
        long now = System.nanoTime();
        String file = String.format(Locale.ROOT, "frame-%06d.ppm", capture.attempted++);
        NativeImage image;
        try {
            image = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
        } catch (Exception ex) {
            capture.errors.add("Readback " + file + ": " + ex);
            return;
        }
        if (capture.width == 0) { capture.width = image.getWidth(); capture.height = image.getHeight(); }
        else if (capture.width != image.getWidth() || capture.height != image.getHeight())
            capture.errors.add("Framebuffer resized during capture at " + file);
        try {
            capture.writer.execute(() -> {
                try {
                    writePpm(capture, image, capture.directory.resolve(file));
                    capture.frames.add(new Frame(now, file));
                    capture.written.incrementAndGet();
                } catch (Exception ex) {
                    capture.errors.add("Write " + file + ": " + ex);
                } finally {
                    image.close();
                }
            });
            capture.accepted++;
        } catch (RejectedExecutionException ex) {
            image.close();
            capture.overflow.incrementAndGet();
            capture.errors.add("Writer queue overflow at " + file + " timestampNs=" + now);
        }
    }

    private static void writePpm(Session capture, NativeImage image, Path file) throws IOException {
        // Pinned 1.21.1 bulk RGBA pixels are ABGR ints: red low byte, green next, blue next.
        int[] pixels = image.copyPixelsRgba();
        int bytes = Math.multiplyExact(pixels.length, 3);
        if (capture.rgbBuffer.length != bytes) capture.rgbBuffer = new byte[bytes];
        int offset = 0;
        for (int pixel : pixels) {
            capture.rgbBuffer[offset++] = (byte) pixel;
            capture.rgbBuffer[offset++] = (byte) (pixel >>> 8);
            capture.rgbBuffer[offset++] = (byte) (pixel >>> 16);
        }
        byte[] header = ("P6\n" + image.getWidth() + " " + image.getHeight() + "\n255\n").getBytes(StandardCharsets.US_ASCII);
        try (var output = new BufferedOutputStream(Files.newOutputStream(file), 64 * 1024)) {
            output.write(header);
            output.write(capture.rgbBuffer);
        }
    }

    /** Stop admitting images immediately; raw frame drain and completion stay off-thread. */
    public void stop() {
        Session capture = session;
        if (capture == null || !capture.accepting) return;
        capture.stopNs = System.nanoTime();
        capture.accepting = false;
        capture.writer.shutdown();
        Thread finalizer = new Thread(() -> finish(capture), "aero-frame-finalize");
        finalizer.setDaemon(true);
        finalizer.start();
    }

    private void finish(Session capture) {
        try {
            while (!capture.writer.awaitTermination(1, TimeUnit.SECONDS)) { }
            JsonObject report = new JsonObject();
            report.addProperty("schema", 1);
            report.addProperty("source", "Minecraft framebuffer after GameRenderer.render; uncompressed P6 RGB");
            report.addProperty("name", capture.name);
            report.addProperty("targetFps", 60);
            report.addProperty("startNs", capture.startNs);
            report.addProperty("startEpochMs", capture.startEpochMs);
            report.addProperty("stopNs", capture.stopNs);
            report.addProperty("durationMs", (capture.stopNs - capture.startNs) / 1e6);
            report.addProperty("width", capture.width);
            report.addProperty("height", capture.height);
            report.addProperty("attempted", capture.attempted);
            report.addProperty("accepted", capture.accepted);
            report.addProperty("frameCount", capture.frames.size());
            report.addProperty("sampling", "every rendered frame");
            report.addProperty("overflow", capture.overflow.get());
            report.add("errors", JSON.toJsonTree(new ArrayList<>(capture.errors)));
            report.add("frames", JSON.toJsonTree(capture.frames));
            report.addProperty("complete", true);
            report.addProperty("success", capture.overflow.get() == 0 && capture.errors.isEmpty() && !capture.frames.isEmpty());
            Path temporary = capture.directory.resolve("frames.json.tmp"), target = capture.directory.resolve("frames.json");
            Files.writeString(temporary, JSON.toJson(report));
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target); }
            capture.complete = true;
            System.out.println("AERO_CAPTURE finalized name=" + capture.name + " frames=" + capture.frames.size() + " errors=" + capture.errors.size());
        } catch (Exception ex) {
            capture.errors.add("Finalize: " + ex);
            System.err.println("AERO_CAPTURE " + ex);
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            lastSession = capture;
            if (session == capture) session = null;
        }
    }

    public JsonObject status() {
        Session capture = session;
        if (capture == null) capture = lastSession;
        JsonObject status = new JsonObject();
        status.addProperty("active", capture != null && capture.accepting);
        status.addProperty("draining", capture != null && !capture.accepting && !capture.complete && session == capture);
        if (capture != null) {
            status.addProperty("name", capture.name);
            status.addProperty("complete", capture.complete);
            status.addProperty("written", capture.written.get());
            status.addProperty("overflow", capture.overflow.get());
            status.addProperty("errors", capture.errors.size());
        }
        return status;
    }
}

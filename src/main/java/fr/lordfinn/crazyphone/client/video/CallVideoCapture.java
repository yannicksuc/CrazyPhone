package fr.lordfinn.crazyphone.client.video;

import com.mojang.blaze3d.pipeline.RenderTarget;

/**
 * Grabs a small square picture out of a render target, cheaply and without ever stalling the frame: the GPU does
 * the downscale (a blit into a tiny texture), and the pixels are read back asynchronously, a frame or two later,
 * so the CPU never waits for the GPU. Only ever one capture in flight; asking for another while one is pending is
 * simply refused (the caller drops that frame, it never queues).
 *
 * Two implementations behind one API, because the rendering layer changed shape completely at 1.21.10:
 * <ul>
 * <li>below 1.21.10, plain OpenGL: a linear-filtered glBlitFramebuffer of the center square into a tiny
 * renderbuffer, then glReadPixels into a pixel-pack buffer with a fence, mapped once the fence has passed.
 * Nothing here touches a texture binding Minecraft tracks.</li>
 * <li>from 1.21.10 on, the same thing through the GpuDevice API (copied from how vanilla's own Tracy frame capture
 * does it, the one vanilla code path that does exactly this job), which also covers the Vulkan backend of 26.2+.
 * TRACY_BLIT scales the whole picture, so that small texture keeps the window's aspect ratio and the center
 * square is cut out on the CPU.</li>
 * </ul>
 * Pixels come out as plain 0xRRGGBB ints, top row first, already averaged down to the requested side.
 */
public final class CallVideoCapture {
    /** Receives a finished capture, on the render thread. */
    public interface Sink {
        void accept(int[] rgb, int side);
    }

    /** The GPU downscales to SUPERSAMPLE times the final side, the CPU averages the rest (each output pixel is then
     * the mean of SUPERSAMPLE^2 filtered samples: no shimmering edges when the camera moves). */
    private static final int SUPERSAMPLE = 3;

    private static Sink pendingSink;
    private static int pendingSide;
    private static float pendingZoom = 1f;
    private static boolean inFlight;

    private CallVideoCapture() {
    }

    public static boolean isBusy() {
        return inFlight;
    }

    //? if <1.21.10 {
    private static int fbo;
    private static int renderbuffer;
    private static int packBuffer;
    private static int bufferWidth = -1;
    private static int bufferHeight = -1;
    private static long fence;

    /** Starts a capture of {@code target}'s center square ({@code zoom} > 1 takes a smaller center square, like a
     * digital zoom). Returns false (and does nothing) if one is already in
     * flight. Must be called on the render thread, while nothing else is mid-draw. */
    public static boolean capture(RenderTarget target, int side, float zoom, Sink sink) {
        if (inFlight)
            return false;
        int w = side * SUPERSAMPLE;
        int h = side * SUPERSAMPLE;
        ensureBuffers(w, h);
        int square = Math.max(1, Math.round(Math.min(target.width, target.height) / Math.max(1f, zoom)));
        int sx = (target.width - square) / 2;
        int sy = (target.height - square) / 2;
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, fbo);
        com.mojang.blaze3d.platform.GlStateManager._glBlitFrameBuffer(sx, sy, sx + square, sy + square, 0, 0, w, h,
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_LINEAR);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, fbo);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
        org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, 0L);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0);
        fence = org.lwjgl.opengl.GL32.glFenceSync(org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        // Back to exactly what was bound before: the target being rendered to, for both read and draw.
        target.bindWrite(false);
        pendingSink = sink;
        pendingSide = side;
        pendingZoom = zoom;
        inFlight = true;
        return true;
    }

    /** Finishes a pending capture once the GPU is done with it. Call once per frame; never blocks. */
    public static void poll() {
        if (!inFlight || fence == 0)
            return;
        int status = org.lwjgl.opengl.GL32.glClientWaitSync(fence, 0, 0L);
        if (status != org.lwjgl.opengl.GL32.GL_ALREADY_SIGNALED && status != org.lwjgl.opengl.GL32.GL_CONDITION_SATISFIED) {
            if (status == org.lwjgl.opengl.GL32.GL_WAIT_FAILED)
                abort();
            return;
        }
        org.lwjgl.opengl.GL32.glDeleteSync(fence);
        fence = 0;
        int w = bufferWidth, h = bufferHeight;
        int[] rgb;
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
        try {
            java.nio.ByteBuffer data = org.lwjgl.opengl.GL30.glMapBufferRange(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0, (long) w * h * 4,
                    org.lwjgl.opengl.GL30.GL_MAP_READ_BIT);
            if (data == null) {
                abort();
                return;
            }
            rgb = downsample(data, w, h, 0, 0, w, h, pendingSide, true);
            org.lwjgl.opengl.GL15.glUnmapBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER);
        } finally {
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0);
        }
        deliver(rgb);
    }

    private static void ensureBuffers(int w, int h) {
        if (w == bufferWidth && h == bufferHeight && fbo != 0)
            return;
        releaseBuffers();
        renderbuffer = org.lwjgl.opengl.GL30.glGenRenderbuffers();
        org.lwjgl.opengl.GL30.glBindRenderbuffer(org.lwjgl.opengl.GL30.GL_RENDERBUFFER, renderbuffer);
        org.lwjgl.opengl.GL30.glRenderbufferStorage(org.lwjgl.opengl.GL30.GL_RENDERBUFFER, org.lwjgl.opengl.GL11.GL_RGBA8, w, h);
        org.lwjgl.opengl.GL30.glBindRenderbuffer(org.lwjgl.opengl.GL30.GL_RENDERBUFFER, 0);
        fbo = org.lwjgl.opengl.GL30.glGenFramebuffers();
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, fbo);
        org.lwjgl.opengl.GL30.glFramebufferRenderbuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                org.lwjgl.opengl.GL30.GL_RENDERBUFFER, renderbuffer);
        int status = org.lwjgl.opengl.GL30.glCheckFramebufferStatus(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER);
        packBuffer = org.lwjgl.opengl.GL15.glGenBuffers();
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
        org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, (long) w * h * 4, org.lwjgl.opengl.GL15.GL_STREAM_READ);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, 0);
        bufferWidth = w;
        bufferHeight = h;
        if (status != org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE) {
            releaseBuffers();
            throw new IllegalStateException("Call video capture framebuffer incomplete: " + status);
        }
    }

    private static void releaseBuffers() {
        if (fence != 0) {
            org.lwjgl.opengl.GL32.glDeleteSync(fence);
            fence = 0;
        }
        if (fbo != 0)
            org.lwjgl.opengl.GL30.glDeleteFramebuffers(fbo);
        if (renderbuffer != 0)
            org.lwjgl.opengl.GL30.glDeleteRenderbuffers(renderbuffer);
        if (packBuffer != 0)
            org.lwjgl.opengl.GL15.glDeleteBuffers(packBuffer);
        fbo = renderbuffer = packBuffer = 0;
        bufferWidth = bufferHeight = -1;
    }
    //?}
    //? if >=1.21.10 <26 {
    /*private static com.mojang.blaze3d.textures.GpuTexture texture;
    private static com.mojang.blaze3d.textures.GpuTextureView textureView;
    private static com.mojang.blaze3d.buffers.GpuBuffer buffer;
    private static int bufferWidth = -1;
    private static int bufferHeight = -1;
    private static volatile boolean copied;

    public static boolean capture(RenderTarget target, int side, float zoom, Sink sink) {
        if (inFlight || target.getColorTextureView() == null)
            return false;
        // Keeps the window's aspect ratio (TRACY_BLIT stretches the whole source over the whole output), with
        // the short side at SUPERSAMPLE times the final side.
        int h = side * SUPERSAMPLE;
        int w = Math.max(h, Math.round(h * (float) target.width / Math.max(1, target.height)));
        if (target.height > target.width) {
            w = side * SUPERSAMPLE;
            h = Math.max(w, Math.round(w * (float) target.height / Math.max(1, target.width)));
        }
        ensureBuffers(w, h);
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        try (var pass = device.createCommandEncoder().createRenderPass(() -> "CrazyPhone call video", textureView, java.util.OptionalInt.empty())) {
            pass.setPipeline(net.minecraft.client.renderer.RenderPipelines.TRACY_BLIT);
            pass.bindSampler("InSampler", target.getColorTextureView());
            pass.draw(0, 3);
        }
        copied = false;
        encoder.copyTextureToBuffer(texture, buffer, 0, () -> copied = true, 0);
        pendingSink = sink;
        pendingSide = side;
        pendingZoom = zoom;
        inFlight = true;
        return true;
    }

    public static void poll() {
        if (!inFlight || !copied)
            return;
        copied = false;
        int w = bufferWidth, h = bufferHeight;
        int side = pendingSide;
        int crop = Math.max(1, Math.round(Math.min(w, h) / Math.max(1f, pendingZoom)));
        int[] rgb;
        try (var view = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)) {
            rgb = downsample(view.data(), w, h, (w - crop) / 2, (h - crop) / 2, crop, crop, side, true);
        }
        deliver(rgb);
    }

    private static void ensureBuffers(int w, int h) {
        if (w == bufferWidth && h == bufferHeight && texture != null)
            return;
        releaseBuffers();
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        texture = device.createTexture("CrazyPhone call video", 10, com.mojang.blaze3d.textures.TextureFormat.RGBA8, w, h, 1, 1);
        textureView = device.createTextureView(texture);
        buffer = device.createBuffer(() -> "CrazyPhone call video buffer", 9, w * h * 4);
        bufferWidth = w;
        bufferHeight = h;
    }

    private static void releaseBuffers() {
        if (textureView != null)
            textureView.close();
        if (texture != null)
            texture.close();
        if (buffer != null)
            buffer.close();
        textureView = null;
        texture = null;
        buffer = null;
        bufferWidth = bufferHeight = -1;
    }
    *///?}
    //? if >=26 <26.2 {
    /*private static com.mojang.blaze3d.textures.GpuTexture texture;
    private static com.mojang.blaze3d.textures.GpuTextureView textureView;
    private static com.mojang.blaze3d.buffers.GpuBuffer buffer;
    private static int bufferWidth = -1;
    private static int bufferHeight = -1;
    private static volatile boolean copied;

    public static boolean capture(RenderTarget target, int side, float zoom, Sink sink) {
        if (inFlight || target.getColorTextureView() == null)
            return false;
        // Keeps the window's aspect ratio (TRACY_BLIT stretches the whole source over the whole output), with
        // the short side at SUPERSAMPLE times the final side.
        int h = side * SUPERSAMPLE;
        int w = Math.max(h, Math.round(h * (float) target.width / Math.max(1, target.height)));
        if (target.height > target.width) {
            w = side * SUPERSAMPLE;
            h = Math.max(w, Math.round(w * (float) target.height / Math.max(1, target.width)));
        }
        ensureBuffers(w, h);
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        try (var pass = device.createCommandEncoder().createRenderPass(() -> "CrazyPhone call video", textureView, java.util.OptionalInt.empty())) {
            pass.setPipeline(net.minecraft.client.renderer.RenderPipelines.TRACY_BLIT);
            pass.bindTexture("InSampler", target.getColorTextureView(),
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
            pass.draw(0, 3);
        }
        copied = false;
        encoder.copyTextureToBuffer(texture, buffer, 0, () -> copied = true, 0);
        pendingSink = sink;
        pendingSide = side;
        pendingZoom = zoom;
        inFlight = true;
        return true;
    }

    public static void poll() {
        if (!inFlight || !copied)
            return;
        copied = false;
        int w = bufferWidth, h = bufferHeight;
        int side = pendingSide;
        int crop = Math.max(1, Math.round(Math.min(w, h) / Math.max(1f, pendingZoom)));
        int[] rgb;
        try (var view = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)) {
            rgb = downsample(view.data(), w, h, (w - crop) / 2, (h - crop) / 2, crop, crop, side, true);
        }
        deliver(rgb);
    }

    private static void ensureBuffers(int w, int h) {
        if (w == bufferWidth && h == bufferHeight && texture != null)
            return;
        releaseBuffers();
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        texture = device.createTexture("CrazyPhone call video", 10, com.mojang.blaze3d.textures.TextureFormat.RGBA8, w, h, 1, 1);
        textureView = device.createTextureView(texture);
        buffer = device.createBuffer(() -> "CrazyPhone call video buffer", 9, (long) w * h * 4);
        bufferWidth = w;
        bufferHeight = h;
    }

    private static void releaseBuffers() {
        if (textureView != null)
            textureView.close();
        if (texture != null)
            texture.close();
        if (buffer != null)
            buffer.close();
        textureView = null;
        texture = null;
        buffer = null;
        bufferWidth = bufferHeight = -1;
    }
    *///?}
    //? if >=26.2 <26.3 {
    /*private static com.mojang.blaze3d.textures.GpuTexture texture;
    private static com.mojang.blaze3d.textures.GpuTextureView textureView;
    private static com.mojang.blaze3d.buffers.GpuBuffer buffer;
    private static int bufferWidth = -1;
    private static int bufferHeight = -1;
    private static volatile boolean copied;

    public static boolean capture(RenderTarget target, int side, float zoom, Sink sink) {
        if (inFlight || target.getColorTextureView() == null)
            return false;
        // Keeps the window's aspect ratio (TRACY_BLIT stretches the whole source over the whole output), with
        // the short side at SUPERSAMPLE times the final side.
        int h = side * SUPERSAMPLE;
        int w = Math.max(h, Math.round(h * (float) target.width / Math.max(1, target.height)));
        if (target.height > target.width) {
            w = side * SUPERSAMPLE;
            h = Math.max(w, Math.round(w * (float) target.height / Math.max(1, target.width)));
        }
        ensureBuffers(w, h);
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        try (var pass = device.createCommandEncoder().createRenderPass(() -> "CrazyPhone call video", textureView, java.util.Optional.empty())) {
            com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(net.minecraft.client.renderer.RenderPipelines.TRACY_BLIT);
            pass.bindTexture("InSampler", target.getColorTextureView(),
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
        copied = false;
        encoder.copyTextureToBuffer(texture, buffer, 0, () -> copied = true, 0);
        pendingSink = sink;
        pendingSide = side;
        pendingZoom = zoom;
        inFlight = true;
        return true;
    }

    public static void poll() {
        if (!inFlight || !copied)
            return;
        copied = false;
        int w = bufferWidth, h = bufferHeight;
        int side = pendingSide;
        int crop = Math.max(1, Math.round(Math.min(w, h) / Math.max(1f, pendingZoom)));
        int[] rgb;
        try (var view = buffer.map(true, false)) {
            rgb = downsample(view.data(), w, h, (w - crop) / 2, (h - crop) / 2, crop, crop, side, true);
        }
        deliver(rgb);
    }

    private static void ensureBuffers(int w, int h) {
        if (w == bufferWidth && h == bufferHeight && texture != null)
            return;
        releaseBuffers();
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        texture = device.createTexture("CrazyPhone call video", 10, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        textureView = device.createTextureView(texture);
        buffer = device.createBuffer(() -> "CrazyPhone call video buffer", 9, (long) w * h * 4);
        bufferWidth = w;
        bufferHeight = h;
    }

    private static void releaseBuffers() {
        if (textureView != null)
            textureView.close();
        if (texture != null)
            texture.close();
        if (buffer != null)
            buffer.close();
        textureView = null;
        texture = null;
        buffer = null;
        bufferWidth = bufferHeight = -1;
    }
    *///?}
    //? if >=26.3 {
    /*private static com.mojang.renderpearl.api.textures.GpuTexture texture;
    private static com.mojang.renderpearl.api.textures.GpuTextureView textureView;
    private static com.mojang.renderpearl.api.buffers.GpuBuffer buffer;
    private static int bufferWidth = -1;
    private static int bufferHeight = -1;
    private static volatile boolean copied;

    public static boolean capture(RenderTarget target, int side, float zoom, Sink sink) {
        if (inFlight || target.getColorTextureView() == null)
            return false;
        // Keeps the window's aspect ratio (TRACY_BLIT stretches the whole source over the whole output), with
        // the short side at SUPERSAMPLE times the final side.
        int h = side * SUPERSAMPLE;
        int w = Math.max(h, Math.round(h * (float) target.width / Math.max(1, target.height)));
        if (target.height > target.width) {
            w = side * SUPERSAMPLE;
            h = Math.max(w, Math.round(w * (float) target.height / Math.max(1, target.width)));
        }
        ensureBuffers(w, h);
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        try (var pass = device.createCommandEncoder().createRenderPass(() -> "CrazyPhone call video", textureView, java.util.Optional.empty())) {
            com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(net.minecraft.client.renderer.RenderPipelines.TRACY_BLIT));
            pass.setUniform("InSampler", target.getColorTextureView(),
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
        copied = false;
        encoder.copyTextureToBuffer(texture, buffer, 0, () -> copied = true, 0);
        pendingSink = sink;
        pendingSide = side;
        pendingZoom = zoom;
        inFlight = true;
        return true;
    }

    public static void poll() {
        if (!inFlight || !copied)
            return;
        copied = false;
        int w = bufferWidth, h = bufferHeight;
        int side = pendingSide;
        int crop = Math.max(1, Math.round(Math.min(w, h) / Math.max(1f, pendingZoom)));
        int[] rgb;
        try (var view = buffer.map(true, false)) {
            rgb = downsample(view.data(), w, h, (w - crop) / 2, (h - crop) / 2, crop, crop, side, true);
        }
        deliver(rgb);
    }

    private static void ensureBuffers(int w, int h) {
        if (w == bufferWidth && h == bufferHeight && texture != null)
            return;
        releaseBuffers();
        var device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        texture = device.createTexture("CrazyPhone call video", 10, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        textureView = device.createTextureView(texture);
        buffer = device.createBuffer(() -> "CrazyPhone call video buffer", 9, (long) w * h * 4);
        bufferWidth = w;
        bufferHeight = h;
    }

    private static void releaseBuffers() {
        if (textureView != null)
            textureView.close();
        if (texture != null)
            texture.close();
        if (buffer != null)
            buffer.close();
        textureView = null;
        texture = null;
        buffer = null;
        bufferWidth = bufferHeight = -1;
    }
    *///?}

    private static void deliver(int[] rgb) {
        Sink sink = pendingSink;
        int side = pendingSide;
        pendingSink = null;
        inFlight = false;
        if (sink != null && rgb != null)
            sink.accept(rgb, side);
    }

    /** Drops whatever is pending (the result would be thrown away anyway). */
    public static void abort() {
        pendingSink = null;
        inFlight = false;
    }

    /** Frees every GPU resource (left a call, video disabled, disconnected). Render thread only. */
    public static void release() {
        abort();
        releaseBuffers();
    }

    /** Area-average of the (cx, cy, cw, ch) region of an RGBA8 buffer down to side x side, flipping rows (GPU
     * readbacks are bottom row first). */
    static int[] downsample(java.nio.ByteBuffer data, int w, int h, int cx, int cy, int cw, int ch, int side, boolean flip) {
        int[] out = new int[side * side];
        for (int oy = 0; oy < side; oy++) {
            int y0 = cy + oy * ch / side, y1 = Math.max(y0 + 1, cy + (oy + 1) * ch / side);
            for (int ox = 0; ox < side; ox++) {
                int x0 = cx + ox * cw / side, x1 = Math.max(x0 + 1, cx + (ox + 1) * cw / side);
                int r = 0, g = 0, b = 0, n = 0;
                for (int y = y0; y < y1; y++) {
                    int row = flip ? h - 1 - y : y;
                    for (int x = x0; x < x1; x++) {
                        int i = (row * w + x) * 4;
                        r += data.get(i) & 0xFF;
                        g += data.get(i + 1) & 0xFF;
                        b += data.get(i + 2) & 0xFF;
                        n++;
                    }
                }
                out[oy * side + ox] = ((r / n) << 16) | ((g / n) << 8) | (b / n);
            }
        }
        return out;
    }
}

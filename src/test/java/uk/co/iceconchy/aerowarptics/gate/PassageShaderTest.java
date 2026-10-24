package uk.co.iceconchy.aerowarptics.gate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shader the mod ships, compiled by a real graphics driver - and every style of gate passage drawn
 * with one, and its pixels read back.
 *
 * <p>A shader that does not compile is the quietest failure this mod has. The game logs it, the lens
 * stands down or the passage is drawn plain, and nothing else ever says so: no crash, no missing texture,
 * only a player who chose a tunnel and gets a fade. And a shader is written blind - no other test here
 * can so much as parse one. So this makes a hidden window, exactly the kind of OpenGL context the game
 * asks for, and hands each program to the driver the way the game does.
 *
 * <p>Then it draws. Every passage style is held to the one rule a style may not break - see
 * {@link GatePassageStyle}: <em>while the veil is whole, nothing of the world shows</em>. That is a
 * statement about the alpha of every pixel, so every pixel's alpha is read, across the whole passage and
 * at more than one shape of screen, an ultrawide among them, where a hole or a wipe sized for sixteen by
 * nine stops short of the corners. The frames are {@link PassageFrame}'s - the ones the game draws from.
 *
 * <p><b>Skipped where no context can be made</b> - a build server with no display - rather than failed:
 * what it would be reporting there is the machine, not the shaders.
 *
 * <p>And none of this says a style looks right. For that, set {@code PASSAGE_SHEETS} to a folder and run
 * it again: it leaves a picture of every style there - see {@link #writeSheets}.
 */
class PassageShaderTest {

    private static final Path CORE = Path.of("src/main/resources/assets/aerowarptics/shaders/core");

    /** A passage as long as the server's default, heard to end on time. */
    private static final double FORECAST = 60.0D;

    private static final double SEED = 2.2D;

    /** Width and height, in pixels, of each view a passage is drawn into: wide, ultrawide, square, and on end. */
    private static final int[][] VIEWS = {{96, 54}, {128, 36}, {48, 48}, {36, 64}};

    private static long window = MemoryUtil.NULL;
    private static boolean initialised;

    @BeforeAll
    static void makeContext() {
        boolean made = false;
        try {
            initialised = GLFW.glfwInit();
            if (initialised) {
                // What the game itself asks for: OpenGL 3.2, core profile, forward compatible.
                GLFW.glfwDefaultWindowHints();
                GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
                window = GLFW.glfwCreateWindow(64, 64, "AeroWarptics shader test", MemoryUtil.NULL, MemoryUtil.NULL);
                if (window != MemoryUtil.NULL) {
                    GLFW.glfwMakeContextCurrent(window);
                    GL.createCapabilities();
                    made = true;
                }
            }
        } catch (Throwable unavailable) {
            made = false;
        }
        Assumptions.assumeTrue(made, "no OpenGL context can be made here, so no shader can be compiled");
    }

    /**
     * Writes a contact sheet of every style into a folder: twelve moments of a passage, laid out four to a
     * row, over a checkerboard standing in for the far side so that the veil thinning can be seen.
     *
     * <p>Not a check - there is no asserting that a tunnel looks like a tunnel - but the only way to see a
     * style without walking through a gate, and the first two versions of four of these were visibly wrong
     * in ways no assertion here would have noticed. Asked for by setting {@code PASSAGE_SHEETS} to a
     * folder before running this test; without it nothing is written.
     */
    private static void writeSheets(Path folder) throws IOException {
        int width = 400;
        int height = 225;
        int columns = 4;
        double[] ages = {0.5D, 4.0D, 9.0D, 16.0D, 28.0D, 40.0D, 52.0D, 56.0D, 59.0D, 60.0D, 65.0D, 70.0D};
        Files.createDirectories(folder);
        for (GatePassageStyle style : shaded()) {
            int program = program(style.shader());
            BufferedImage sheet = new BufferedImage(width * columns, height * ((ages.length + columns - 1) / columns),
                    BufferedImage.TYPE_INT_RGB);
            for (int index = 0; index < ages.length; index++) {
                int[] rgba = draw(program, frame(ages[index]), width, height).rgba();
                int left = index % columns * width;
                int top = index / columns * height;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        // Read back bottom row first; a picture has its top row first.
                        int at = ((height - 1 - y) * width + x) * 4;
                        float solid = rgba[at + 3] / 255.0F;
                        boolean dark = (x / 20 + y / 20) % 2 == 0;
                        int red = Math.round(rgba[at] * solid + (dark ? 70 : 200) * (1.0F - solid));
                        int green = Math.round(rgba[at + 1] * solid + (dark ? 150 : 180) * (1.0F - solid));
                        int blue = Math.round(rgba[at + 2] * solid + (dark ? 60 : 120) * (1.0F - solid));
                        sheet.setRGB(left + x, top + y, red << 16 | green << 8 | blue);
                    }
                }
            }
            ImageIO.write(sheet, "png", folder.resolve(style.name().toLowerCase(Locale.ROOT) + ".png").toFile());
            GL20.glDeleteProgram(program);
        }
    }

    @AfterAll
    static void releaseContext() {
        String sheets = System.getenv("PASSAGE_SHEETS");
        if (window != MemoryUtil.NULL && sheets != null && !sheets.isBlank()) {
            try {
                writeSheets(Path.of(sheets));
            } catch (IOException e) {
                throw new AssertionError("could not write the passage contact sheets to " + sheets, e);
            }
        }
        try {
            if (window != MemoryUtil.NULL) {
                GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
                GL.setCapabilities(null);
                GLFW.glfwDestroyWindow(window);
                window = MemoryUtil.NULL;
            }
            if (initialised) {
                GLFW.glfwTerminate();
            }
        } catch (Throwable ignored) {
            // Nothing to be done about a context that will not go quietly.
        }
    }

    // ------------------------------------------------------------- the programs

    private static JsonObject json(String shader) {
        try {
            return JsonParser.parseString(Files.readString(CORE.resolve(shader + ".json"), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + shader + ".json", e);
        }
    }

    private static String source(JsonObject json, String stage, String extension) {
        String name = json.get(stage).getAsString();
        try {
            return Files.readString(CORE.resolve(name.substring(name.indexOf(':') + 1) + extension), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + name + extension, e);
        }
    }

    private static int compile(int type, String source, String what) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS),
                what + " does not compile:\n" + GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    /** Compiles and links a core shader as the game does: each vertex element bound by name, in order. */
    private static int program(String shader) {
        JsonObject json = json(shader);
        int vertex = compile(GL20.GL_VERTEX_SHADER, source(json, "vertex", ".vsh"), shader + "'s vertex program");
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, source(json, "fragment", ".fsh"), shader + "'s fragment program");
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glBindAttribLocation(program, 1, "UV0");
        GL20.glBindAttribLocation(program, 2, "Color");
        GL20.glLinkProgram(program);
        assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS),
                shader + " does not link:\n" + GL20.glGetProgramInfoLog(program));
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return program;
    }

    private static List<String> shaders() {
        try (Stream<Path> files = Files.list(CORE)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length())).sorted().toList();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Every shader compiles and links, and has every uniform its JSON says it has. A uniform a program
     * declares and never reads is thrown away by the driver, and the game then warns that it cannot find
     * it every time the shader loads - noise that buries the one line that would matter.
     */
    @Test
    void everyCoreShaderCompilesLinksAndKeepsTheUniformsItDeclares() {
        List<String> shaders = shaders();
        assertTrue(shaders.size() >= 7, "core shaders have gone missing: " + shaders);
        for (String shader : shaders) {
            int program = program(shader);
            for (JsonElement uniform : json(shader).getAsJsonArray("uniforms")) {
                String name = uniform.getAsJsonObject().get("name").getAsString();
                assertTrue(GL20.glGetUniformLocation(program, name) >= 0, shader + " declares " + name
                        + " and the linked program has no such uniform - nothing in it reads one");
            }
            GL20.glDeleteProgram(program);
        }
    }

    // --------------------------------------------------------------- the pixels

    /** One view drawn: its pixels as red, green, blue and alpha, 0 to 255, a row at a time. */
    private record Picture(int[] rgba) {

        int pixels() {
            return rgba.length / 4;
        }

        int leastAlpha() {
            int least = 255;
            for (int pixel = 0; pixel < pixels(); pixel++) {
                least = Math.min(least, rgba[pixel * 4 + 3]);
            }
            return least;
        }

        int mostAlpha() {
            int most = 0;
            for (int pixel = 0; pixel < pixels(); pixel++) {
                most = Math.max(most, rgba[pixel * 4 + 3]);
            }
            return most;
        }

        /** The widest any one of red, green and blue ranges across the picture. */
        int spread() {
            int widest = 0;
            for (int channel = 0; channel < 3; channel++) {
                int least = 255;
                int most = 0;
                for (int pixel = 0; pixel < pixels(); pixel++) {
                    least = Math.min(least, rgba[pixel * 4 + channel]);
                    most = Math.max(most, rgba[pixel * 4 + channel]);
                }
                widest = Math.max(widest, most - least);
            }
            return widest;
        }
    }

    private static final float[] IDENTITY = {
            1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};

    /** Draws one frame of a passage into a view of the given size, with nothing blended, and reads it back. */
    private static Picture draw(int program, PassageFrame frame, int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        int target = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER),
                "could not make somewhere to draw into");

        // The whole of the view as one quad, as the painter draws it: position, then texture coordinate.
        int array = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(array);
        int buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {
                -1.0F, -1.0F, 0.0F, 0.0F, 0.0F,
                1.0F, -1.0F, 0.0F, 1.0F, 0.0F,
                -1.0F, 1.0F, 0.0F, 0.0F, 1.0F,
                1.0F, 1.0F, 0.0F, 1.0F, 1.0F}, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 20, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 20, 12L);

        GL20.glUseProgram(program);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ModelViewMat"), false, IDENTITY);
        GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ProjMat"), false, IDENTITY);
        for (Map.Entry<String, float[]> uniform : frame.uniforms(width / (float) height).entrySet()) {
            int location = GL20.glGetUniformLocation(program, uniform.getKey());
            if (location < 0) {
                continue; // not one this style reads
            }
            float[] value = uniform.getValue();
            if (value.length == 2) {
                GL20.glUniform2f(location, value[0], value[1]);
            } else {
                GL20.glUniform1f(location, value[0]);
            }
        }

        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        // Cleared to something no style draws, so a pixel nothing wrote would stand out as half-solid grey.
        GL11.glClearColor(0.5F, 0.5F, 0.5F, 0.5F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);

        ByteBuffer read = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, read);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "drawing raised an OpenGL error");
        int[] rgba = new int[width * height * 4];
        for (int index = 0; index < rgba.length; index++) {
            rgba[index] = read.get(index) & 0xFF;
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL30.glDeleteFramebuffers(target);
        GL11.glDeleteTextures(texture);
        GL15.glDeleteBuffers(buffer);
        GL30.glDeleteVertexArrays(array);
        return new Picture(rgba);
    }

    private static List<GatePassageStyle> shaded() {
        List<GatePassageStyle> shaded = new ArrayList<>();
        for (GatePassageStyle style : GatePassageStyle.values()) {
            if (style.shader() != null) {
                shaded.add(style);
            }
        }
        return shaded;
    }

    /** The frame the game would draw at this age of a passage that ends on time. */
    private static PassageFrame frame(double age) {
        return PassageFrame.at(age, FORECAST, age >= FORECAST ? FORECAST : -1.0D, SEED);
    }

    /**
     * The rule. From the tick a passage begins to the moment the far side starts to show, every pixel of
     * every style is solid - at every shape of view. The traveller is standing at the far gate for all of
     * it, held.
     */
    @Test
    void whileTheVeilIsWholeNothingOfTheWorldShows() {
        for (GatePassageStyle style : shaded()) {
            int program = program(style.shader());
            for (double age = 0.0D; age <= FORECAST; age += 2.5D) {
                PassageFrame frame = frame(age);
                assertEquals(1.0F, frame.veil(), "the veil should be whole at " + age + "t");
                for (int[] view : VIEWS) {
                    assertEquals(255, draw(program, frame, view[0], view[1]).leastAlpha(), style + " lets the world"
                            + " through at " + age + "t of a passage, on a " + view[0] + "x" + view[1] + " view");
                }
            }
            GL20.glDeleteProgram(program);
        }
    }

    /** And once the veil has gone, nothing of the passage is left over the view - not in a corner, either. */
    @Test
    void onceTheVeilHasGoneNothingIsLeftOverTheView() {
        PassageFrame gone = frame(FORECAST + GatePassage.EMERGE_TICKS);
        assertEquals(0.0F, gone.veil());
        for (GatePassageStyle style : shaded()) {
            int program = program(style.shader());
            for (int[] view : VIEWS) {
                assertEquals(0, draw(program, gone, view[0], view[1]).mostAlpha(), style + " is still drawing over"
                        + " the far side after its passage is over, on a " + view[0] + "x" + view[1] + " view");
            }
            GL20.glDeleteProgram(program);
        }
    }

    /** As the veil thins it only thins: the world never shows more, then less, at any one pixel. */
    @Test
    void theVeilThinsAtEveryPixelAndNeverThickens() {
        for (GatePassageStyle style : shaded()) {
            int program = program(style.shader());
            int[] before = draw(program, frame(FORECAST), 96, 54).rgba();
            for (double age = FORECAST + 1.0D; age <= FORECAST + GatePassage.EMERGE_TICKS; age += 1.0D) {
                int[] now = draw(program, frame(age), 96, 54).rgba();
                for (int pixel = 3; pixel < now.length; pixel += 4) {
                    assertTrue(now[pixel] <= before[pixel], style + " covers a pixel again at " + age
                            + "t that it had begun to clear");
                }
                before = now;
            }
            GL20.glDeleteProgram(program);
        }
    }

    /**
     * Whatever a style's ending is - white, or the black behind an iris - it is all there is by the time
     * the far side starts to show. A tunnel still visible then would be seen through the thinning veil.
     */
    @Test
    void everyStyleEndsOnOneFlatField() {
        PassageFrame whole = frame(FORECAST);
        assertEquals(1.0F, whole.flare());
        for (GatePassageStyle style : shaded()) {
            int program = program(style.shader());
            for (int[] view : VIEWS) {
                int spread = draw(program, whole, view[0], view[1]).spread();
                assertTrue(spread <= 3, style + " has not finished ending as the far side begins to show: its"
                        + " picture still ranges over " + spread + " levels on a " + view[0] + "x" + view[1] + " view");
            }
            GL20.glDeleteProgram(program);
        }
    }

    /** And on the way there it draws something. A shader that compiles and paints one colour is not a style. */
    @Test
    void everyStyleDrawsSomethingOnTheWay() {
        for (GatePassageStyle style : shaded()) {
            int program = program(style.shader());
            int spread = 0;
            for (double age = 18.0D; age <= 42.0D; age += 4.0D) {
                spread = Math.max(spread, draw(program, frame(age), 96, 54).spread());
            }
            assertTrue(spread >= 40, style + " is all but one flat colour in the middle of its passage: " + spread);
            GL20.glDeleteProgram(program);
        }
    }

    /** No two frames a tick apart are the same picture: the thing moves. */
    @Test
    void everyStyleMoves() {
        for (GatePassageStyle style : shaded()) {
            int program = program(style.shader());
            int[] one = draw(program, frame(30.0D), 96, 54).rgba();
            int[] two = draw(program, frame(31.0D), 96, 54).rgba();
            int changed = 0;
            for (int index = 0; index < one.length; index++) {
                if (Math.abs(one[index] - two[index]) > 2) {
                    changed++;
                }
            }
            assertTrue(changed > one.length / 200, style + " is the same picture a tick later: only " + changed
                    + " of " + one.length + " values differ");
            GL20.glDeleteProgram(program);
        }
    }
}

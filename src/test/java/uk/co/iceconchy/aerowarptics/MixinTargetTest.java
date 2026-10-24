package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every mixin in this mod is registered, and that every injection in it lands on exactly one place
 * in the game's real bytecode.
 *
 * <p>Neither can be seen without launching the game, and each fails in its own unhelpful way. A mixin
 * class missing from {@code aerowarptics.mixins.json} compiles, ships, and never applies - the storm sky
 * would simply not darken, with nothing in the log. An injection whose target has been misspelt is a
 * crash on the first frame, far from the change that caused it. And an {@code INVOKE} that matches a
 * second call nobody noticed quietly changes that one too.
 *
 * <p>So the mixin classes are read as bytecode - their annotations are not visible to reflection - and
 * checked against the Minecraft classes on the test classpath, which are the same Mojang-named classes
 * the game runs.
 */
class MixinTargetTest {

    private static final Path CONFIG = Path.of("src/main/resources/aerowarptics.mixins.json");
    private static final Path MODS_TOML = Path.of("src/main/templates/META-INF/neoforge.mods.toml");
    private static final Path SOURCES = Path.of("src/main/java");

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String MODIFY_RETURN_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;";
    private static final String MODIFY_EXPRESSION_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
    private static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";

    private static JsonObject config() {
        try {
            return JsonParser.parseString(Files.readString(CONFIG)).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static List<String> registered() {
        JsonObject config = config();
        String pkg = config.get("package").getAsString();
        List<String> names = new ArrayList<>();
        for (String side : List.of("mixins", "client", "server")) {
            if (config.has(side)) {
                for (JsonElement entry : config.getAsJsonArray(side)) {
                    names.add(pkg + "." + entry.getAsString());
                }
            }
        }
        return names;
    }

    private static ClassNode read(String internalName) {
        try (InputStream in = MixinTargetTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, internalName + " is not on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) {
            all.addAll(visible);
        }
        if (invisible != null) {
            all.addAll(invisible);
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                return (T) annotation.values.get(i + 1);
            }
        }
        return null;
    }

    @Test
    void theModLoaderIsToldAboutTheConfig() throws IOException {
        String toml = Files.readString(MODS_TOML);
        assertTrue(toml.contains("[[mixins]]") && toml.contains("config = \"${mod_id}.mixins.json\""),
                "neoforge.mods.toml does not declare the mixin config, so no mixin in it will ever apply");
        assertEquals("aerowarptics.mixins.json", CONFIG.getFileName().toString(),
                "the config's file name must be the mod id's, which is what the template names");
    }

    @Test
    void everyMixinClassIsRegisteredAndEveryRegistrationExists() throws IOException {
        String pkg = config().get("package").getAsString();
        Path dir = SOURCES.resolve(pkg.replace('.', '/'));
        Set<String> onDisk = new HashSet<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = dir.relativize(file).toString().replace('\\', '/').replace('/', '.');
                onDisk.add(pkg + "." + relative.substring(0, relative.length() - ".java".length()));
            }
        }
        Set<String> listed = new HashSet<>(registered());
        Set<String> unlisted = new HashSet<>(onDisk);
        unlisted.removeAll(listed);
        Set<String> missing = new HashSet<>(listed);
        missing.removeAll(onDisk);
        assertTrue(unlisted.isEmpty(), "mixins that will never apply, because the config does not list them: " + unlisted);
        assertTrue(missing.isEmpty(), "the config lists mixins that do not exist: " + missing);
        assertTrue(!onDisk.isEmpty(), "found no mixins at all - has the package moved?");
    }

    @Test
    void everyInjectionLandsOnExactlyOnePlaceInTheGame() {
        int checked = 0;
        List<String> problems = new ArrayList<>();

        for (String mixinName : registered()) {
            ClassNode mixin = read(mixinName.replace('.', '/'));
            AnnotationNode mixinAnnotation = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)
                    .stream().filter(a -> a.desc.equals(MIXIN)).findFirst().orElse(null);
            assertNotNull(mixinAnnotation, mixinName + " has no @Mixin");
            List<Type> targets = value(mixinAnnotation, "value");
            assertNotNull(targets, mixinName + " names no target class");

            for (MethodNode handler : mixin.methods) {
                for (AnnotationNode injector : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                    boolean returns = injector.desc.equals(MODIFY_RETURN_VALUE);
                    boolean argument = injector.desc.equals(MODIFY_ARG);
                    boolean wraps = injector.desc.equals(WRAP_OPERATION);
                    if (!returns && !argument && !wraps && !injector.desc.equals(MODIFY_EXPRESSION_VALUE)) {
                        continue;
                    }
                    List<String> methodNames = value(injector, "method");
                    // At[] on most injectors, a lone At on @ModifyArg.
                    Object atValue = value(injector, "at");
                    AnnotationNode at = atValue instanceof List<?> ats ? (AnnotationNode) ats.get(0) : (AnnotationNode) atValue;
                    String kind = value(at, "value");
                    String invoke = value(at, "target");
                    Integer ordinal = value(at, "ordinal");

                    for (Type target : targets) {
                        ClassNode game = read(target.getInternalName());
                        for (String methodName : methodNames) {
                            checked++;
                            String where = mixinName + "." + handler.name + " -> "
                                    + target.getClassName() + "." + methodName;
                            List<MethodNode> matches = game.methods.stream()
                                    .filter(m -> m.name.equals(methodName)).toList();
                            if (matches.size() != 1) {
                                problems.add(where + ": " + matches.size() + " methods by that name, expected exactly one");
                                continue;
                            }
                            MethodNode method = matches.get(0);
                            Type[] handlerArgs = Type.getArgumentTypes(handler.desc);

                            if (returns) {
                                if (!Type.getReturnType(method.desc).equals(handlerArgs[0])) {
                                    problems.add(where + ": returns " + Type.getReturnType(method.desc)
                                            + " but the handler takes " + handlerArgs[0]);
                                }
                            } else if ("INVOKE".equals(kind)) {
                                int calls = countCalls(method, invoke);
                                // An ordinal names one call of several; without one there must be exactly one.
                                if (ordinal == null ? calls != 1 : calls <= ordinal) {
                                    problems.add(where + ": " + calls + " calls to " + invoke + ", expected "
                                            + (ordinal == null ? "exactly one" : "more than " + ordinal));
                                }
                                String callDesc = invoke.substring(invoke.indexOf('('));
                                if (argument) {
                                    problems.addAll(checkArgument(where, injector, handler, callDesc));
                                } else if (wraps) {
                                    problems.addAll(checkWrap(where, handler, invoke));
                                } else if (!Type.getReturnType(callDesc).equals(handlerArgs[0])) {
                                    problems.add(where + ": the call returns a different type from the one the handler takes");
                                }
                            } else {
                                problems.add(where + ": unchecked injection point " + kind + " - teach this test about it");
                            }

                            if (!argument && !wraps) {
                                problems.addAll(checkCapturedArguments(where, handler, method));
                            }
                        }
                    }
                }
            }
        }

        // Problems first: a broken target and a short count together should report the target.
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(checked >= 6, "only checked " + checked + " injections - has the annotation reading broken?");
    }

    /**
     * A {@code @Local(argsOnly = true)} parameter is matched by type among the target's arguments, so
     * that type has to appear there exactly once or the capture is ambiguous.
     */
    /** A @ModifyArg takes and returns exactly the type of the argument it names. */
    private static List<String> checkArgument(String where, AnnotationNode injector, MethodNode handler, String callDesc) {
        List<String> problems = new ArrayList<>();
        Integer index = value(injector, "index");
        Type[] callArgs = Type.getArgumentTypes(callDesc);
        if (index == null || index < 0 || index >= callArgs.length) {
            problems.add(where + ": names argument " + index + " of a call that takes " + callArgs.length);
            return problems;
        }
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        if (handlerArgs.length < 1 || !handlerArgs[0].equals(callArgs[index])
                || !Type.getReturnType(handler.desc).equals(callArgs[index])) {
            problems.add(where + ": argument " + index + " is a " + callArgs[index] + " but the handler is "
                    + handler.desc);
        }
        return problems;
    }

    /** A @WrapOperation of an instance call takes the receiver, the call's arguments, then the Operation. */
    private static List<String> checkWrap(String where, MethodNode handler, String invoke) {
        List<String> problems = new ArrayList<>();
        Type owner = Type.getType(invoke.substring(0, invoke.indexOf(';') + 1));
        String callDesc = invoke.substring(invoke.indexOf('('));
        Type[] callArgs = Type.getArgumentTypes(callDesc);
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        if (handlerArgs.length != callArgs.length + 2 || !handlerArgs[0].equals(owner)
                || !handlerArgs[handlerArgs.length - 1].getDescriptor().equals(OPERATION)) {
            problems.add(where + ": a wrap of " + invoke + " must take (" + owner + ", its arguments, Operation)");
            return problems;
        }
        for (int i = 0; i < callArgs.length; i++) {
            if (!handlerArgs[i + 1].equals(callArgs[i])) {
                problems.add(where + ": argument " + i + " is a " + callArgs[i] + " but the handler takes " + handlerArgs[i + 1]);
            }
        }
        if (!Type.getReturnType(handler.desc).equals(Type.getReturnType(callDesc))) {
            problems.add(where + ": returns " + Type.getReturnType(handler.desc) + " in place of a call returning "
                    + Type.getReturnType(callDesc));
        }
        return problems;
    }

    private static List<String> checkCapturedArguments(String where, MethodNode handler, MethodNode target) {
        List<String> problems = new ArrayList<>();
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        Type[] targetArgs = Type.getArgumentTypes(target.desc);
        for (int index = 1; index < handlerArgs.length; index++) {
            List<AnnotationNode> parameter = new ArrayList<>();
            if (handler.visibleParameterAnnotations != null && handler.visibleParameterAnnotations[index] != null) {
                parameter.addAll(handler.visibleParameterAnnotations[index]);
            }
            if (handler.invisibleParameterAnnotations != null && handler.invisibleParameterAnnotations[index] != null) {
                parameter.addAll(handler.invisibleParameterAnnotations[index]);
            }
            boolean captured = parameter.stream().anyMatch(a -> a.desc.equals(LOCAL));
            if (!captured) {
                problems.add(where + ": handler parameter " + index + " is not a @Local capture");
                continue;
            }
            Type wanted = handlerArgs[index];
            long matching = Stream.of(targetArgs).filter(wanted::equals).count();
            if (matching != 1) {
                problems.add(where + ": captures a " + wanted + " argument, but the target has " + matching);
            }
        }
        return problems;
    }

    private static int countCalls(MethodNode method, String target) {
        String owner = target.substring(1, target.indexOf(';'));
        String name = target.substring(target.indexOf(';') + 1, target.indexOf('('));
        String desc = target.substring(target.indexOf('('));
        int calls = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
                calls++;
            }
        }
        return calls;
    }
}

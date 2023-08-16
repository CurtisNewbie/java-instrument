package com.curtisnewbie.agent.perf;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * This may have huge performance impact if too many classes are intercepted,
 * in such case, the benchmark is simply not reliable.
 * <p>
 * We should always specify the type and method precisely.
 *
 * @author yongj.zhuang
 */
public class PerfAgent {

    public static Advice timingAdvice = Advice.to(TimingAdvice.class);

    public static final boolean debug = false; // debug for byte-buddy
    public static final Set<String> typeNames = new HashSet<>();
    public static final Set<String> methodNames = new HashSet<>();

    public static void premain(String arguments, Instrumentation instrumentation) {
        final AgentBuilder.Identified.Extendable t = new AgentBuilder.Default()
                .type(typeMatcher())
                .transform((builder, type, classLoader, module) ->
                        builder.visit(timingAdvice.on(methodMatcher())));

        if (debug) {
            t.with(AgentBuilder.Listener.StreamWriting.toSystemOut()) // for debugging
                    .installOn(instrumentation);
            return;
        }

        t.installOn(instrumentation);
    }

    public static ElementMatcher<? super MethodDescription> methodMatcher() {
        ElementMatcher.Junction<? super MethodDescription> tm = not(isAbstract());

        for (String methodName : methodNames) {
            tm = tm.and(nameStartsWith(methodName));
        }

        return tm;
    }

    public static ElementMatcher<? super TypeDescription> typeMatcher() {
        ElementMatcher.Junction<? super TypeDescription> tm = not(isSynthetic())
                .and(not(nameStartsWith("java.")))
                .and(not(nameStartsWith("net.bytebuddy")))
                .and(not(nameStartsWith("sun.reflect")))
                .and(not(nameStartsWith("com.sun")));

        for (String typeName : typeNames) {
            tm = tm.and(nameStartsWith(typeName));
        }

        return tm;
    }

    public static class TimingAdvice {
        public static final String MAIN = "main";
        public static ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
        public static final long UNIT = 1_000_000L; // ms to ns

        @Advice.OnMethodEnter
        static long enter() {
            if (!Thread.currentThread().getName().equalsIgnoreCase(MAIN)) return 0L;

            depth.set(depth.get() + 1);
            return System.nanoTime();
        }

        @Advice.OnMethodExit
        static void exit(@Advice.Enter long time, @Advice.Origin String origin, @Advice.AllArguments() Object[] args) {
            if (!Thread.currentThread().getName().equalsIgnoreCase(MAIN)) return;

            long took = (System.nanoTime() - time);
            Object[] copy = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !(args[i] instanceof Class) && !isPrimitive(args[i].getClass())) {
                    copy[i] = args[i].getClass().getName();
                } else {
                    copy[i] = args[i];
                }
            }
            System.out.printf("Perf, %d, %s %s, %d, ns%s\n", depth.get(), origin, Arrays.toString(copy), took, (took > UNIT ? " " + (took / UNIT) + "ms" : ""));
            depth.set(depth.get() - 1);
        }

        public static boolean isPrimitive(Class<?> type) {
            return type.isPrimitive() ||
                    type == Double.class || type == Float.class || type == Long.class ||
                    type == Integer.class || type == Short.class || type == Character.class ||
                    type == Byte.class || type == Boolean.class || type == String.class;
        }
    }
}

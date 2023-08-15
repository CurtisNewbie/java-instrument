package com.curtisnewbie.agent.perf;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * @author yongj.zhuang
 */
public class PerfAgent {

    public static Advice timingAdvice = Advice.to(TimingAdvice.class);

    public static void premain(String arguments, Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .type(not(nameStartsWith("java."))
                        .and(not(isSynthetic()))
                        .and(not(nameStartsWith("org.slf4j")))
                        .and(not(nameStartsWith("net.bytebuddy")))
                        .and(not(nameStartsWith("org.springframework.util")))
                        .and(not(nameStartsWith("sun.reflect")))
                        .and(not(nameStartsWith("com.sun")))
                        .and(not(nameContains("logging")))
                        .and(not(nameContains("BeanDefinition")))
                        .and(not(nameContains("ResolvableType")))
                        .and(not(nameStartsWith("cn.hutool")))
                        .and(not(nameStartsWith("org.yaml.snakeyaml")))
                        .and(not(nameStartsWith("com.fasterxml")))
                        .and(not(nameStartsWith("org.springframework.core.env")))
                        .and(not(nameStartsWith("org.springframework.core.convert")))
                        .and(not(nameStartsWith("org.springframework.boot.context.properties")))
                )
                .transform((builder, type, classLoader, module) ->
                        builder.visit(timingAdvice.on(isPublic()
                                .and(not(isAbstract()))
                                .and(not(isStatic()))))
                )
//                .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
                .installOn(instrumentation);
    }

    public static class TimingAdvice {

        public static ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
        public static final long UNIT = 1000_000L;
        public static final long THRESHOLD = 1_000_000_000L;
//        static final long THRESHOLD = 0L;

        @Advice.OnMethodEnter
        static long enter() {
            if (!Thread.currentThread().getName().equalsIgnoreCase("main"))
                return 0L;

            depth.set(depth.get() + 1);
            return System.nanoTime();
        }

        @Advice.OnMethodExit
        static void exit(@Advice.Enter long time, @Advice.Origin String origin, @Advice.AllArguments() Object[] args) {
            if (!Thread.currentThread().getName().equalsIgnoreCase("main"))
                return;

            long took = (System.nanoTime() - time);
            if (took >= THRESHOLD) {
                Object[] copy = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    if (args[i] != null && !(args[i] instanceof Class) && !isPrimitive(args[i].getClass())) {
                        copy[i] = args[i].getClass().getName();
                    } else {
                        copy[i] = args[i];
                    }
                }
                System.out.printf("Perf, %d, %s %s, %d, ms\n", depth.get(), origin, Arrays.toString(copy), took / UNIT);
            }
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

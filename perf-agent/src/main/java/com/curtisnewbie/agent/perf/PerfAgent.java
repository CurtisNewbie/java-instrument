package com.curtisnewbie.agent.perf;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * @author yongj.zhuang
 */
public class PerfAgent {

    public static Advice timingAdvice = Advice.to(TimingAdvice.class);

    public static void premain(String arguments, Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .type(not(nameStartsWith("java."))
                        .and(not(nameStartsWith("net.bytebuddy")))
                        .and(not(nameStartsWith("org.springframework.util")))
                        .and(not(nameStartsWith("sun.reflect")))
                        .and(not(isSynthetic()))
                        .and(not(nameContains("logging"))))
                .transform((builder, type, classLoader, module) ->
                        builder.visit(timingAdvice.on(isPublic()
                                .and(not(isAbstract()))
                                .and(not(isStatic()))))
                )
//                .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
                .installOn(instrumentation);
    }

    static class TimingAdvice {

        static final long SEC = 1000_000_000L;
        static final long THRESHOLD = SEC;
        static final boolean logAny = false;

        @Advice.OnMethodEnter
        static long enter() {
            return System.nanoTime();
        }

        @Advice.OnMethodExit
        static void exit(@Advice.Enter long time, @Advice.Origin String origin) {
            if (!Thread.currentThread().getName().equalsIgnoreCase("main"))
                return;

            if (logAny) {
                long took = (System.nanoTime() - time);
                System.out.printf("Perf - %s ->> %s took %s ns\n", Thread.currentThread().getName(), origin, took);
                return;
            }

            long took = (System.nanoTime() - time);
            if (took > THRESHOLD) {
                System.out.printf("Perf - %s ->> %s took %s seconds\n", Thread.currentThread().getName(), origin, took / SEC);
            }
        }
    }
}

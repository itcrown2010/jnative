package io.jnative.processor;

import io.jnative.meta.MetaApiVersion;
import io.jnative.processor.builtin.CliProcessor;
import io.jnative.processor.builtin.ConfigProcessor;
import io.jnative.processor.builtin.DiProcessor;
import io.jnative.processor.builtin.EntityProcessor;
import io.jnative.processor.builtin.JsonProcessor;
import io.jnative.processor.builtin.RouteProcessor;
import io.jnative.processor.builtin.RpcMetaProcessor;
import io.jnative.processor.builtin.ValidationProcessor;

import java.util.ArrayList;
import java.util.List;

/** Discovers processors from explicit registry / package metadata (no classpath scan). */
public final class ProcessorRegistry {

    private ProcessorRegistry() {
    }

    public static List<AnnotationProcessor> builtin() {
        List<AnnotationProcessor> list = new ArrayList<>();
        list.add(new JsonProcessor());
        list.add(new ValidationProcessor());
        list.add(new ConfigProcessor());
        list.add(new CliProcessor());
        list.add(new DiProcessor());
        list.add(new EntityProcessor());
        list.add(new RouteProcessor());
        list.add(new RpcMetaProcessor());
        for (AnnotationProcessor p : list) {
            MetaApiVersion.checkCompatible(p.minProcessorApi(), p.maxProcessorApi());
        }
        return list;
    }
}

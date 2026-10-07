package io.jnative.processor;

import io.jnative.meta.ClassModel;
import io.jnative.meta.ProgramModel;
import io.jnative.meta.TypeId;

import java.util.ArrayList;
import java.util.List;

public final class RoundEnvironment {
    private final ProgramModel program;
    private final int round;
    private final boolean lastRound;

    public RoundEnvironment(ProgramModel program, int round, boolean lastRound) {
        this.program = program;
        this.round = round;
        this.lastRound = lastRound;
    }

    public ProgramModel program() {
        return program;
    }

    public int round() {
        return round;
    }

    public boolean processingOver() {
        return lastRound;
    }

    public List<ClassModel> getElementsAnnotatedWith(TypeId annotation) {
        return program.findTypesAnnotatedWith(annotation.simpleName());
    }

    public List<ClassModel> getElementsAnnotatedWith(String annotationSimpleName) {
        return program.findTypesAnnotatedWith(annotationSimpleName);
    }

    public List<ClassModel> rootElements() {
        return new ArrayList<>(program.types());
    }
}

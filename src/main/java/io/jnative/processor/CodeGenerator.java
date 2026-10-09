package io.jnative.processor;

import java.util.ArrayList;
import java.util.List;

/** Escape hatch + structured builders for generated JNative source.
 * @author 张晓达
 */
public final class CodeGenerator {
    private final List<GeneratedArtifact> artifacts = new ArrayList<>();
    private final String processorName;
    private final String processorVersion;

    public CodeGenerator(String processorName, String processorVersion) {
        this.processorName = processorName;
        this.processorVersion = processorVersion;
    }

    public SourceFileBuilder newSourceFile(String relativePath) {
        return new SourceFileBuilder(this, relativePath);
    }

    public void createSourceFile(
            String relativePath,
            String content,
            String originSource,
            String originSymbol,
            String inputFingerprint
    ) {
        GeneratedArtifact art = new GeneratedArtifact(
                GeneratedArtifact.Kind.SOURCE,
                relativePath,
                content,
                processorName,
                processorVersion,
                originSource,
                originSymbol,
                inputFingerprint);
        artifacts.add(art);
    }

    public void createResource(
            String relativePath,
            String content,
            String originSource,
            String originSymbol,
            String inputFingerprint
    ) {
        artifacts.add(new GeneratedArtifact(
                GeneratedArtifact.Kind.RESOURCE,
                relativePath,
                content,
                processorName,
                processorVersion,
                originSource,
                originSymbol,
                inputFingerprint));
    }

    public void createMetadata(
            String relativePath,
            String content,
            String originSource,
            String originSymbol,
            String inputFingerprint
    ) {
        artifacts.add(new GeneratedArtifact(
                GeneratedArtifact.Kind.METADATA,
                relativePath,
                content,
                processorName,
                processorVersion,
                originSource,
                originSymbol,
                inputFingerprint));
    }

    void add(GeneratedArtifact artifact) {
        artifacts.add(artifact);
    }

    public List<GeneratedArtifact> artifacts() {
        return List.copyOf(artifacts);
    }
}

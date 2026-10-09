package io.jnative.layout;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.PrimitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class ObjectLayoutTest {

    @Test
    void headerThenAlignedFields() {
        ClassSymbol cls = new ClassSymbol("Person", SourceLocation.UNKNOWN, false);
        cls.addField(new FieldSymbol(cls, "age", PrimitiveType.INT, 0, SourceLocation.UNKNOWN));
        cls.addField(new FieldSymbol(cls, "id", PrimitiveType.LONG, 1, SourceLocation.UNKNOWN));
        ClassLayout layout = ObjectLayoutBuilder.build(cls);
        assertEquals(8, layout.headerSize());
        assertEquals(8, layout.field("age").orElseThrow().offset());
        assertEquals(16, layout.field("id").orElseThrow().offset());
        assertEquals(1, layout.field("age").orElseThrow().index());
        assertEquals(2, layout.field("id").orElseThrow().index());
        assertEquals(1, layout.fieldGepBaseIndex());
    }

    @Test
    void emptyClassStillHasHeader() {
        ClassSymbol cls = new ClassSymbol("Empty", SourceLocation.UNKNOWN, false);
        ClassLayout layout = ObjectLayoutBuilder.build(cls);
        assertEquals(8, layout.headerSize());
        assertTrue(layout.instanceSize() >= 8);
    }

    @Test
    void subclassPreservesParentFieldOffsets() {
        ClassSymbol animal = new ClassSymbol("Animal", SourceLocation.UNKNOWN, false);
        animal.addField(new FieldSymbol(animal, "age", PrimitiveType.INT, 0, SourceLocation.UNKNOWN));
        animal.setLayout(ObjectLayoutBuilder.build(animal));

        ClassSymbol dog = new ClassSymbol("Dog", SourceLocation.UNKNOWN, false);
        dog.setSuperClass(animal);
        dog.addField(new FieldSymbol(dog, "weight", PrimitiveType.INT, 0, SourceLocation.UNKNOWN));
        dog.setLayout(ObjectLayoutBuilder.build(dog));

        long animalAge = animal.layout().field("age").orElseThrow().offset();
        long dogAge = dog.layout().field("age").orElseThrow().offset();
        assertEquals(animalAge, dogAge);
        assertTrue(dog.layout().field("weight").orElseThrow().offset() > dogAge);
    }
}

package io.jnative.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class TypeSystemTest {

    @Test
    void primitiveWidening() {
        assertTrue(PrimitiveType.LONG.isAssignableFrom(PrimitiveType.INT));
        assertTrue(PrimitiveType.DOUBLE.isAssignableFrom(PrimitiveType.INT));
        assertFalse(PrimitiveType.INT.isAssignableFrom(PrimitiveType.BOOLEAN));
    }

    @Test
    void nullAssignableToClass() {
        ClassSymbol person = new ClassSymbol("Person", io.jnative.diagnostics.SourceLocation.UNKNOWN, false);
        ClassType type = person.asType();
        assertTrue(type.isAssignableFrom(NullType.INSTANCE));
        assertFalse(PrimitiveType.INT.isAssignableFrom(NullType.INSTANCE));
    }

    @Test
    void referenceTypes() {
        ClassSymbol person = new ClassSymbol("Person", io.jnative.diagnostics.SourceLocation.UNKNOWN, false);
        assertTrue(person.asType().isReference());
        assertFalse(PrimitiveType.INT.isReference());
        assertTrue(NullType.INSTANCE.isReference());
    }

    @Test
    void subtypeAssignable() {
        ClassSymbol animal = new ClassSymbol("Animal", io.jnative.diagnostics.SourceLocation.UNKNOWN, false);
        ClassSymbol dog = new ClassSymbol("Dog", io.jnative.diagnostics.SourceLocation.UNKNOWN, false);
        dog.setSuperClass(animal);
        assertTrue(animal.asType().isAssignableFrom(dog.asType()));
        assertFalse(dog.asType().isAssignableFrom(animal.asType()));
    }
}

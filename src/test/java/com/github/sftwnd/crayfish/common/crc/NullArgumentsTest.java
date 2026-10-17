/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The messages of the exceptions of the arguments name the method called, also where it delegates: null at every
 * object parameter of every public method and constructor of the public types, found by reflection, so that a method
 * added later is checked too; a part outside the data; parameters that do not fit into the width
 */
class NullArgumentsTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the public types of the library; their public member types are found by reflection
    private static final Class<?>[] TYPES = {
        CrcParameters.class, StandardCrc.class, CrcDescription.class, CrcModel.class, CRC.class, CrcChecksum.class
    };

    // a valid value of every type of a parameter and of the object of an instance method, a new one for every call.
    // A type without a sample fails the test, so that a method with a parameter of a new type is not skipped
    private static final Map<Class<?>, Supplier<Object>> SAMPLES = new HashMap<>();

    static {
        SAMPLES.put(boolean.class, () -> false);
        SAMPLES.put(int.class, () -> 0);
        SAMPLES.put(long.class, () -> 0L);
        SAMPLES.put(String.class, () -> "CRC-32");
        SAMPLES.put(byte[].class, CHECK::clone);
        SAMPLES.put(ByteBuffer.class, () -> ByteBuffer.wrap(CHECK.clone()));
        SAMPLES.put(CrcParameters.class, StandardCrc.CRC_32_ISO_HDLC::parameters);
        SAMPLES.put(StandardCrc.class, () -> StandardCrc.CRC_32_ISO_HDLC);
        SAMPLES.put(CrcDescription.class, () -> CrcModel.of("CRC-32").description());
        SAMPLES.put(CrcModel.class, () -> CrcModel.of("CRC-32"));
        SAMPLES.put(CrcModel.Builder.class, () -> CrcModel.builder(32));
        SAMPLES.put(CRC.class, () -> CrcModel.of("CRC-32").crc(CHECK));
        SAMPLES.put(CrcChecksum.class, () -> CrcModel.of("CRC-32").checksum());
    }

    // the name of a type in the messages: CrcModel, CrcModel.Builder
    private static String name(final Class<?> type) {
        Class<?> enclosing = type.getEnclosingClass();
        return enclosing == null ? type.getSimpleName() : name(enclosing) + '.' + type.getSimpleName();
    }

    // a method or a constructor in the messages of the test, for example CrcModel.crc(byte[], int)
    private static String describe(final Executable executable) {
        String type = name(executable.getDeclaringClass());
        String parameters = Arrays.stream(executable.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "));
        return (executable instanceof Constructor ? "new " + type : type + '.' + executable.getName()) + '(' + parameters + ')';
    }

    // the type and its public member types
    private static void addTypes(final Class<?> type, final List<Class<?>> types) {
        types.add(type);
        for (Class<?> member : type.getDeclaredClasses()) {
            if (Modifier.isPublic(member.getModifiers())) {
                addTypes(member, types);
            }
        }
    }

    // equals(Object), which takes null, and valueOf(String), which the compiler gives an enum with values(), which has
    // no parameter
    private static boolean isSkipped(final Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        if ("equals".equals(method.getName())) {
            return Arrays.equals(parameters, new Class<?>[]{Object.class});
        }
        return method.getDeclaringClass().isEnum() && "valueOf".equals(method.getName()) && Arrays.equals(parameters, new Class<?>[]{String.class});
    }

    // the public methods and constructors of the type with an object parameter, but for the skipped ones
    private static List<Executable> executables(final Class<?> type) {
        List<Executable> executables = new ArrayList<>(Arrays.asList(type.getConstructors()));
        for (Method method : type.getDeclaredMethods()) {
            // a synthetic method, a bridge or the method of a lambda, is not a method of the source
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic() && !isSkipped(method)) {
                executables.add(method);
            }
        }
        executables.removeIf(executable -> Arrays.stream(executable.getParameterTypes()).allMatch(Class::isPrimitive));
        return executables;
    }

    // a new sample of the type
    private static Object sample(final Class<?> type) {
        Supplier<Object> sample = SAMPLES.get(type);
        assertNotNull(sample, "No sample of " + type.getTypeName() + ": add a sample of the type to NullArgumentsTest.SAMPLES");
        return sample.get();
    }

    // the call of the method or of the constructor: an exception of its own is thrown as it is
    private static void call(final Executable executable, final Object target, final Object[] arguments) throws ReflectiveOperationException {
        try {
            if (executable instanceof Constructor) {
                ((Constructor<?>) executable).newInstance(arguments);
            } else {
                ((Method) executable).invoke(target, arguments);
            }
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    // null at the index and samples at the other positions: NullPointerException with the message
    // <Class>::<method> - <parameter> is null, the method new for a constructor. The classes are compiled without the
    // names of the parameters, so the name in the message is checked to be a name, not to be the name of the parameter
    private static void assertNullArgument(final Executable executable, final int index) {
        Class<?>[] types = executable.getParameterTypes();
        Object[] arguments = new Object[types.length];
        for (int position = 0; position < types.length; position++) {
            arguments[position] = position == index ? null : sample(types[position]);
        }
        Class<?> type = executable.getDeclaringClass();
        Object target = executable instanceof Method && !Modifier.isStatic(executable.getModifiers()) ? sample(type) : null;
        String invocation = describe(executable) + " with null at the position " + index;
        NullPointerException exception = assertThrows(NullPointerException.class, () -> call(executable, target, arguments),
                invocation + " has to throw NullPointerException");
        String source = name(type) + "::" + (executable instanceof Constructor ? "new" : executable.getName());
        String message = String.valueOf(exception.getMessage());
        assertTrue(message.matches(Pattern.quote(source + " - ") + "[a-z]\\w* is null"),
                invocation + ": the message has to be \"" + source + " - <parameter> is null\", not \"" + message + '"');
    }

    // null at every object parameter, one at a time
    private static void assertNullArguments(final Executable executable) {
        Class<?>[] types = executable.getParameterTypes();
        for (int index = 0; index < types.length; index++) {
            if (!types[index].isPrimitive()) {
                assertNullArgument(executable, index);
            }
        }
    }

    @Test
    void testNullArguments() {
        List<Class<?>> types = new ArrayList<>();
        for (Class<?> type : TYPES) {
            addTypes(type, types);
        }
        Set<String> called = new HashSet<>();
        for (Class<?> type : types) {
            for (Executable executable : executables(type)) {
                assertNullArguments(executable);
                called.add(describe(executable));
            }
        }
        // the methods that named the method they delegate to, and a method of every other type: the search is checked
        // as well
        for (String expected : new String[]{"CrcModel.of(String)", "CrcModel.crc(byte[])", "CrcModel.crc(byte[], int)",
                "CrcModel.crc(byte[], int, int)", "CrcDescription.model(String)", "CrcDescription.model(String, boolean)",
                "StandardCrc.find(String)", "CRC.combine(CRC)", "CrcChecksum.update(ByteBuffer)"}) {
            assertTrue(called.contains(expected), expected + " has to be called with null");
        }
    }

    // the calculation of a part of the data
    @FunctionalInterface
    private interface Part {

        void calculate(byte[] data, int offset, int length);

    }

    // a part outside the data of 16 bytes: ArrayIndexOutOfBoundsException with the message of the method called
    private static void assertOutside(final String source, final Part part, final int offset, final int length) {
        byte[] data = new byte[16];
        ArrayIndexOutOfBoundsException exception = assertThrows(ArrayIndexOutOfBoundsException.class, () -> part.calculate(data, offset, length),
                source + ": the offset " + offset + " and the length " + length + " have to be rejected");
        assertEquals(source + " - offset " + offset + " and length " + length + " out of the buffer length 16", exception.getMessage(), "Wrong message");
    }

    // the first bytes of the data of 16 bytes outside the data: the part at the offset 0
    private static void assertFirstOutside(final String source, final ObjIntConsumer<byte[]> first, final int length) {
        assertOutside(source, (data, offset, size) -> first.accept(data, size), 0, length);
    }

    @Test
    void testPartOutsideTheData() {
        CrcModel model = CrcModel.of("CRC-32");
        CRC crc = model.crc(CHECK);
        CrcChecksum checksum = model.checksum();
        // a negative offset, a negative length, an end beyond the data and an end beyond Integer.MAX_VALUE
        int[][] parts = {{-1, 8}, {0, -1}, {10, 7}, {Integer.MAX_VALUE, 2}};
        for (int[] part : parts) {
            assertOutside("CrcModel::crc", model::crc, part[0], part[1]);
            assertOutside("CrcModel::value", model::value, part[0], part[1]);
            assertOutside("CRC::update", crc::update, part[0], part[1]);
            assertOutside("CrcChecksum::update", checksum::update, part[0], part[1]);
        }
        for (int length : new int[]{-1, 17}) {
            assertFirstOutside("CrcModel::crc", model::crc, length);
            assertFirstOutside("CrcModel::value", model::value, length);
            assertFirstOutside("CRC::update", crc::update, length);
        }
        assertEquals(model.check(), crc.value(), "A rejected part has not got to change the CRC");
        assertEquals(0, checksum.length(), "A rejected part has not got to change the checksum");
    }

    @Test
    void testParametersOutsideTheWidth() {
        // CrcModel.of(width, ...) checks the parameters itself: the messages name it, not CrcParameters.of
        IllegalArgumentException width = assertThrows(IllegalArgumentException.class, () -> CrcModel.of(65, 0x1L, 0x0L, false, false, 0x0L),
                "The width 65 has to be rejected");
        assertEquals("CrcModel::of - width has to be in 1..64: 65", width.getMessage(), "Wrong message");
        IllegalArgumentException poly = assertThrows(IllegalArgumentException.class, () -> CrcModel.of(16, 0x18005L, 0x0L, true, true, 0x0L),
                "A poly with the x^16 term has to be rejected");
        assertEquals("CrcModel::of - poly 0x18005 does not fit into the width 16 (poly without the x^16 term)", poly.getMessage(), "Wrong message");
        // an int literal 0xFFFFFFFF is -1, sign-extended to all 64 bits
        IllegalArgumentException init = assertThrows(IllegalArgumentException.class,
                () -> CrcModel.of(32, 0x04C11DB7L, 0xFFFFFFFF, true, true, 0xFFFFFFFFL), "A sign-extended init has to be rejected");
        assertEquals("CrcModel::of - init 0xFFFFFFFFFFFFFFFF does not fit into the width 32 (an int literal is sign-extended: "
                + "use a long literal such as 0xFFFFFFFFL)", init.getMessage(), "Wrong message");
        IllegalArgumentException xorout = assertThrows(IllegalArgumentException.class, () -> CrcModel.of(8, 0x07L, 0x0L, false, false, 0x100L),
                "A xorout above the width has to be rejected");
        assertEquals("CrcModel::of - xorout 0x100 does not fit into the width 8", xorout.getMessage(), "Wrong message");
    }

}

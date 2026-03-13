package no.kompilator.situs.runtime;

import no.kompilator.situs.annotations.CsvSource;
import no.kompilator.situs.annotations.EmptySource;
import no.kompilator.situs.annotations.EnumSource;
import no.kompilator.situs.annotations.MethodSource;
import no.kompilator.situs.annotations.NullAndEmptySource;
import no.kompilator.situs.annotations.NullSource;
import no.kompilator.situs.annotations.ParameterizedTest;
import no.kompilator.situs.annotations.Test;
import no.kompilator.situs.annotations.ValueSource;
import no.kompilator.situs.domain.TestCaseDefinition;
import no.kompilator.situs.params.Arguments;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class TestDefinitionResolver {

    List<TestCaseDefinition> resolveTestCases(Class<?> suiteClass) {
        validateLifecycleMethods(suiteClass);
        return Arrays.stream(suiteClass.getDeclaredMethods())
                .filter(this::isTestMethod)
                .sorted(Comparator
                        .comparingInt(this::resolveOrder)
                        .thenComparing(Method::getName))
                .flatMap(method -> toTestCaseDefinitions(suiteClass, method).stream())
                .collect(Collectors.toList());
    }

    private List<TestCaseDefinition> toTestCaseDefinitions(Class<?> suiteClass, Method method) {
        boolean plain = method.isAnnotationPresent(Test.class);
        boolean parameterized = method.isAnnotationPresent(ParameterizedTest.class);
        if (plain && parameterized) {
            throw new IllegalArgumentException("Method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' cannot declare both @Test and @ParameterizedTest");
        }
        if (plain) {
            Test testAnn = method.getAnnotation(Test.class);
            validateRegularTestMethod(suiteClass, method, testAnn);
            String testName = testAnn.name().isEmpty() ? method.getName() : testAnn.name();
            long timeoutMs = resolveTimeoutMs(suiteClass, testName, testAnn.timeoutMs(), testAnn.timeout());
            validateTestConfiguration(suiteClass, testName, timeoutMs, testAnn.delayMs(), testAnn.retries());
            return List.of(new TestCaseDefinition(testName, testAnn.description(), method,
                    timeoutMs, testAnn.delayMs(), testAnn.retries()));
        }

        ParameterizedTest testAnn = method.getAnnotation(ParameterizedTest.class);
        validateParameterizedTestMethod(suiteClass, method, testAnn);
        List<Object[]> argumentSets = resolveArgumentSets(suiteClass, method);
        if (argumentSets.isEmpty()) {
            throw new IllegalArgumentException("No arguments resolved for @ParameterizedTest method '"
                    + method.getName() + "' in suite '" + suiteClass.getName() + "'");
        }

        long timeoutMs = resolveTimeoutMs(suiteClass, method.getName(), testAnn.timeoutMs(), testAnn.timeout());
        validateTestConfiguration(suiteClass, method.getName(), timeoutMs, testAnn.delayMs(), testAnn.retries());
        List<TestCaseDefinition> definitions = new ArrayList<>(argumentSets.size());
        for (int i = 0; i < argumentSets.size(); i++) {
            Object[] arguments = argumentSets.get(i);
            validateResolvedArguments(suiteClass, method, arguments);
            String invocationName = formatParameterizedName(testAnn, method, i + 1, arguments);
            definitions.add(new TestCaseDefinition(invocationName, testAnn.description(), method,
                    timeoutMs, testAnn.delayMs(), testAnn.retries(), arguments));
        }
        return definitions;
    }

    private boolean isTestMethod(Method method) {
        return method.isAnnotationPresent(Test.class) || method.isAnnotationPresent(ParameterizedTest.class);
    }

    private int resolveOrder(Method method) {
        if (method.isAnnotationPresent(Test.class)) {
            return method.getAnnotation(Test.class).order();
        }
        if (method.isAnnotationPresent(ParameterizedTest.class)) {
            return method.getAnnotation(ParameterizedTest.class).order();
        }
        return 0;
    }

    private void validateRegularTestMethod(Class<?> suiteClass, Method method, Test testAnn) {
        validateAnnotatedMethodShape(suiteClass, method, "@Test");
        if (method.getParameterCount() != 0) {
            throw new IllegalArgumentException("@Test method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must not declare parameters");
        }
    }

    private void validateParameterizedTestMethod(Class<?> suiteClass, Method method, ParameterizedTest testAnn) {
        validateAnnotatedMethodShape(suiteClass, method, "@ParameterizedTest");
        if (method.getParameterCount() == 0) {
            throw new IllegalArgumentException("@ParameterizedTest method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must declare at least one parameter");
        }
        int sourceCount = countSources(method);
        if (sourceCount == 0) {
            throw new IllegalArgumentException("@ParameterizedTest method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must declare at least one argument source");
        }
        if (method.isAnnotationPresent(NullSource.class) || method.isAnnotationPresent(EmptySource.class)
                || method.isAnnotationPresent(NullAndEmptySource.class)) {
            if (method.getParameterCount() != 1) {
                throw new IllegalArgumentException("@NullSource/@EmptySource can only be used on single-argument "
                        + "@ParameterizedTest method '" + method.getName() + "' in suite '" + suiteClass.getName() + "'");
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType.isPrimitive() && (method.isAnnotationPresent(NullSource.class)
                    || method.isAnnotationPresent(NullAndEmptySource.class))) {
                throw new IllegalArgumentException("Null arguments cannot be used with primitive parameter '"
                        + method.getParameters()[0].getName() + "' on method '" + method.getName()
                        + "' in suite '" + suiteClass.getName() + "'");
            }
        }
    }

    private int countSources(Method method) {
        int count = 0;
        count += method.isAnnotationPresent(ValueSource.class) ? 1 : 0;
        count += method.isAnnotationPresent(CsvSource.class) ? 1 : 0;
        count += method.isAnnotationPresent(MethodSource.class) ? 1 : 0;
        count += method.isAnnotationPresent(EnumSource.class) ? 1 : 0;
        count += method.isAnnotationPresent(NullSource.class) ? 1 : 0;
        count += method.isAnnotationPresent(EmptySource.class) ? 1 : 0;
        count += method.isAnnotationPresent(NullAndEmptySource.class) ? 1 : 0;
        return count;
    }

    private List<Object[]> resolveArgumentSets(Class<?> suiteClass, Method method) {
        List<Object[]> resolved = new ArrayList<>();
        if (method.isAnnotationPresent(NullSource.class) || method.isAnnotationPresent(NullAndEmptySource.class)) {
            resolved.add(new Object[]{null});
        }
        if (method.isAnnotationPresent(EmptySource.class) || method.isAnnotationPresent(NullAndEmptySource.class)) {
            resolved.add(new Object[]{emptyValueFor(method)});
        }
        if (method.isAnnotationPresent(ValueSource.class)) {
            resolved.addAll(resolveValueSourceArguments(suiteClass, method));
        }
        if (method.isAnnotationPresent(CsvSource.class)) {
            resolved.addAll(resolveCsvSourceArguments(suiteClass, method));
        }
        if (method.isAnnotationPresent(MethodSource.class)) {
            resolved.addAll(resolveMethodSourceArguments(suiteClass, method));
        }
        if (method.isAnnotationPresent(EnumSource.class)) {
            resolved.addAll(resolveEnumSourceArguments(suiteClass, method));
        }
        return resolved;
    }

    private List<Object[]> resolveValueSourceArguments(Class<?> suiteClass, Method method) {
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException("@ValueSource requires exactly one parameter on method '"
                    + method.getName() + "' in suite '" + suiteClass.getName() + "'");
        }
        ValueSource source = method.getAnnotation(ValueSource.class);
        List<Object> values = new ArrayList<>();
        addArrayValues(values, source.shorts());
        addArrayValues(values, source.bytes());
        addArrayValues(values, source.ints());
        addArrayValues(values, source.longs());
        addArrayValues(values, source.floats());
        addArrayValues(values, source.doubles());
        addArrayValues(values, source.chars());
        addArrayValues(values, source.booleans());
        values.addAll(Arrays.asList(source.strings()));
        values.addAll(Arrays.asList(source.classes()));

        long populatedAttributes = Stream.of(
                        source.shorts().length,
                        source.bytes().length,
                        source.ints().length,
                        source.longs().length,
                        source.floats().length,
                        source.doubles().length,
                        source.chars().length,
                        source.booleans().length,
                        source.strings().length,
                        source.classes().length)
                .filter(length -> length > 0)
                .count();
        if (populatedAttributes != 1) {
            throw new IllegalArgumentException("@ValueSource on method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must declare exactly one non-empty attribute");
        }
        return values.stream().map(value -> new Object[]{value}).toList();
    }

    private List<Object[]> resolveCsvSourceArguments(Class<?> suiteClass, Method method) {
        CsvSource source = method.getAnnotation(CsvSource.class);
        return Arrays.stream(source.value())
                .map(row -> parseCsvRow(row, source.delimiter()))
                .map(values -> convertCsvRowToArguments(suiteClass, method, values))
                .toList();
    }

    private List<Object[]> resolveMethodSourceArguments(Class<?> suiteClass, Method method) {
        MethodSource source = method.getAnnotation(MethodSource.class);
        String[] providerNames = source.value().length == 0 ? new String[]{method.getName()} : source.value();
        List<Object[]> resolved = new ArrayList<>();
        for (String providerName : providerNames) {
            Method provider = findProviderMethod(suiteClass, providerName);
            Object providerInstance = Modifier.isStatic(provider.getModifiers()) ? null : instantiateProviderOwner(suiteClass);
            Object provided;
            try {
                provider.setAccessible(true);
                provided = provider.invoke(providerInstance);
            } catch (IllegalAccessException | InvocationTargetException e) {
                Throwable cause = e instanceof InvocationTargetException invocationTargetException
                        && invocationTargetException.getCause() != null
                        ? invocationTargetException.getCause()
                        : e;
                throw new IllegalArgumentException("@MethodSource provider '" + providerName + "' in suite '"
                        + suiteClass.getName() + "' failed: " + cause.getMessage(), cause);
            }
            resolved.addAll(extractProvidedArguments(suiteClass, method, providerName, provided));
        }
        return resolved;
    }

    private Method findProviderMethod(Class<?> suiteClass, String providerName) {
        return Arrays.stream(suiteClass.getDeclaredMethods())
                .filter(method -> method.getName().equals(providerName))
                .filter(method -> method.getParameterCount() == 0)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No zero-argument @MethodSource provider named '"
                        + providerName + "' found in suite '" + suiteClass.getName() + "'"));
    }

    private Object instantiateProviderOwner(Class<?> suiteClass) {
        try {
            var constructor = suiteClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Non-static @MethodSource providers in suite '" + suiteClass.getName()
                    + "' require an accessible no-arg constructor", e);
        }
    }

    private List<Object[]> extractProvidedArguments(
            Class<?> suiteClass, Method testMethod, String providerName, Object provided) {
        if (provided == null) {
            throw new IllegalArgumentException("@MethodSource provider '" + providerName + "' in suite '"
                    + suiteClass.getName() + "' returned null");
        }

        List<Object> rawValues = new ArrayList<>();
        if (provided instanceof Stream<?> stream) {
            rawValues.addAll(stream.toList());
        } else if (provided instanceof Iterable<?> iterable) {
            iterable.forEach(rawValues::add);
        } else if (provided instanceof Iterator<?> iterator) {
            iterator.forEachRemaining(rawValues::add);
        } else if (provided.getClass().isArray()) {
            int length = Array.getLength(provided);
            for (int i = 0; i < length; i++) {
                rawValues.add(Array.get(provided, i));
            }
        } else {
            throw new IllegalArgumentException("@MethodSource provider '" + providerName + "' in suite '"
                    + suiteClass.getName() + "' must return a Stream, Iterable, Iterator, or array");
        }

        return rawValues.stream()
                .map(value -> normalizeProvidedArgument(testMethod, value))
                .toList();
    }

    private Object[] normalizeProvidedArgument(Method testMethod, Object value) {
        if (value instanceof Arguments arguments) {
            return arguments.values();
        }
        if (testMethod.getParameterCount() == 1) {
            return new Object[]{value};
        }
        if (value instanceof Object[] values) {
            return Arrays.copyOf(values, values.length);
        }
        throw new IllegalArgumentException("@MethodSource for method '" + testMethod.getName()
                + "' must emit Arguments or Object[] for multi-parameter tests");
    }

    private List<Object[]> resolveEnumSourceArguments(Class<?> suiteClass, Method method) {
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException("@EnumSource requires exactly one parameter on method '"
                    + method.getName() + "' in suite '" + suiteClass.getName() + "'");
        }
        EnumSource source = method.getAnnotation(EnumSource.class);
        List<? extends Enum<?>> constants = Arrays.asList(source.value().getEnumConstants());
        Set<String> names = new LinkedHashSet<>(Arrays.asList(source.names()));
        Stream<? extends Enum<?>> stream = constants.stream();
        if (!names.isEmpty()) {
            stream = source.mode() == EnumSource.Mode.INCLUDE
                    ? stream.filter(value -> names.contains(value.name()))
                    : stream.filter(value -> !names.contains(value.name()));
        }
        return stream.map(value -> new Object[]{value}).toList();
    }

    private Object[] convertCsvRowToArguments(Class<?> suiteClass, Method method, List<String> values) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length != values.size()) {
            throw new IllegalArgumentException("@CsvSource row for method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' declares " + values.size() + " values but method expects "
                    + parameters.length);
        }
        Object[] converted = new Object[values.size()];
        for (int i = 0; i < values.size(); i++) {
            converted[i] = convertValue(values.get(i), parameters[i].getType(), "CSV value");
        }
        return converted;
    }

    private List<String> parseCsvRow(String row, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char currentChar = row.charAt(i);
            if (currentChar == '\'') {
                quoted = !quoted;
                continue;
            }
            if (currentChar == delimiter && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        values.add(current.toString().trim());
        return values;
    }

    private Object emptyValueFor(Method method) {
        Class<?> parameterType = method.getParameterTypes()[0];
        if (parameterType == String.class) {
            return "";
        }
        if (parameterType.isArray()) {
            return Array.newInstance(parameterType.getComponentType(), 0);
        }
        if (List.class.isAssignableFrom(parameterType) || Collection.class == parameterType) {
            return List.of();
        }
        if (Set.class.isAssignableFrom(parameterType)) {
            return Set.of();
        }
        if (Map.class.isAssignableFrom(parameterType)) {
            return Map.of();
        }
        throw new IllegalArgumentException("@EmptySource on method '" + method.getName()
                + "' supports String, arrays, List, Set, Collection, and Map parameters");
    }

    private void validateResolvedArguments(Class<?> suiteClass, Method method, Object[] arguments) {
        Parameter[] parameters = method.getParameters();
        if (parameters.length != arguments.length) {
            throw new IllegalArgumentException("Resolved " + arguments.length + " argument(s) for method '"
                    + method.getName() + "' in suite '" + suiteClass.getName() + "', but method expects "
                    + parameters.length);
        }
        for (int i = 0; i < parameters.length; i++) {
            Object converted = convertValue(arguments[i], parameters[i].getType(), "argument " + i);
            arguments[i] = converted;
        }
    }

    private Object convertValue(Object rawValue, Class<?> targetType, String label) {
        if (rawValue == null) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("Cannot assign null to primitive " + targetType.getSimpleName()
                        + " parameter for " + label);
            }
            return null;
        }

        Class<?> wrapperType = wrap(targetType);
        if (wrapperType.isInstance(rawValue)) {
            return rawValue;
        }
        if (rawValue instanceof String stringValue) {
            return convertString(stringValue, targetType, label);
        }
        if (targetType.isEnum() && rawValue instanceof Enum<?> enumValue && targetType.isInstance(enumValue)) {
            return enumValue;
        }
        if (targetType == String.class) {
            return String.valueOf(rawValue);
        }
        throw new IllegalArgumentException("Cannot convert value '" + rawValue + "' to " + targetType.getSimpleName()
                + " for " + label);
    }

    private Object convertString(String value, Class<?> targetType, String label) {
        if ("null".equalsIgnoreCase(value) && !targetType.isPrimitive()) {
            return null;
        }
        if (targetType == String.class) {
            return value;
        }
        if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(value);
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(value);
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(value);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (targetType == char.class || targetType == Character.class) {
            if (value.length() != 1) {
                throw new IllegalArgumentException("Expected a single character for " + label);
            }
            return value.charAt(0);
        }
        if (targetType == Class.class) {
            try {
                return Class.forName(value);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Unable to load class '" + value + "' for " + label, e);
            }
        }
        if (targetType.isEnum()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object enumValue = Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), value);
            return enumValue;
        }
        throw new IllegalArgumentException("Unsupported conversion to " + targetType.getSimpleName()
                + " for " + label);
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private String formatParameterizedName(ParameterizedTest annotation, Method method, int index, Object[] arguments) {
        String template = annotation.name().isBlank()
                ? method.getName() + " [" + index + "]"
                : annotation.name();
        String joinedArguments = Arrays.stream(arguments)
                .map(this::renderValue)
                .collect(Collectors.joining(", "));
        String formatted = template.replace("{index}", String.valueOf(index))
                .replace("{arguments}", joinedArguments);
        for (int i = 0; i < arguments.length; i++) {
            formatted = formatted.replace("{" + i + "}", renderValue(arguments[i]));
        }
        if (Objects.equals(formatted, template)
                && !template.contains("{index}")
                && !template.contains("{arguments}")) {
            return formatted + " [" + index + "]";
        }
        return formatted;
    }

    private String renderValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value.getClass().isArray()) {
            return IntStream.range(0, Array.getLength(value))
                    .mapToObj(i -> renderValue(Array.get(value, i)))
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        return String.valueOf(value);
    }

    private void validateLifecycleMethods(Class<?> suiteClass) {
        validateLifecycleMethods(suiteClass, no.kompilator.situs.annotations.BeforeAll.class, "@BeforeAll");
        validateLifecycleMethods(suiteClass, no.kompilator.situs.annotations.BeforeEach.class, "@BeforeEach");
        validateLifecycleMethods(suiteClass, no.kompilator.situs.annotations.AfterEach.class, "@AfterEach");
        validateLifecycleMethods(suiteClass, no.kompilator.situs.annotations.AfterAll.class, "@AfterAll");
    }

    private void validateLifecycleMethods(
            Class<?> suiteClass, Class<? extends Annotation> annotationType, String label) {
        Arrays.stream(suiteClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(annotationType))
                .forEach(method -> {
                    validateAnnotatedMethodShape(suiteClass, method, label);
                    if (method.getParameterCount() != 0) {
                        throw new IllegalArgumentException(label + " method '" + method.getName() + "' in suite '"
                                + suiteClass.getName() + "' must not declare parameters");
                    }
                });
    }

    private void validateAnnotatedMethodShape(Class<?> suiteClass, Method method, String annotationLabel) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException(annotationLabel + " method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must be public");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException(annotationLabel + " method '" + method.getName() + "' in suite '"
                    + suiteClass.getName() + "' must not be static");
        }
    }

    private void validateTestConfiguration(
            Class<?> suiteClass, String testName, long timeoutMs, long delayMs, int retries) {
        if (timeoutMs < -1) {
            throw new IllegalArgumentException("Invalid timeoutMs for test '" + testName + "' in suite '"
                    + suiteClass.getName() + "': " + timeoutMs + " (must be -1, 0, or > 0)");
        }
        if (delayMs < 0) {
            throw new IllegalArgumentException("Invalid delayMs for test '" + testName + "' in suite '"
                    + suiteClass.getName() + "': " + delayMs + " (must be >= 0)");
        }
        if (retries < 0) {
            throw new IllegalArgumentException("Invalid retries for test '" + testName + "' in suite '"
                    + suiteClass.getName() + "': " + retries + " (must be >= 0)");
        }
    }

    private long resolveTimeoutMs(Class<?> suiteClass, String testName, long timeoutMs, String timeout) {
        String trimmedTimeout = timeout == null ? "" : timeout.trim();
        if (!trimmedTimeout.isEmpty() && timeoutMs != 0) {
            throw new IllegalArgumentException("Test '" + testName + "' in suite '" + suiteClass.getName()
                    + "' cannot declare both timeoutMs and timeout");
        }
        if (trimmedTimeout.isEmpty()) {
            return timeoutMs;
        }
        try {
            Duration duration = Duration.parse(trimmedTimeout);
            return duration.isNegative() ? -1 : duration.toMillis();
        } catch (ArithmeticException | DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid timeout for test '" + testName + "' in suite '"
                    + suiteClass.getName() + "': " + trimmedTimeout + " (must be an ISO-8601 duration)", e);
        }
    }

    private void addArrayValues(List<Object> target, Object array) {
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            target.add(Array.get(array, i));
        }
    }
}

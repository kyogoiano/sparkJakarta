package spark.utils;

import java.lang.reflect.Field;

/**
 * Utility class for reflection operations in tests.
 * Replaces PowerMock's Whitebox functionality.
 * Supports both instance and static fields.
 */
public class ReflectionTestUtils
{
    /**
     * Get the value of a private field from an object.
     *
     * @param target    The object containing the field (or Class for static fields)
     * @param fieldName The name of the field to read
     * @param <T>       The type of the field value
     * @return The value of the field
     * @throws RuntimeException if the field cannot be accessed
     */
    @SuppressWarnings("unchecked")
    public static <T> T getField(Object target, String fieldName)
    {
        try {
            Class<?> clazz = (target instanceof Class) ? (Class<?>) target : target.getClass();
            Field field = findField(clazz, fieldName);
            field.setAccessible(true);
            return (T) field.get((target instanceof Class) ? null : target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            String className = (target instanceof Class) ? ((Class<?>) target).getName() : target.getClass().getName();
            throw new RuntimeException("Failed to get field '" + fieldName + "' from " + className, e);
        }
    }

    /**
     * Set the value of a private field in an object.
     *
     * @param target    The object containing the field (or Class for static fields)
     * @param fieldName The name of the field to set
     * @param value     The value to set
     * @throws RuntimeException if the field cannot be accessed
     */
    public static void setField(Object target, String fieldName, Object value)
    {
        try {
            Class<?> clazz = (target instanceof Class) ? (Class<?>) target : target.getClass();
            Field field = findField(clazz, fieldName);
            field.setAccessible(true);
            field.set((target instanceof Class) ? null : target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            String className = (target instanceof Class) ? ((Class<?>) target).getName() : target.getClass().getName();
            throw new RuntimeException("Failed to set field '" + fieldName + "' in " + className, e);
        }
    }

    /**
     * Find a field in a class or its superclasses.
     *
     * @param clazz     The class to search
     * @param fieldName The name of the field
     * @return The field
     * @throws NoSuchFieldException if the field is not found
     */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException
    {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + clazz.getName() + " or its superclasses");
    }
}

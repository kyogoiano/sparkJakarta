package sparkTest.util;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class FieldUtil {
    public static void modifyField(final Field field, final Object value) throws Exception{

        field.setAccessible(true);

        int mods = field.getModifiers();
        if (Modifier.isFinal(mods)) {
            throw new Exception("Please note it is impossible to modify final fields!!!");
        }

        field.set(null, value);
    }
}

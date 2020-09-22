package com.qsmaxmin.plugin.transforms;

import com.android.build.api.transform.TransformException;
import com.qsmaxmin.annotation.properties.Property;
import com.qsmaxmin.plugin.helper.TransformHelper;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/8/13 14:50
 * @Description
 */
public class PropertyTransform {
    private static final String readPropertyMethodName  = "readPropertiesByQsPlugin";
    private static final String savePropertyMethodName  = "savePropertiesByQsPlugin";
    private static final String clearPropertyMethodName = "clearPropertiesByQsPlugin";

    private static void println(String text) {
        TransformHelper.println("\t\t> " + text);
    }

    public static void transform(CtClass clazz, String rootPath) throws Exception {
        println("transform class(@AutoProperty) :" + clazz.getName());
        if (clazz.isFrozen()) clazz.defrost();

        CtMethod baseReadMethod = null;
        CtMethod baseSaveMethod = null;
        CtMethod baseClearMethod = null;
        CtMethod[] methods = clazz.getMethods();
        for (CtMethod m : methods) {
            if (readPropertyMethodName.equals(m.getName())) {
                baseReadMethod = m;
            } else if (savePropertyMethodName.equals(m.getName())) {
                baseSaveMethod = m;
            } else if (clearPropertyMethodName.equals(m.getName())) {
                baseClearMethod = m;
            }
        }
        if (baseReadMethod == null || baseSaveMethod == null || baseClearMethod == null) {
            throw new TransformException("transform error, class '" + clazz.getName() + "' should extends 'QsProperties' !!!");
        }
        CtMethod readMethod = new CtMethod(baseReadMethod, clazz, null);
        CtMethod saveMethod = new CtMethod(baseSaveMethod, clazz, null);
        CtMethod clearMethod = new CtMethod(baseClearMethod, clazz, null);

        StringBuilder readSB = new StringBuilder("{");
        StringBuilder saveSB = new StringBuilder("{");
        StringBuilder clearSB = new StringBuilder("{");
        CtField[] fields = clazz.getDeclaredFields();
        for (CtField field : fields) {
            Object ann;
            try {
                ann = field.getAnnotation(Property.class);
            } catch (Exception ignored) {
                continue;
            }
            if (ann == null) continue;
            Property property = (Property) ann;
            String fieldName = field.getName();
            String key = property.value().length() == 0 ? fieldName : property.value();
            String typeName = field.getType().getName();

            String nameTag = getMethodNameByType(typeName);
            if (nameTag == null) {
                readSB.append(fieldName).append(" = (").append(typeName).append(")getObject($1, \"").append(key).append("\", ").append(typeName).append(".class);");
                saveSB.append("putObject(\"").append(key).append("\", ").append(fieldName).append(");");
            } else {
                String getMethodName = "get" + nameTag;
                String putMethodName = "put" + nameTag;
                readSB.append(fieldName).append(" = ").append(getMethodName).append("($1, \"").append(key).append("\");");
                saveSB.append(putMethodName).append("(\"").append(key).append("\", ").append(fieldName).append(");");
            }
            clearSB.append(fieldName).append(" = ").append(getEmptyValueByType(typeName)).append(";");
        }

        readMethod.setBody(readSB.append('}').toString());
        saveMethod.setBody(saveSB.append('}').toString());
        clearMethod.setBody(clearSB.append('}').toString());

        clazz.addMethod(readMethod);
        clazz.addMethod(saveMethod);
        clazz.addMethod(clearMethod);

        clazz.writeFile(rootPath);
    }

    private static String getEmptyValueByType(String typeName) {
        switch (typeName) {
            case "int":
            case "long":
            case "float":
            case "byte":
            case "short":
            case "double":
            case "char":
                return "0";
            case "boolean":
                return "false";
            default:
                return "null";
        }
    }

    private static String getMethodNameByType(String typeName) {
        switch (typeName) {
            case "java.lang.String":
                return "String";

            case "int":
                return "Int";
            case "java.lang.Integer":
                return "Int2";

            case "long":
                return "Long";
            case "java.lang.Long":
                return "Long2";

            case "boolean":
                return "Boolean";
            case "java.lang.Boolean":
                return "Boolean2";

            case "float":
                return "Float";
            case "java.lang.Float":
                return "Float2";

            case "byte":
                return "Byte";
            case "java.lang.Byte":
                return "Byte2";

            case "short":
                return "Short";
            case "java.lang.Short":
                return "Short2";

            case "double":
                return "Double";
            case "java.lang.Double":
                return "Double2";

            case "char":
                return "Char";
            case "java.lang.Character":
                return "Char2";
            default:
                return null;
        }
    }
}

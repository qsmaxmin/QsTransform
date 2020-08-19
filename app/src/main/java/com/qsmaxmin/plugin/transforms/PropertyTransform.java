package com.qsmaxmin.plugin.transforms;

import com.android.build.api.transform.TransformException;
import com.qsmaxmin.annotation.properties.Property;
import com.qsmaxmin.plugin.TransformHelper;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;

/**
 * @CreateBy administrator
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

    public static void transform(CtClass clazz, String rootPath, String filePath) throws Exception {
        println("transform class(@AutoProperty) :" + filePath);
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
            throw new TransformException("transform error, class '" + filePath + "' should extends 'QsProperties' !!!");
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

            String getMethodName = getMethodNameByType(typeName, true);
            String putMethodName = getMethodNameByType(typeName, false);
            if (getMethodName != null) {
                readSB.append(fieldName).append(" = ").append(getMethodName).append("($1, \"").append(key).append("\");");
                saveSB.append(putMethodName).append("(\"").append(key).append("\", ").append(fieldName).append(");");
                clearSB.append(fieldName).append(" = ").append(getEmptyValueByType(typeName)).append(";");
            } else {
                println("@Property " + typeName + " " + fieldName + "; not support!!!");
            }
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
            case "java.lang.String":
            case "java.lang.Integer":
            case "java.lang.Long":
            case "java.lang.Boolean":
            case "java.lang.Float":
            case "java.util.Set":
                return "null";
            case "int":
            case "long":
            case "float":
                return "0";
            case "boolean":
                return "false";
            default:
                return null;
        }
    }

    private static String getMethodNameByType(String typeName, boolean isGet) {
        String name;
        switch (typeName) {
            case "java.lang.String":
                name = "String";
                break;
            case "int":
                name = "Int";
                break;
            case "java.lang.Integer":
                name = "Int2";
                break;
            case "long":
                name = "Long";
                break;
            case "java.lang.Long":
                name = "Long2";
                break;
            case "boolean":
                name = "Boolean";
                break;
            case "java.lang.Boolean":
                name = "Boolean2";
                break;
            case "float":
                name = "Float";
                break;
            case "java.lang.Float":
                name = "Float2";
                break;
            case "java.util.Set":
                name = "StringSet";
                break;
            default:
                return null;
        }
        return (isGet ? "get" : "put") + name;
    }
}

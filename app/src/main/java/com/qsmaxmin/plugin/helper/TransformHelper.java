package com.qsmaxmin.plugin.helper;

import java.io.IOException;
import java.io.InputStream;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * @CreateBy administrator
 * @Date 2020/8/5 17:56
 * @Description
 */
public class TransformHelper {
    private static TransformHelper helper;
    private static boolean         enableLog;
    private        ClassPool       classPool;

    public TransformHelper() {
        ClassPool.cacheOpenedJarFile = false;
        classPool = new ClassPool(null);
        classPool.appendSystemPath();

    }

    public static TransformHelper getInstance() {
        if (helper == null) {
            synchronized (TransformHelper.class) {
                if (helper == null) helper = new TransformHelper();
            }
        }
        return helper;
    }

    public void appendClassPath(String pathName) throws NotFoundException {
        classPool.appendClassPath(pathName);
    }

    public CtClass makeClass(String className) {
        return classPool.makeClass(className);
    }

    public CtClass makeClass(InputStream classFile, boolean ifNotFrozen) throws IOException {
        return classPool.makeClass(classFile, ifNotFrozen);
    }

    public CtClass makeClassIfNotExists(String className) {
        CtClass clazz = classPool.getOrNull(className);
        if (clazz == null) {
            return classPool.makeClass(className);
        } else {
            return clazz;
        }
    }

    public CtClass get(String className) throws NotFoundException {
        return classPool.get(className);
    }

    public static boolean hasField(CtClass ctClass, String fieldName) {
        try {
            return ctClass.getDeclaredField(fieldName) != null;
        } catch (NotFoundException ignored) {
            return false;
        }
    }

    public static boolean hasField(CtClass ctClass, CtField field) {
        try {
            return ctClass.getDeclaredField(field.getName()) != null;
        } catch (NotFoundException ignored) {
            return false;
        }
    }

    public static boolean hasDeclaredMethod(CtClass ctClass, String methodName) {
        try {
            return ctClass.getDeclaredMethod(methodName) != null;
        } catch (NotFoundException ignored) {
            return false;
        }
    }

    public static boolean hasDeclaredMethod(CtClass ctClass, CtMethod method) {
        try {
            return ctClass.getDeclaredMethod(method.getName()) != null;
        } catch (NotFoundException ignored) {
            return false;
        }
    }

    public static boolean hasConstructor(CtClass ctClass) {
        try {
            CtConstructor[] constructors = ctClass.getDeclaredConstructors();
            return constructors != null && constructors.length != 0;
        } catch (Exception ignored) {
            return false;
        }
    }


    public static void addField(CtClass clazz, CtField field) throws Exception {
        deleteField(clazz, field.getName());
        clazz.addField(field);
    }

    public static void addMethod(CtClass clazz, CtMethod method) throws Exception {
        deleteMethod(clazz, method.getName());
        clazz.addMethod(method);
    }

    public static void addConstructor(CtClass clazz, CtConstructor constructor) throws Exception {
        deleteConstructor(clazz);
        clazz.addConstructor(constructor);
    }

    public static void deleteField(CtClass clazz, String fieldName) {
        try {
            CtField field = clazz.getDeclaredField(fieldName);
            if (field != null) {
                clazz.removeField(field);
            }
        } catch (Exception ignored) {
        }
    }

    public static void deleteMethod(CtClass clazz, String methodName) {
        try {
            CtMethod method = clazz.getDeclaredMethod(methodName);
            if (method != null) {
                clazz.removeMethod(method);
            }
        } catch (Exception ignored) {
        }
    }

    public static void deleteConstructor(CtClass clazz) {
        try {
            CtConstructor[] constructors = clazz.getDeclaredConstructors();
            if (constructors != null && constructors.length > 0) {
                for (CtConstructor c : constructors) {
                    clazz.removeConstructor(c);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static CtMethod getMethod(CtClass clazz, String methodName) {
        CtMethod[] methods = clazz.getMethods();
        if (methods != null && methods.length > 0) {
            for (CtMethod method : methods) {
                if (method.getName().equals(methodName)) return method;
            }
        }
        return null;
    }

    public static CtMethod getDeclaredMethod(CtClass clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (NotFoundException ignored) {
        }
        return null;
    }

    public static boolean hasInterface(CtClass clazz, String interfaceName) {
        try {
            CtClass[] interfaces = clazz.getInterfaces();
            if (interfaces != null && interfaces.length > 0) {
                for (CtClass cl : interfaces) {
                    if (cl.getName().equals(interfaceName)) {
                        return true;
                    }
                }
            }
        } catch (NotFoundException ignored) {
        }
        return false;
    }

    public static void println(String text) {
        if (enableLog) System.out.println(text);
    }

    public static void enableLog(boolean showLog) {
        enableLog = showLog;
    }

    public static boolean isEnableLog() {
        return enableLog;
    }

    public static boolean isFieldHasAnnotation(CtField field, Class<?> annClass) {
        try {
            Object annotation = field.getAnnotation(annClass);
            if (annotation != null) return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }

    public static String getDefaultReturnText(CtMethod method) throws Exception {
        CtClass returnType = method.getReturnType();
        if (returnType != CtClass.voidType) {
            if (isNumberType(returnType)) {
                return "return 0;";
            } else if (returnType == CtClass.booleanType) {
                return "return false;";
            } else {
                return "return null;";
            }
        }
        return null;
    }

    private static boolean isNumberType(CtClass type) {
        return type == CtClass.intType
                || type == CtClass.floatType
                || type == CtClass.longType
                || type == CtClass.byteType
                || type == CtClass.charType
                || type == CtClass.doubleType
                || type == CtClass.shortType;
    }
}

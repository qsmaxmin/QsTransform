package com.qsmaxmin.plugin;

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
    public static final String    BASE_PROJECT_NAME    = "QsBase";
    public static final String    BASE_ANNOTATION_NAME = "QsAnnotation";
    private static      ClassPool classPool;
    private static      boolean   enableLog;

    public static synchronized ClassPool getClassPool() {
        if (classPool == null) {
            classPool = new ClassPool(null);
            classPool.appendSystemPath();
        }
        return classPool;
    }

    public static synchronized void release() {
        classPool.clearImportedPackages();
        classPool = null;
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

    public static boolean hasMethod(CtClass ctClass, String methodName) {
        CtMethod[] methods = ctClass.getMethods();
        if (methods == null || methods.length == 0) return false;
        for (CtMethod m : methods) {
            if (m.getName().equals(methodName)) return true;
        }
        return false;
    }

    public static boolean hasMethod(CtClass ctClass, CtMethod method) {
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


    public static void addFiled(CtClass clazz, CtField field) throws Exception {
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

    public static CtClass getClass(String className) {
        CtClass clazz = getClassPool().getOrNull(className);
        if (clazz == null) {
            return getClassPool().makeClass(className);
        } else {
            return clazz;
        }
    }

    public static CtMethod getMethodByName(CtClass clazz, String methodName) {
        CtMethod[] methods = clazz.getMethods();
        if (methods != null && methods.length > 0) {
            for (CtMethod method : methods) {
                if (method.getName().equals(methodName)) return method;
            }
        }
        return null;
    }

    public static void println(String text) {
        if (enableLog) System.out.println(text);
    }

    public static void enableLog(boolean showLog) {
        enableLog = showLog;
    }

    public static boolean isImplementsInterface(CtClass implClass, CtClass interfaceClass) {
        try {


            CtClass[] interfaces = implClass.getInterfaces();
            if (interfaces == null || interfaces.length == 0) {
                System.out.println("========>>interface is null");
                return false;
            }

            for (CtClass c : interfaces) {
                System.out.println("========>>" + c.getName() + "=====>>" + interfaceClass.getName());
                if (c == interfaceClass) {
                    return true;
                }
            }
            return false;
        } catch (NotFoundException ignored) {
            return false;
        }
    }

    public static boolean isFieldHasAnnotation(CtField field, Class<?> annClass) {
        try {
            Object annotation = field.getAnnotation(annClass);
            if (annotation != null) return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }
}

package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.permission.Permission;
import com.qsmaxmin.plugin.helper.TransformHelper;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/8/13 16:36
 * @Description
 */
public class PermissionTransform {
    private static final String   CLASS_PERMISSION_HELPER   = "com.qsmaxmin.qsbase.plugin.permission.PermissionHelper";
    private static final String   CLASS_PERMISSION_CALLBACK = "com.qsmaxmin.qsbase.plugin.permission.PermissionCallbackListener";
    private static final String   METHOD_CHECK_PERMISSION   = "isPermissionGranted";
    private static final String   METHOD_CALLBACK           = "onPermissionCallback";
    private static final String   METHOD_REQUEST_PERMISSION = "startRequestPermission";
    private static       CtClass  callbackInterface;
    private static       CtMethod callbackMethod;

    private static void println(String text) {
        TransformHelper.println("\t\t> " + text);
    }

    public static boolean transform(CtClass clazz, CtMethod[] declaredMethods, String rootPath) throws Exception {
        if (declaredMethods == null || declaredMethods.length == 0) return false;
        if (callbackInterface == null) {
            synchronized (PermissionTransform.class) {
                if (callbackInterface == null) {
                    callbackInterface = TransformHelper.getInstance().get(CLASS_PERMISSION_CALLBACK);
                    callbackMethod = callbackInterface.getDeclaredMethod(METHOD_CALLBACK);
                }
            }
        }

        int methodIndex = 0;
        for (CtMethod originalMethod : declaredMethods) {
            Object annotation = originalMethod.getAnnotation(Permission.class);
            if (annotation != null) {
                boolean isStaticMethod = Modifier.isStatic(originalMethod.getModifiers());

                Permission permission = (Permission) annotation;
                String[] permissionArr = permission.value();
                boolean forceGoOn = permission.forceGoOn();

                addNewMethod(clazz, originalMethod, methodIndex, isStaticMethod);

                CtClass implClass = createCallbackImplClass(clazz, originalMethod, forceGoOn, methodIndex, isStaticMethod);

                String code = generateOriginalMethodCode(permissionArr, originalMethod, implClass.getName(), methodIndex, isStaticMethod);
                originalMethod.setBody(code);

                implClass.writeFile(rootPath);

                methodIndex++;
            }
        }
        if (methodIndex > 0) {
            println("transform class(@Permission) :" + clazz.getName());
        }
        return methodIndex > 0;
    }


    /**
     * copy original method body to new method
     */
    private static void addNewMethod(CtClass clazz, CtMethod originalMethod, int methodIndex, boolean isStaticMethod) throws Exception {
        String newMethodName = getNewMethodName(originalMethod, methodIndex);
        CtMethod newMethod = new CtMethod(originalMethod, clazz, null);
        newMethod.setName(newMethodName);
        if (isStaticMethod) {
            newMethod.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
        } else {
            newMethod.setModifiers(Modifier.PUBLIC);
        }
        TransformHelper.addMethod(clazz, newMethod);
    }

    private static CtClass createCallbackImplClass(CtClass clazz, CtMethod originalMethod, boolean forceGoOn, int methodIndex, boolean isStaticMethod) throws Exception {
        String implClassName = clazz.getName() + "_QsPermission" + methodIndex;
        CtClass implClass = TransformHelper.getInstance().makeClassIfNotExists(implClassName);
        if (implClass.isFrozen()) implClass.defrost();

        implClass.setInterfaces(new CtClass[]{callbackInterface});
        CtClass[] parameterTypes = originalMethod.getParameterTypes();

        if (isStaticMethod) {
            CtConstructor constructor = new CtConstructor(parameterTypes, implClass);
            if (parameterTypes != null && parameterTypes.length > 0) {
                StringBuilder sb = new StringBuilder("{");
                for (int i = 0; i < parameterTypes.length; i++) {
                    CtClass pt = parameterTypes[i];
                    CtField f = new CtField(pt, "p" + i, implClass);
                    f.setModifiers(Modifier.PRIVATE);
                    TransformHelper.addFiled(implClass, f);

                    sb.append("$0.p").append(i).append(" = $").append(i + 1).append(';');
                }
                constructor.setBody(sb.append('}').toString());
            } else {
                constructor.setBody("{}");
            }
            TransformHelper.addConstructor(implClass, constructor);

            CtMethod runMethodImpl = new CtMethod(callbackMethod, implClass, null);
            String newMethodName = getNewMethodName(originalMethod, methodIndex);
            runMethodImpl.setBody(createImplMethodBodyStatic(clazz.getName(), newMethodName, forceGoOn, parameterTypes));
            TransformHelper.addMethod(implClass, runMethodImpl);

        } else {
            CtField field = new CtField(clazz, "target", implClass);
            field.setModifiers(Modifier.PRIVATE);
            TransformHelper.addFiled(implClass, field);
            CtClass[] params;
            if (parameterTypes != null && parameterTypes.length > 0) {
                for (int i = 0; i < parameterTypes.length; i++) {
                    CtClass pt = parameterTypes[i];
                    CtField f = new CtField(pt, "p" + i, implClass);
                    f.setModifiers(Modifier.PRIVATE);
                    TransformHelper.addFiled(implClass, f);
                }
                params = new CtClass[parameterTypes.length + 1];
                params[0] = clazz;
                System.arraycopy(parameterTypes, 0, params, 1, parameterTypes.length);
            } else {
                params = new CtClass[]{clazz};
            }

            CtConstructor constructor = new CtConstructor(params, implClass);
            StringBuilder sb = new StringBuilder("{$0.target = $1;");
            for (int i = 1; i < params.length; i++) {
                sb.append("$0.p").append(i - 1).append(" = $").append(i + 1).append(';');
            }
            constructor.setBody(sb.append('}').toString());
            TransformHelper.addConstructor(implClass, constructor);


            CtMethod callbackImplMethod = new CtMethod(callbackMethod, implClass, null);
            String newMethodName = getNewMethodName(originalMethod, methodIndex);
            callbackImplMethod.setBody(createImplMethodBody(newMethodName, forceGoOn, parameterTypes));

            TransformHelper.addMethod(implClass, callbackImplMethod);
        }
        return implClass;
    }

    private static String createImplMethodBodyStatic(String className, String newMethodName, boolean forceGoOn, CtClass[] parameterTypes) {
        String executeCode;
        if (parameterTypes != null && parameterTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append("$0.p").append(i);
                if (i != parameterTypes.length - 1) sb.append(',');
            }
            executeCode = className + "." + newMethodName + "(" + sb.toString() + ");";
        } else {
            executeCode = className + "." + newMethodName + "();";
        }
        if (forceGoOn) {
            return executeCode;
        } else {
            return "if($1)" + executeCode;
        }
    }

    private static String createImplMethodBody(String newMethodName, boolean forceGoOn, CtClass[] parameterTypes) {
        String executeCode;
        if (parameterTypes != null && parameterTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append("$0.p").append(i);
                if (i != parameterTypes.length - 1) sb.append(',');
            }
            executeCode = "target." + newMethodName + "(" + sb.toString() + ");";
        } else {
            executeCode = "target." + newMethodName + "();";
        }

        if (forceGoOn) {
            return executeCode;
        } else {
            return "if($1)" + executeCode;
        }
    }

    private static String generateOriginalMethodCode(String[] permissionArr, CtMethod originalMethod, String callbackImpl, int methodIndex, boolean isStaticMethod) throws Exception {
        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < permissionArr.length; i++) {
            temp.append('\"').append(permissionArr[i]).append('\"');
            if (i != permissionArr.length - 1) {
                temp.append(',');
            }
        }
        String permissionText = temp.toString();//"aa","bb","cc"

        String args = "";
        CtClass[] parameterTypes = originalMethod.getParameterTypes();
        if (parameterTypes != null && parameterTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append("$").append(i + 1);
                if (i != parameterTypes.length - 1) sb.append(',');
            }
            args = sb.toString();
        }
        String newMethodName = getNewMethodName(originalMethod, methodIndex);


        String implCode;
        if (isStaticMethod) {
            implCode = "new " + callbackImpl + "(" + args + ")";
        } else {
            if (args.length() == 0) {
                implCode = "new " + callbackImpl + "($0)";
            } else {
                implCode = "new " + callbackImpl + "($0," + args + ")";
            }
        }

        return "{" + "String[] permissions = new String[]{" + permissionText + "};" +
                "boolean granted = " + CLASS_PERMISSION_HELPER + "." + METHOD_CHECK_PERMISSION + "(permissions);" +
                "if(granted){" + newMethodName + "($$);}" +
                "else{" + CLASS_PERMISSION_HELPER + ".getInstance()." + METHOD_REQUEST_PERMISSION + "(" + implCode + ", permissions);}}";
    }

    private static String getNewMethodName(CtMethod originalMethod, int methodIndex) {
        return originalMethod.getName() + "_QsPermission_" + methodIndex;
    }
}

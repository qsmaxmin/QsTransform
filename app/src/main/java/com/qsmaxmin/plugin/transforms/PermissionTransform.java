package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.permission.Permission;
import com.qsmaxmin.plugin.TransformHelper;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;

/**
 * @CreateBy administrator
 * @Date 2020/8/13 16:36
 * @Description
 */
public class PermissionTransform {
    private static final String   CLASS_PERMISSION_HELPER   = "com.qsmaxmin.qsbase.plugin.permission.PermissionHelper";
    private static final String   CLASS_PERMISSION_CALLBACK = "com.qsmaxmin.qsbase.plugin.permission.PermissionCallbackListener";
    private static final String   METHOD_CHECK_PERMISSION   = "isPermissionGranted";
    private static final String   METHOD_CALLBACK           = "onPermissionCallback";
    private static final String   METHOD_REQUEST_PERMISSION = "requestPermission";
    private static       CtClass  callbackInterface;
    private static       CtMethod callbackMethod;

    private static void println(String text) {
        TransformHelper.println("\t\t> " + text);
    }

    public static boolean transform(CtClass clazz, CtMethod[] declaredMethods, String rootPath, String filePath) throws Exception {
        if (declaredMethods == null || declaredMethods.length == 0) return false;
        if (callbackInterface == null) {
            synchronized (PermissionTransform.class) {
                if (callbackInterface == null) {
                    callbackInterface = TransformHelper.getClassPool().get(CLASS_PERMISSION_CALLBACK);
                    callbackMethod = callbackInterface.getDeclaredMethod(METHOD_CALLBACK);
                }
            }
        }

        int methodIndex = 0;
        for (CtMethod originalMethod : declaredMethods) {
            Object annotation = originalMethod.getAnnotation(Permission.class);
            if (annotation != null) {
                if (!TransformHelper.hasMethod(clazz, METHOD_REQUEST_PERMISSION)) {
                    throw new CannotCompileException("class(" + clazz.getSimpleName() + ") with @Permission should implement 'QsIPermission' and override method !!!");
                }

                Permission permission = (Permission) annotation;
                String[] permissionArr = permission.value();
                boolean forceGoOn = permission.forceGoOn();

                addNewMethod(clazz, originalMethod, methodIndex);

                CtClass implClass = createCallbackImplClass(clazz, originalMethod, forceGoOn, methodIndex);

                String code = generateOriginalMethodCode(permissionArr, originalMethod, implClass.getName(), methodIndex);
                originalMethod.setBody(code);

                implClass.writeFile(rootPath);

                methodIndex++;
            }
        }
        if (methodIndex > 0) {
            println("transform class(@Permission) :" + filePath);
        }
        return methodIndex > 0;
    }


    /**
     * copy original method body to new method
     */
    private static void addNewMethod(CtClass clazz, CtMethod originalMethod, int methodIndex) throws Exception {
        String newMethodName = getNewMethodName(originalMethod, methodIndex);

        CtMethod newMethod = new CtMethod(originalMethod, clazz, null);
        newMethod.setName(newMethodName);
        newMethod.setModifiers(Modifier.PUBLIC);

        TransformHelper.addMethod(clazz, newMethod);
    }

    private static CtClass createCallbackImplClass(CtClass clazz, CtMethod originalMethod, boolean forceGoOn, int methodIndex) throws Exception {
        String implClassName = clazz.getName() + "_QsPermission" + methodIndex;
        CtClass implClass = TransformHelper.getClass(implClassName);
        if (implClass.isFrozen()) implClass.defrost();

        implClass.setInterfaces(new CtClass[]{callbackInterface});

        CtClass[] parameterTypes = originalMethod.getParameterTypes();

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
        callbackImplMethod.setBody(createImplBody(newMethodName, forceGoOn, parameterTypes));

        TransformHelper.addMethod(implClass, callbackImplMethod);

        return implClass;
    }

    private static String createImplBody(String newMethodName, boolean forceGoOn, CtClass[] parameterTypes) {
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

    private static String generateOriginalMethodCode(String[] permissionArr, CtMethod originalMethod, String callbackImpl, int methodIndex) throws Exception {
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
                sb.append(",$").append(i + 1);
            }
            args = sb.toString();
        }
        String newMethodName = getNewMethodName(originalMethod, methodIndex);
        return "{boolean granted = " + CLASS_PERMISSION_HELPER + "." + METHOD_CHECK_PERMISSION + "(new String[]{" + permissionText + "});" +
                "if(granted){" + newMethodName + "($$);}" +
                "else{" + METHOD_REQUEST_PERMISSION + "(new " + callbackImpl + "($0" + args + "), new String[]{" + permissionText + "});}}";
    }

    private static String getNewMethodName(CtMethod originalMethod, int methodIndex) {
        return originalMethod.getName() + "_QsPermission_" + methodIndex;
    }
}

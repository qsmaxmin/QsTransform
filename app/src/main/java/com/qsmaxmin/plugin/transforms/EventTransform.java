package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.event.Subscribe;
import com.qsmaxmin.plugin.helper.TransformHelper;

import java.util.ArrayList;
import java.util.List;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/8/18 12:25
 * @Description
 */
public class EventTransform {
    private static final String CLASS_EVENT_HELPER = "com.qsmaxmin.qsbase.plugin.event.EventHelper";
    private static final String METHOD_BIND        = "bindEventByQsPlugin";
    private static final String METHOD_UNBIND      = "unbindEventByQsPlugin";
    private static final String METHOD_REGISTER    = "register";
    private static final String METHOD_UNREGISTER  = "unregister";

    private static void println(String text) {
        TransformHelper.println("\t\t> " + text);
    }

    public static boolean transform(CtClass clazz, CtMethod[] declaredMethods) throws Exception {
        List<CtMethod> list = null;
        for (CtMethod method : declaredMethods) {
            Object ann = method.getAnnotation(Subscribe.class);
            if (ann != null) {
                if (list == null) list = new ArrayList<>();
                list.add(method);
            }
        }

        if (list != null && list.size() > 0) {
            println("transform class(@Subscribe) :" + clazz.getName());
            checkCanTransform(clazz);

            String bindCode = getBindMethodBodyCode(clazz, list);

            CtMethod bindMethod = TransformHelper.getDeclaredMethod(clazz, METHOD_BIND);
            if (bindMethod != null) {
                bindMethod.insertAfter(bindCode);
            } else {
                bindMethod = new CtMethod(CtClass.voidType, METHOD_BIND, null, clazz);
                bindMethod.setBody(bindCode);
                TransformHelper.addMethod(clazz, bindMethod);
            }

            String unbindCode = getUnbindMethodBodyCode(clazz, list);
            CtMethod unbindMethod = TransformHelper.getDeclaredMethod(clazz, METHOD_UNBIND);
            if (unbindMethod != null) {
                unbindMethod.insertAfter(unbindCode);
            } else {
                unbindMethod = new CtMethod(CtClass.voidType, METHOD_UNBIND, null, clazz);
                unbindMethod.setBody(unbindCode);
                TransformHelper.addMethod(clazz, unbindMethod);
            }
            return true;
        }
        return false;
    }

    private static void checkCanTransform(CtClass clazz) throws CannotCompileException {
        CtMethod bindMethod = TransformHelper.getMethod(clazz, METHOD_BIND);
        if (bindMethod == null) {
            throw new CannotCompileException("Classes(" + clazz.getSimpleName() + ") with @Subscribe annotations must implements QsIBindEvent, and execute its method when class created and destroyed..");
        }
    }

    private static String getUnbindMethodBodyCode(CtClass clazz, List<CtMethod> list) throws Exception {
        StringBuilder mainSB = new StringBuilder("{" + CLASS_EVENT_HELPER + "." + METHOD_UNREGISTER + "($0,");

        StringBuilder sb = new StringBuilder("new Class[]{");
        for (int i = 0, size = list.size(); i < size; i++) {
            CtMethod method = list.get(i);
            CtClass[] types = method.getParameterTypes();

            CtClass paramCtClass = types[0];
            String paramClassName = paramCtClass.getName();
            sb.append(paramClassName).append(".class");
            if (i != size - 1) {
                sb.append(',');
            } else {
                sb.append('}');
            }
        }
        return mainSB.append(sb.toString()).append(");}").toString();
    }

    private static String getBindMethodBodyCode(CtClass clazz, List<CtMethod> list) throws Exception {
        StringBuilder mainSB = new StringBuilder("{" + CLASS_EVENT_HELPER + "." + METHOD_REGISTER + "($0,");

        StringBuilder sb0 = new StringBuilder("new String[]{");
        StringBuilder sb1 = new StringBuilder("new Class[]{");

        for (int i = 0, size = list.size(); i < size; i++) {
            CtMethod method = list.get(i);
            String methodName = method.getName();
            CtClass[] types = method.getParameterTypes();
            if (types == null || types.length != 1) {
                throw new CannotCompileException("class:" + clazz.getName() + ", method:" + methodName + " with @Subscribe can only be one parameter");
            }
            CtClass paramCtClass = types[0];
            String paramClassName = paramCtClass.getName();

            sb0.append("\"").append(methodName).append("\"");
            sb1.append(paramClassName).append(".class");
            if (i != size - 1) {
                sb0.append(',');
                sb1.append(',');
            } else {
                sb0.append('}');
                sb1.append('}');
            }
        }
        return mainSB.append(sb0.toString()).append(",").append(sb1.toString()).append(");}").toString();
    }
}

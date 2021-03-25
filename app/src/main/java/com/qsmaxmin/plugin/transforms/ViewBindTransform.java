package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.bind.Bind;
import com.qsmaxmin.annotation.bind.BindBundle;
import com.qsmaxmin.annotation.bind.OnClick;
import com.qsmaxmin.plugin.helper.TransformHelper;

import java.util.HashMap;
import java.util.HashSet;

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
public class ViewBindTransform {
    private static final String   INTERFACE_BIND_VIEW   = "com.qsmaxmin.qsbase.plugin.bind.QsIBindView";
    private static final String   INTERFACE_BIND_BUNDLE = "com.qsmaxmin.qsbase.plugin.bind.QsIBindBundle";
    private static final String   PATH_BIND_HELPER      = "com.qsmaxmin.qsbase.plugin.bind.ViewBindHelper";
    private static final String   METHOD_BIND_VIEW      = "bindViewByQsPlugin";
    private static final String   METHOD_BIND_BUNDLE    = "bindBundleByQsPlugin";
    private static final String   PATH_VIEW             = "android.view.View";
    private static final String   PATH_BUNDLE           = "android.os.Bundle";
    private static       CtClass  helperClass;
    private static       CtClass  listenerClass;
    private static       CtMethod onClickMethod;

    public static int transform(CtClass clazz, CtMethod[] declaredMethods, CtField[] declaredFields, String rootPath) throws Exception {
        if (listenerClass == null) {
            synchronized (ThreadPointTransform.class) {
                if (listenerClass == null) {
                    listenerClass = TransformHelper.getInstance().get("android.view.View$OnClickListener");
                    onClickMethod = listenerClass.getDeclaredMethod("onClick");
                    helperClass = TransformHelper.getInstance().get(PATH_BIND_HELPER);
                }
            }
        }

        HashMap<CtField, Bind> bindMap = null;
        HashMap<CtField, BindBundle> bundleMap = null;
        HashMap<CtMethod, OnClick> onClickMap = null;

        if (declaredFields != null && declaredFields.length > 0) {
            for (CtField field : declaredFields) {
                Object obj = field.getAnnotation(Bind.class);
                if (obj != null) {
                    if (bindMap == null) bindMap = new HashMap<>();
                    bindMap.put(field, (Bind) obj);
                } else {
                    Object o = field.getAnnotation(BindBundle.class);
                    if (o != null) {
                        if (bundleMap == null) bundleMap = new HashMap<>();
                        bundleMap.put(field, (BindBundle) o);
                    }
                }
            }
        }

        if (declaredMethods != null && declaredMethods.length > 0) {
            for (CtMethod method : declaredMethods) {
                Object obj = method.getAnnotation(OnClick.class);
                if (obj != null) {
                    if (onClickMap == null) onClickMap = new HashMap<>();
                    onClickMap.put(method, (OnClick) obj);
                }
            }
        }

        if (bindMap != null || onClickMap != null || bundleMap != null) {
            if (clazz.isFrozen()) clazz.defrost();
            if (bindMap != null || onClickMap != null) {
                addBindViewMethod(clazz, bindMap, onClickMap, rootPath);
            }
            if (bundleMap != null) {
                addBindBundleMethod(clazz, bundleMap);
            }

            int state = 0;
            if (bindMap != null) state |= MainTransform.STATE_BIND_VIEW;
            if (onClickMap != null) state |= MainTransform.STATE_ONCLICK;
            if (bundleMap != null) state |= MainTransform.STATE_BIND_BUNDLE;
            return state;
        }
        return 0;
    }

    private static void addBindBundleMethod(CtClass clazz, HashMap<CtField, BindBundle> bundleMap) throws Exception {
        CtMethod ctMethod = TransformHelper.getDeclaredMethod(clazz, METHOD_BIND_BUNDLE);
        boolean hasInterface = TransformHelper.hasInterface(clazz, INTERFACE_BIND_BUNDLE);

        StringBuilder sb = new StringBuilder("{");
        if (ctMethod == null && !hasInterface) {
            sb.append("super." + METHOD_BIND_BUNDLE + "($1);");
        }
        sb.append("if($1==null)return;");

        for (CtField field : bundleMap.keySet()) {
            BindBundle bindBundle = bundleMap.get(field);
            String bk = bindBundle.value();
            String typeName = field.getType().getName();
            String bundleMethodName = getBundleMethodName(typeName);

            if (bundleMethodName == null) {
                sb.append("$0.").append(field.getName()).append("=(").append(typeName).append(")").append(PATH_BIND_HELPER).append(".get($1,\"").append(bk).append("\");");
//                sb.append("$0.").append(field.getName()).append("=(").append(typeName).append(")$1.get(\"").append(bk).append("\");");
            } else {
                sb.append("$0.").append(field.getName()).append("=").append(PATH_BIND_HELPER).append(".").append(bundleMethodName).append("($1,\"").append(bk).append("\");");
//                sb.append("$0.").append(field.getName()).append("=$1.").append(bundleMethodName).append("(\"").append(bk).append("\");");
            }
        }
        String code = sb.append("}").toString();

        if (ctMethod != null) {
            ctMethod.insertAfter(code);
        } else {
            CtClass[] bundleClass = new CtClass[]{TransformHelper.getInstance().get(PATH_BUNDLE)};
            ctMethod = new CtMethod(CtClass.voidType, METHOD_BIND_BUNDLE, bundleClass, clazz);
            ctMethod.setModifiers(Modifier.PUBLIC);
            ctMethod.setBody(code);
            clazz.addMethod(ctMethod);
        }
    }

    private static String getBundleMethodName(String typeName) {
        switch (typeName) {
            case "int":
                return "getInt";
            case "float":
                return "getFloat";
            case "byte":
                return "getByte";
            case "char":
                return "getChar";
            case "long":
                return "getLong";
            case "double":
                return "getDouble";
            case "boolean":
                return "getBoolean";
            case "short":
                return "getShort";
            case "java.lang.String":
                return "getString";
            default:
                return null;
        }
    }

    private static void addBindViewMethod(CtClass clazz, HashMap<CtField, Bind> bindMap, HashMap<CtMethod, OnClick> onClickMap, String rootPath) throws Exception {
        CtMethod ctMethod = TransformHelper.getDeclaredMethod(clazz, METHOD_BIND_VIEW);
        boolean hasInterface = TransformHelper.hasInterface(clazz, INTERFACE_BIND_VIEW);
        StringBuilder sb = new StringBuilder("{");
        if (ctMethod == null && !hasInterface) {
            sb.append("super." + METHOD_BIND_VIEW + "($1);");
        }
        sb.append("if($1==null)return;");

        HashSet<Integer> bindIds = null;
        if (bindMap != null) {
            bindIds = new HashSet<>();
            int id;
            for (CtField field : bindMap.keySet()) {
                Bind bind = bindMap.get(field);
                id = bind.value();
                bindIds.add(id);
                String typeName = field.getType().getName();
                sb.append("android.view.View v_").append(id).append("=$1.findViewById(").append(id).append(");");
                sb.append("if(v_").append(id).append("!=null)$0.").append(field.getName()).append("=(").append(typeName).append(")v_").append(id).append(";");
            }
        }
        if (onClickMap != null) {
            int index = 0;
            for (CtMethod method : onClickMap.keySet()) {
                OnClick onClick = onClickMap.get(method);
                long interval = onClick.clickInterval();
                int[] ids = onClick.value();

                CtClass implClass = createClickListenerImplClass(clazz, index, method, interval);
                String implClassName = implClass.getName();

                sb.append(implClassName).append(" l").append(index).append("=new ").append(implClassName).append("($0);");
                for (int id : ids) {
                    if (bindIds == null || !bindIds.contains(id)) {
                        sb.append("android.view.View v_").append(id).append("=$1.findViewById(").append(id).append(");");
                    }
                    sb.append("if(v_").append(id).append("!=null)v_").append(id).append(".setOnClickListener(l").append(index).append(");");
                }
                implClass.writeFile(rootPath);
                index++;
            }
        }
        String code = sb.append('}').toString();

        if (ctMethod != null) {
            ctMethod.insertAfter(code);
        } else {
            CtClass[] viewClasses = new CtClass[]{TransformHelper.getInstance().get(PATH_VIEW)};
            ctMethod = new CtMethod(CtClass.voidType, METHOD_BIND_VIEW, viewClasses, clazz);
            ctMethod.setModifiers(Modifier.PUBLIC);
            ctMethod.setBody(code);
            clazz.addMethod(ctMethod);
        }
    }

    private static CtClass createClickListenerImplClass(CtClass clazz, int index, CtMethod method, long interval) throws Exception {
        String implClassName = clazz.getName() + "_QsListener" + index;
        CtClass implClass = TransformHelper.getInstance().makeClassIfNotExists(implClassName);
        if (implClass.isFrozen()) implClass.defrost();

        implClass.setInterfaces(new CtClass[]{listenerClass});

        CtField targetFile = new CtField(clazz, "target", implClass);
        targetFile.setModifiers(Modifier.PRIVATE);
        TransformHelper.addField(implClass, targetFile);
        if (interval > 0) {
            CtField longFile = new CtField(CtClass.longType, "lastTime", implClass);
            longFile.setModifiers(Modifier.PRIVATE);
            TransformHelper.addField(implClass, longFile);
        }

        CtClass[] params = new CtClass[]{clazz};
        CtConstructor constructor = new CtConstructor(params, implClass);
        constructor.setBody("{$0.target=$1;}");
        TransformHelper.addConstructor(implClass, constructor);

        CtMethod onClickMethodImpl = new CtMethod(onClickMethod, implClass, null);
        if (interval > 0) {
            onClickMethodImpl.setBody("{long time = System.currentTimeMillis();if (time - $0.lastTime < " + interval + ") return;" +
                    "$0.lastTime = time;" + "$0.target. " + method.getName() + " ($1);}");
        } else {
            onClickMethodImpl.setBody("{$0.target." + method.getName() + "($1);}");
        }
        TransformHelper.addMethod(implClass, onClickMethodImpl);
        return implClass;
    }
}

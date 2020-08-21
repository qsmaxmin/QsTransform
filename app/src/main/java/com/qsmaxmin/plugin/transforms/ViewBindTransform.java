package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.bind.Bind;
import com.qsmaxmin.annotation.bind.BindBundle;
import com.qsmaxmin.annotation.bind.OnClick;
import com.qsmaxmin.plugin.TransformHelper;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;

/**
 * @CreateBy administrator
 * @Date 2020/8/13 16:36
 * @Description
 */
public class ViewBindTransform {
    private static final String METHOD_BIND_VIEW   = "bindViewByQsPlugin";
    private static final String METHOD_BIND_BUNDLE = "bindBundleByQsPlugin";
    private static final String PATH_VIEW          = "android.view.View";
    private static final String PATH_BUNDLE        = "android.os.Bundle";
    private static final int    STATE_BIND_VIEW    = 0b001;
    private static final int    STATE_BIND_BUNDLE  = 0b010;

    private static void println(String text) {
        TransformHelper.println("\t\t> " + text);
    }

    public static boolean transform(CtClass clazz, CtMethod[] declaredMethods, CtField[] declaredFields, String filePath) throws Exception {
        int state = 0;
        if (declaredFields != null && declaredFields.length > 0) {
            for (CtField field : declaredFields) {
                if (!matched(state, STATE_BIND_VIEW) && TransformHelper.isFieldHasAnnotation(field, Bind.class)) {
                    state |= STATE_BIND_VIEW;
                } else if (!matched(state, STATE_BIND_BUNDLE) && TransformHelper.isFieldHasAnnotation(field, BindBundle.class)) {
                    state |= STATE_BIND_BUNDLE;
                }
            }
        }

        if (!matched(state, STATE_BIND_VIEW)) {
            if (declaredMethods != null && declaredMethods.length > 0) {
                for (CtMethod method : declaredMethods) {
                    if (method.getAnnotation(OnClick.class) != null) {
                        state |= STATE_BIND_VIEW;
                        break;
                    }
                }
            }
        }

        if (state != 0) {
            println("transform class(@Bind, @OnClick...) :" + filePath);
            if (clazz.isFrozen()) clazz.defrost();
            if (matched(state, STATE_BIND_VIEW)) {
                addBindViewMethod(clazz);
            }
            if (matched(state, STATE_BIND_BUNDLE)) {
                addBindBundleMethod(clazz);
            }
            return true;
        }
        return false;
    }

    private static boolean matched(int state, int tag) {
        return (state & tag) == tag;
    }

    private static void addBindViewMethod(CtClass clazz) throws Exception {
        CtClass[] viewClasses = new CtClass[]{TransformHelper.getInstance().makeClass(PATH_VIEW)};
        CtMethod ctMethod = new CtMethod(CtClass.voidType, METHOD_BIND_VIEW, viewClasses, clazz);
        ctMethod.setModifiers(Modifier.PUBLIC);
        ctMethod.setBody("{" + clazz.getName() + "_QsBind.bindView($0, $1);}");
        clazz.addMethod(ctMethod);
    }

    private static void addBindBundleMethod(CtClass clazz) throws Exception {
        CtClass[] bundleClass = new CtClass[]{TransformHelper.getInstance().makeClass(PATH_BUNDLE)};
        CtMethod ctMethod = new CtMethod(CtClass.voidType, METHOD_BIND_BUNDLE, bundleClass, clazz);
        ctMethod.setModifiers(Modifier.PUBLIC);
        ctMethod.setBody("{" + clazz.getName() + "_QsBind.bindBundle($0, $1);}");
        clazz.addMethod(ctMethod);
    }
}

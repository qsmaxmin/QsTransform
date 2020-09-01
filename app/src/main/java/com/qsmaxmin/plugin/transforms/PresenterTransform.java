package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.presenter.Presenter;
import com.qsmaxmin.plugin.helper.TransformHelper;

import java.util.List;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.ClassFile;
import javassist.bytecode.annotation.Annotation;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/8/28 16:09
 * @Description
 */
public class PresenterTransform {

    private static void println(String text) {
        TransformHelper.println("\t\t> " + text);
    }

    public static boolean transform(CtClass clazz) throws Exception {
        Object presenter = clazz.getAnnotation(Presenter.class);
        if (presenter == null) {
            return false;
        }
        println("transform class(@Presenter) :" + clazz.getName());
        if (clazz.isFrozen()) clazz.defrost();

        String presenterClassName = getPresenterClassName(clazz);
        if (presenterClassName == null) return false;

        CtMethod method = CtMethod.make("public java.lang.Object createPresenter() {" +
                "return new " + presenterClassName + "();" +
                "}", clazz);

        clazz.addMethod(method);
        return true;
    }

    private static String getPresenterClassName(CtClass clazz) {
        ClassFile classFile = clazz.getClassFile();
        List<AttributeInfo> attributes = classFile.getAttributes();
        for (AttributeInfo info : attributes) {
            if (info instanceof AnnotationsAttribute) {
                Annotation[] annotations = ((AnnotationsAttribute) info).getAnnotations();
                for (Annotation ann : annotations) {
                    String typeName = ann.getTypeName();
                    if (typeName.equals(Presenter.class.getName())) {
                        String className = ann.getMemberValue("value").toString();
                        int index = className.lastIndexOf('.');
                        return className.substring(0, index);
                    }
                }
            }
        }
        return null;
    }

    private static String getBodyCode(String presenterClassName) {
        String c0 = presenterClassName + " p = new " + presenterClassName + "();";

        return "{ " + c0
                + "return c0;}";
    }
}

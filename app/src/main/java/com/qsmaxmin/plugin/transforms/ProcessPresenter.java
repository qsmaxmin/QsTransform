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
class ProcessPresenter {

    void transform(CtClass clazz) throws Exception {
        if (clazz.isFrozen()) clazz.defrost();
        String presenterClassName = getPresenterClassName(clazz);
        if (presenterClassName == null) return;

        if (!TransformHelper.hasDeclaredMethod(clazz, "createPresenter")) {
            CtMethod method = CtMethod.make("public java.lang.Object createPresenter() {" +
                    "return new " + presenterClassName + "();" +
                    "}", clazz);
            clazz.addMethod(method);
        } else {
            throw new Exception("class:" + clazz.getName() + " with @Presenter annotation, but not has method 'createPresenter()' !!");
        }
    }

    private String getPresenterClassName(CtClass clazz) {
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
}

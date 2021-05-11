package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.event.Subscribe;
import com.qsmaxmin.plugin.helper.TransformHelper;
import com.qsmaxmin.plugin.model.DataHolder;

import java.util.List;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/8/18 12:25
 * @Description
 */
class ProcessEvent {
    private static final String   CLASS_EVENT_HELPER  = "com.qsmaxmin.qsbase.plugin.event.EventHelper";
    private static final String   CLASS_EVENT_HANDLER = "com.qsmaxmin.qsbase.plugin.event.EventHandler";
    private static final String   METHOD_BIND         = "bindEventByQsPlugin";
    private static final String   METHOD_UNBIND       = "unbindEventByQsPlugin";
    private static final String   METHOD_REGISTER     = "register";
    private static final String   METHOD_UNREGISTER   = "unregister";
    private final        CtClass  handlerInterfaceClass;
    private final        CtClass  javaClass;
    private final        CtMethod executeMethod;
    private final        CtMethod getParamsClassMethod;

    ProcessEvent() throws Exception {
        handlerInterfaceClass = TransformHelper.getInstance().get(CLASS_EVENT_HANDLER);
        executeMethod = handlerInterfaceClass.getDeclaredMethod("execute");
        getParamsClassMethod = handlerInterfaceClass.getDeclaredMethod("getParamsClass");
        javaClass = TransformHelper.getInstance().get("java.lang.Class");
    }

    void transform(CtClass clazz, List<DataHolder<CtMethod, Subscribe>> list, String rootPath) throws Exception {
        checkCanTransform(clazz);
        String bindCode = getBindMethodBodyCode(clazz, list, rootPath);

        CtMethod bindMethod = TransformHelper.getDeclaredMethod(clazz, METHOD_BIND);
        if (bindMethod != null) {
            bindMethod.insertAfter(bindCode);
        } else {
            bindMethod = new CtMethod(CtClass.voidType, METHOD_BIND, null, clazz);
            bindMethod.setBody(bindCode);
            TransformHelper.addMethod(clazz, bindMethod);
        }

        String unbindCode = "{" + CLASS_EVENT_HELPER + "." + METHOD_UNREGISTER + "($0);}";
        CtMethod unbindMethod = TransformHelper.getDeclaredMethod(clazz, METHOD_UNBIND);
        if (unbindMethod != null) {
            unbindMethod.insertAfter(unbindCode);
        } else {
            unbindMethod = new CtMethod(CtClass.voidType, METHOD_UNBIND, null, clazz);
            unbindMethod.setBody(unbindCode);
            TransformHelper.addMethod(clazz, unbindMethod);
        }
    }

    private void checkCanTransform(CtClass clazz) throws CannotCompileException {
        CtMethod bindMethod = TransformHelper.getMethod(clazz, METHOD_BIND);
        if (bindMethod == null) {
            throw new CannotCompileException("Classes(" + clazz.getSimpleName() + ") with @Subscribe annotations must implements QsIBindEvent, " +
                    "and execute its method when class created and destroyed..");
        }
    }

    private String getBindMethodBodyCode(CtClass clazz, List<DataHolder<CtMethod, Subscribe>> list, String rootPath) throws Exception {
        StringBuilder mainSB = new StringBuilder("{" + CLASS_EVENT_HELPER + "." + METHOD_REGISTER + "($0,");

        StringBuilder sb = new StringBuilder("new " + CLASS_EVENT_HANDLER + "[]{");

        for (int i = 0, size = list.size(); i < size; i++) {
            DataHolder<CtMethod, Subscribe> data = list.get(i);
            CtMethod method = data.key;
            String methodName = method.getName();
            CtClass[] types = method.getParameterTypes();
            if (types == null || types.length != 1) {
                throw new CannotCompileException("class:" + clazz.getName() + ", method:" + methodName + " with @Subscribe can only be one parameter");
            }
            CtClass paramType = types[0];

            CtClass handlerClass = createEventHandlerClass(clazz, methodName, paramType, i);
            handlerClass.writeFile(rootPath);

            sb.append("new ").append(handlerClass.getName()).append("(this, ").append(paramType.getName()).append(".class)");
            if (i != size - 1) {
                sb.append(',');
            } else {
                sb.append('}');
            }
        }
        return mainSB.append(sb.toString()).append(");}").toString();
    }


    private CtClass createEventHandlerClass(CtClass clazz, String methodName, CtClass parameterClass, int methodIndex) throws Exception {
        String implClassName = clazz.getName() + "_QsHandler" + methodIndex;
        CtClass implClass = TransformHelper.getInstance().makeClassIfNotExists(implClassName);
        if (implClass.isFrozen()) implClass.defrost();
        implClass.setSuperclass(handlerInterfaceClass);

        CtField targetField = new CtField(clazz, "target", implClass);
        targetField.setModifiers(Modifier.PRIVATE);
        TransformHelper.addField(implClass, targetField);

        CtField paramField = new CtField(javaClass, "clazz", implClass);
        paramField.setModifiers(Modifier.PRIVATE);
        TransformHelper.addField(implClass, paramField);

        CtClass[] params = new CtClass[]{clazz, javaClass};
        CtConstructor constructor = new CtConstructor(params, implClass);
        constructor.setBody("{$0.target = $1;$0.clazz=$2;}");
        TransformHelper.addConstructor(implClass, constructor);

        String typeName = parameterClass.getName();
        CtMethod executeMethodImpl = new CtMethod(executeMethod, implClass, null);
        executeMethodImpl.setBody("{target." + methodName + "((" + typeName + ")$1);}");
        TransformHelper.addMethod(implClass, executeMethodImpl);

        CtMethod getParamsClassMethodImpl = new CtMethod(getParamsClassMethod, implClass, null);
        getParamsClassMethodImpl.setBody("{return clazz;}");
        TransformHelper.addMethod(implClass, getParamsClassMethodImpl);

        return implClass;
    }
}

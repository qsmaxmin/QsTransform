package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.thread.ThreadPoint;
import com.qsmaxmin.annotation.thread.ThreadType;
import com.qsmaxmin.plugin.helper.TransformHelper;
import com.qsmaxmin.plugin.model.DataHolder;

import java.util.List;

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
class ProcessThreadPoint {
    private static final String   CLASS_THREAD_HELPER    = "com.qsmaxmin.qsbase.plugin.threadpoll.QsThreadPollHelper";
    private static final String   CLASS_RUNNABLE         = "com.qsmaxmin.qsbase.plugin.threadpoll.SafeRunnable";
    private static final String   methodNameMainThread   = "post";
    private static final String   methodNameWorkThread   = "runOnWorkThread";
    private static final String   methodNameHttpThread   = "runOnHttpThread";
    private static final String   methodNameSingleThread = "runOnSingleThread";
    private final        CtClass  runnableClass;
    private final        CtMethod runMethod;

    ProcessThreadPoint() throws Exception {
        runnableClass = TransformHelper.getInstance().get(CLASS_RUNNABLE);
        runMethod = runnableClass.getDeclaredMethod("safeRun");
    }

    void transform(CtClass clazz, List<DataHolder<CtMethod, ThreadPoint>> threadPointData, String rootPath) throws Exception {
        int methodIndex = 0;
        for (DataHolder<CtMethod, ThreadPoint> data : threadPointData) {
            CtMethod originalMethod = data.key;
            ThreadPoint threadPoint = data.value;
            ThreadType type = threadPoint.value();
            boolean isStaticMethod = Modifier.isStatic(originalMethod.getModifiers());

            addNewMethod(clazz, originalMethod, methodIndex, isStaticMethod);
            CtClass implClass = createRunnableClass(clazz, originalMethod, methodIndex, isStaticMethod);
            String code = generateOriginalMethodCode(originalMethod, implClass.getName(), type, isStaticMethod);
            originalMethod.setBody(code);

            implClass.writeFile(rootPath);
            methodIndex++;
        }
    }


    private CtClass createRunnableClass(CtClass clazz, CtMethod originalMethod, int methodIndex, boolean isStaticMethod) throws Exception {
        String implClassName = clazz.getName() + "_QsThread" + methodIndex;
        CtClass implClass = TransformHelper.getInstance().makeClassIfNotExists(implClassName);
        if (implClass.isFrozen()) implClass.defrost();

        implClass.setSuperclass(runnableClass);

        CtClass[] parameterTypes = originalMethod.getParameterTypes();

        if (isStaticMethod) {
            CtConstructor constructor = new CtConstructor(parameterTypes, implClass);
            if (parameterTypes != null && parameterTypes.length > 0) {
                StringBuilder sb = new StringBuilder("{");
                for (int i = 0; i < parameterTypes.length; i++) {
                    CtClass pt = parameterTypes[i];
                    CtField f = new CtField(pt, "p" + i, implClass);
                    f.setModifiers(Modifier.PRIVATE);
                    TransformHelper.addField(implClass, f);

                    sb.append("$0.p").append(i).append(" = $").append(i + 1).append(';');
                }
                constructor.setBody(sb.append('}').toString());
            } else {
                constructor.setBody("{}");
            }
            TransformHelper.addConstructor(implClass, constructor);

            CtMethod runMethodImpl = new CtMethod(runMethod, implClass, null);
            String newMethodName = getNewMethodName(originalMethod, methodIndex);
            runMethodImpl.setBody(createImplMethodBodyStatic(clazz.getName(), newMethodName, parameterTypes));
            TransformHelper.addMethod(implClass, runMethodImpl);

        } else {
            CtField field = new CtField(clazz, "target", implClass);
            field.setModifiers(Modifier.PRIVATE);
            TransformHelper.addField(implClass, field);
            CtClass[] params;
            if (parameterTypes != null && parameterTypes.length > 0) {
                for (int i = 0; i < parameterTypes.length; i++) {
                    CtClass pt = parameterTypes[i];
                    CtField f = new CtField(pt, "p" + i, implClass);
                    f.setModifiers(Modifier.PRIVATE);
                    TransformHelper.addField(implClass, f);
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

            CtMethod runMethodImpl = new CtMethod(runMethod, implClass, null);
            String newMethodName = getNewMethodName(originalMethod, methodIndex);
            runMethodImpl.setBody(createImplMethodBody(newMethodName, parameterTypes));
            TransformHelper.addMethod(implClass, runMethodImpl);
        }
        return implClass;
    }

    private String createImplMethodBodyStatic(String className, String newMethodName, CtClass[] parameterTypes) {
        if (parameterTypes != null && parameterTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append("$0.p").append(i);
                if (i != parameterTypes.length - 1) sb.append(',');
            }
            return "{" + className + "." + newMethodName + "(" + sb.toString() + ");}";
        } else {
            return "{" + className + "." + newMethodName + "();}";
        }
    }

    private String createImplMethodBody(String newMethodName, CtClass[] parameterTypes) {
        if (parameterTypes != null && parameterTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append("$0.p").append(i);
                if (i != parameterTypes.length - 1) sb.append(',');
            }
            return "{target." + newMethodName + "(" + sb.toString() + ");}";
        } else {
            return "{target." + newMethodName + "();}";
        }
    }


    /**
     * copy original method body to new method
     */
    private void addNewMethod(CtClass clazz, CtMethod originalMethod, int methodIndex, boolean isStatic) throws Exception {
        String newMethodName = getNewMethodName(originalMethod, methodIndex);
        CtMethod newMethod = new CtMethod(originalMethod, clazz, null);
        newMethod.setName(newMethodName);
        if (isStatic) {
            newMethod.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
        } else {
            newMethod.setModifiers(Modifier.PUBLIC);
        }
        TransformHelper.addMethod(clazz, newMethod);
    }

    private String generateOriginalMethodCode(CtMethod originalMethod, String runnableImpl, ThreadType type, boolean isStaticMethod) throws Exception {
        String args = "";
        CtClass[] parameterTypes = originalMethod.getParameterTypes();
        if (parameterTypes != null && parameterTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append('$').append(i + 1);
                if (i != parameterTypes.length - 1) sb.append(',');
            }
            args = sb.toString();
        }
        String executeName = null;
        switch (type) {
            case HTTP:
                executeName = methodNameHttpThread;
                break;
            case WORK:
                executeName = methodNameWorkThread;
                break;
            case MAIN:
                executeName = methodNameMainThread;
                break;
            case SINGLE_WORK:
                executeName = methodNameSingleThread;
                break;
        }

        StringBuilder sb = new StringBuilder("{");
        if (isStaticMethod) {
            sb.append(CLASS_THREAD_HELPER + ".").append(executeName).append("(new ").append(runnableImpl).append("(").append(args).append("));");
        } else {
            if (args.length() == 0) {
                sb.append(CLASS_THREAD_HELPER + ".").append(executeName).append("(new ").append(runnableImpl).append("($0));");
            } else {
                sb.append(CLASS_THREAD_HELPER + ".").append(executeName).append("(new ").append(runnableImpl).append("($0,").append(args).append("));");
            }
        }

        String returnText = TransformHelper.getDefaultReturnText(originalMethod);
        if (returnText != null) {
            sb.append(returnText);
            TransformHelper.println("\t\t\t> method with @ThreadPoint has invalid return value, method:" + originalMethod.getName());
        }
        return sb.append('}').toString();
    }

    private String getNewMethodName(CtMethod originalMethod, int methodIndex) {
        return originalMethod.getName() + "_QsThread_" + methodIndex;
    }
}

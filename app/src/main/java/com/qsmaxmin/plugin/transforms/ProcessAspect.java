package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.aspect.JoinPoint;
import com.qsmaxmin.annotation.aspect.QsAspect;
import com.qsmaxmin.annotation.aspect.QsIAspect;
import com.qsmaxmin.plugin.helper.TransformHelper;
import com.qsmaxmin.plugin.model.DataHolder;

import java.util.List;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.annotation.Annotation;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/10/23 15:08
 * @Description
 */
class ProcessAspect {
    private final CtClass  jointPointClass;
    private final CtMethod proceedMethod;
    private final CtMethod getTargetMethod;
    private final CtMethod getArgsMethod;
    private final CtMethod getTagMethod;
    private final CtClass  stringClass;

    ProcessAspect() throws Exception {
        jointPointClass = TransformHelper.getInstance().get(JoinPoint.class.getName());
        proceedMethod = jointPointClass.getDeclaredMethod("proceed");
        getTargetMethod = jointPointClass.getDeclaredMethod("getTarget");
        getArgsMethod = jointPointClass.getDeclaredMethod("getArgs");
        getTagMethod = jointPointClass.getDeclaredMethod("getTag");
        stringClass = TransformHelper.getInstance().get(String.class.getName());
    }

    void transform(CtClass clazz, List<DataHolder<CtMethod, QsAspect>> qsAspectData, String rootPath) throws Exception {
        if (clazz.isFrozen()) clazz.defrost();
        int methodIndex = 0;
        for (DataHolder<CtMethod, QsAspect> data : qsAspectData) {
            QsAspect aspect = data.value;
            CtMethod originalMethod = data.key;
            String aspectClassName = getAspectClassName(originalMethod);
            if (aspectClassName == null) continue;

            MethodInfo methodInfo = getReturnInfo(originalMethod);

            addNewMethod(clazz, originalMethod, methodIndex, methodInfo);
            CtClass jointPointClass = createJointPointClass(clazz, originalMethod, methodIndex, methodInfo);

            String code = generateOriginalMethodCode(aspect.tag(), aspectClassName, jointPointClass.getName(), methodInfo);
            originalMethod.setBody(code);

            jointPointClass.writeFile(rootPath);
            methodIndex++;
        }
    }

    private CtClass[] getNewConstructParams(CtClass clazz, CtClass[] parameterTypes, MethodInfo methodInfo) {
        int appendLen = methodInfo.isStaticMethod ? 1 : 2;
        CtClass[] ctClasses;
        if (parameterTypes == null || parameterTypes.length == 0) {
            ctClasses = new CtClass[appendLen];
        } else {
            ctClasses = new CtClass[parameterTypes.length + appendLen];
            System.arraycopy(parameterTypes, 0, ctClasses, appendLen, parameterTypes.length);
        }
        if (methodInfo.isStaticMethod) {
            ctClasses[0] = stringClass;
        } else {
            ctClasses[0] = clazz;
            ctClasses[1] = stringClass;
        }
        return ctClasses;
    }

    private CtClass createJointPointClass(CtClass clazz, CtMethod originalMethod, int methodIndex, MethodInfo methodInfo) throws Exception {
        String implClassName = clazz.getName() + "_QsAspect" + methodIndex;
        CtClass implClass = TransformHelper.getInstance().makeClassIfNotExists(implClassName);
        if (implClass.isFrozen()) implClass.defrost();

        CtClass[] parameterTypes = getNewConstructParams(clazz, methodInfo.parameterTypes, methodInfo);

        implClass.setInterfaces(new CtClass[]{jointPointClass});
        CtConstructor constructor = new CtConstructor(parameterTypes, implClass);
        CtMethod proceedMethodImpl = new CtMethod(proceedMethod, implClass, null);
        CtMethod getArgsMethodImpl = new CtMethod(getArgsMethod, implClass, null);
        CtMethod getTargetMethodImpl = new CtMethod(getTargetMethod, implClass, null);
        CtMethod getTagMethodImpl = new CtMethod(getTagMethod, implClass, null);

        StringBuilder constructBuilder = new StringBuilder("{");
        StringBuilder argsBuilder = new StringBuilder();
        for (int i = 0; i < parameterTypes.length; i++) {
            CtClass pt = parameterTypes[i];
            CtField f = new CtField(pt, "p" + i, implClass);
            f.setModifiers(Modifier.PRIVATE);
            TransformHelper.addField(implClass, f);
            constructBuilder.append("$0.p").append(i).append(" = $").append(i + 1).append(';');

            if ((methodInfo.isStaticMethod && i > 0) || (!methodInfo.isStaticMethod && i > 1)) {
                CtClass p = parameterTypes[i];
                switch (p.getName()) {
                    case "int":
                        argsBuilder.append(Integer.class.getName()).append(".valueOf(").append('$').append(i + 1).append(')');
                        break;
                    case "long":
                        argsBuilder.append(Long.class.getName()).append(".valueOf(").append('$').append(i + 1).append(')');
                        break;
                    case "float":
                        argsBuilder.append(Float.class.getName()).append(".valueOf(").append('$').append(i + 1).append(')');
                        break;
                    case "double":
                        argsBuilder.append(Double.class.getName()).append(".valueOf(").append('$').append(i + 1).append(')');
                        break;
                    case "boolean":
                        argsBuilder.append(Boolean.class.getName()).append(".valueOf(").append('$').append(i + 1).append(')');
                    case "char":
                        break;
                    case "short":
                        argsBuilder.append(Short.class.getName()).append(".valueOf(").append('$').append(i + 1).append(')');
                        break;
                    case "byte":
                        argsBuilder.append(Byte.class.getName()).append(".valueOf(").append('$').append(i + 1).append(')');
                        break;
                    default:
                        argsBuilder.append("$").append(i + 1);
                        break;
                }
                if (i != parameterTypes.length - 1) argsBuilder.append(',');
            }
        }
        if (argsBuilder.length() > 0) {
            String objectName = Object.class.getName();
            constructBuilder.append("$0.args=new ").append(objectName).append("[]{").append(argsBuilder.toString()).append("};");

            CtClass ctClass = TransformHelper.getInstance().get(objectName + "[]");
            CtField argsField = new CtField(ctClass, "args", implClass);
            argsField.setModifiers(Modifier.PRIVATE);
            TransformHelper.addField(implClass, argsField);
            getArgsMethodImpl.setBody("{return args;}");
        } else {
            getArgsMethodImpl.setBody("{return null;}");
        }
        constructor.setBody(constructBuilder.append('}').toString());
        TransformHelper.addConstructor(implClass, constructor);
        TransformHelper.addMethod(implClass, getArgsMethodImpl);

        if (methodInfo.isStaticMethod) {
            getTagMethodImpl.setBody("{return " + "p0" + ";}");
            getTargetMethodImpl.setBody("{return " + "null" + ";}");
        } else {
            getTagMethodImpl.setBody("{return " + "p1" + ";}");
            getTargetMethodImpl.setBody("{return " + "p0" + ";}");
        }
        TransformHelper.addMethod(implClass, getTargetMethodImpl);
        TransformHelper.addMethod(implClass, getTagMethodImpl);

        String newMethodName = getNewMethodName(originalMethod, methodIndex);
        proceedMethodImpl.setBody(createImplMethodBody(clazz, newMethodName, parameterTypes, methodInfo));
        TransformHelper.addMethod(implClass, proceedMethodImpl);

        return implClass;
    }

    private String createImplMethodBody(CtClass clazz, String newMethodName, CtClass[] parameterTypes, MethodInfo methodInfo) {
        String className = clazz.getName();
        int fromIndex = methodInfo.isStaticMethod ? 1 : 2;
        StringBuilder sb = null;
        for (int i = fromIndex; i < parameterTypes.length; i++) {
            if (sb == null) sb = new StringBuilder();
            sb.append("$0.p").append(i);
            if (i != parameterTypes.length - 1) sb.append(',');
        }
        String p = (sb == null ? "" : sb.toString());
        String targetObj = methodInfo.isStaticMethod ? className : "p0";
        if (methodInfo.hasReturnValue) {
            if (methodInfo.isBasicReturnType) {
                return "{" + methodInfo.typeName + " value=" + targetObj + "." + newMethodName + "(" + p + ");return new " + methodInfo.castedTypeName + "(value);}";
            } else {
                return "{return " + targetObj + "." + newMethodName + "(" + p + ");}";
            }
        } else {
            return "{" + targetObj + "." + newMethodName + "(" + p + "); return null;}";
        }
    }

    private String generateOriginalMethodCode(String tag, String aspectClassName, String joinPointClassName, MethodInfo methodInfo) {
        CtClass[] parameterTypes = methodInfo.parameterTypes;
        StringBuilder argSb = new StringBuilder();
        argSb.append("\"").append(tag).append("\"");
        if (parameterTypes != null && parameterTypes.length > 0) {
            for (int i = 0; i < parameterTypes.length; i++) {
                argSb.append(",$").append(i + 1);
            }
        }

        StringBuilder bodySb = new StringBuilder("{" + QsIAspect.class.getName() + " aspect = new " + aspectClassName + "();");
        String args;
        if (methodInfo.isStaticMethod) {
            args = argSb.toString();
        } else {
            args = "$0," + argSb.toString();
        }
        if (methodInfo.hasReturnValue) {
            if (methodInfo.isBasicReturnType) {
                bodySb.append(methodInfo.castedTypeName).append(" value = (").append(methodInfo.castedTypeName).append(")aspect.around(new ").append(joinPointClassName).append("(").append(args).append("));");
                bodySb.append("return value.").append(methodInfo.castMethod).append("();");
            } else {
                bodySb.append("return (").append(methodInfo.castedTypeName).append(")aspect.around(new ").append(joinPointClassName).append("(").append(args).append("));");
            }
        } else {
            bodySb.append("aspect.around(new ").append(joinPointClassName).append("(").append(args).append("));");
        }
        return bodySb.append('}').toString();
    }

    private MethodInfo getReturnInfo(CtMethod originalMethod) throws Exception {
        MethodInfo value = new MethodInfo();
        CtClass returnType = originalMethod.getReturnType();
        value.isStaticMethod = Modifier.isStatic(originalMethod.getModifiers());
        value.typeName = returnType.getName();
        value.parameterTypes = originalMethod.getParameterTypes();

        if (returnType == CtClass.voidType) {
            value.hasReturnValue = false;
            return value;
        }
        value.hasReturnValue = true;
        String name = returnType.getName();
        switch (name) {
            case "int":
                value.isBasicReturnType = true;
                value.castedTypeName = Integer.class.getName();
                value.castMethod = "intValue";
                break;
            case "long":
                value.isBasicReturnType = true;
                value.castedTypeName = Long.class.getName();
                value.castMethod = "longValue";
                break;
            case "float":
                value.isBasicReturnType = true;
                value.castedTypeName = Float.class.getName();
                value.castMethod = "floatValue";
                break;
            case "double":
                value.isBasicReturnType = true;
                value.castedTypeName = Double.class.getName();
                value.castMethod = "doubleValue";
                break;
            case "byte":
                value.isBasicReturnType = true;
                value.castedTypeName = Byte.class.getName();
                value.castMethod = "byteValue";
                break;
            case "short":
                value.isBasicReturnType = true;
                value.castedTypeName = Short.class.getName();
                value.castMethod = "shortValue";
                break;
            case "boolean":
                value.isBasicReturnType = true;
                value.castedTypeName = Boolean.class.getName();
                value.castMethod = "booleanValue";
                break;
            case "char":
                value.isBasicReturnType = true;
                value.castedTypeName = Character.class.getName();
                value.castMethod = "charValue";
                break;
            default:
                value.isBasicReturnType = false;
                value.castedTypeName = name;
                value.castMethod = null;
                break;
        }
        return value;
    }

    private static class MethodInfo {
        boolean   isStaticMethod;
        boolean   hasReturnValue;
        boolean   isBasicReturnType;
        String    castedTypeName;
        String    typeName;
        String    castMethod;
        CtClass[] parameterTypes;
    }

    /**
     * copy original method body to new method
     */
    private void addNewMethod(CtClass clazz, CtMethod originalMethod, int methodIndex, MethodInfo methodInfo) throws Exception {
        String newMethodName = getNewMethodName(originalMethod, methodIndex);
        CtMethod newMethod = new CtMethod(originalMethod, clazz, null);
        newMethod.setName(newMethodName);
        if (methodInfo.isStaticMethod) {
            newMethod.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
        } else {
            newMethod.setModifiers(Modifier.PUBLIC);
        }
        TransformHelper.addMethod(clazz, newMethod);
    }

    private String getNewMethodName(CtMethod originalMethod, int methodIndex) {
        return originalMethod.getName() + "_QsAspect_" + methodIndex;
    }

    private String getAspectClassName(CtMethod method) {
        javassist.bytecode.MethodInfo methodInfo = method.getMethodInfo();
        List<AttributeInfo> attributes = methodInfo.getAttributes();
        for (AttributeInfo info : attributes) {
            if (info instanceof AnnotationsAttribute) {
                Annotation[] annotations = ((AnnotationsAttribute) info).getAnnotations();
                for (Annotation ann : annotations) {
                    String typeName = ann.getTypeName();
                    if (typeName.equals(QsAspect.class.getName())) {
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

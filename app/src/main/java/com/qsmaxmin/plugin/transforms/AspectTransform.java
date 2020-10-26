package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.aspect.JoinPoint;
import com.qsmaxmin.annotation.aspect.QsAspect;
import com.qsmaxmin.annotation.aspect.QsIAspect;
import com.qsmaxmin.plugin.helper.TransformHelper;

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
public class AspectTransform {
    private static CtClass  jointPointClass;
    private static CtMethod proceedMethod;
    private static CtMethod getTargetMethod;

    public static boolean transform(CtClass clazz, CtMethod[] declaredMethods, String rootPath) throws Exception {
        if (clazz.isFrozen()) clazz.defrost();

        if (jointPointClass == null) {
            jointPointClass = TransformHelper.getInstance().get(JoinPoint.class.getName());
            proceedMethod = jointPointClass.getDeclaredMethod("proceed");
            getTargetMethod = jointPointClass.getDeclaredMethod("getTarget");
        }
        int methodIndex = 0;
        for (CtMethod originalMethod : declaredMethods) {
            Object ann = originalMethod.getAnnotation(QsAspect.class);
            if (ann != null) {
                String aspectClassName = getAspectClassName(originalMethod);
                if (aspectClassName == null) continue;

                MethodInfo methodInfo = getReturnInfo(originalMethod);

                addNewMethod(clazz, originalMethod, methodIndex, methodInfo);
                CtClass jointPointClass = createJointPointClass(clazz, originalMethod, methodIndex, methodInfo);

                String code = generateOriginalMethodCode(originalMethod, aspectClassName, jointPointClass.getName(), methodInfo);
                originalMethod.setBody(code);

                jointPointClass.writeFile(rootPath);
                methodIndex++;
            }

        }
        return methodIndex > 0;
    }


    private static CtClass createJointPointClass(CtClass clazz, CtMethod originalMethod, int methodIndex, MethodInfo methodInfo) throws Exception {
        String implClassName = clazz.getName() + "_QsAspect" + methodIndex;
        CtClass implClass = TransformHelper.getInstance().makeClassIfNotExists(implClassName);
        if (implClass.isFrozen()) implClass.defrost();

        implClass.setInterfaces(new CtClass[]{jointPointClass});
        CtClass[] parameterTypes = originalMethod.getParameterTypes();

        if (methodInfo.isStaticMethod) {
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

            CtMethod proceedMethodImpl = new CtMethod(proceedMethod, implClass, null);
            String newMethodName = getNewMethodName(originalMethod, methodIndex);
            proceedMethodImpl.setBody(createImplMethodBodyStatic(clazz.getName(), newMethodName, parameterTypes, methodInfo));
            TransformHelper.addMethod(implClass, proceedMethodImpl);

            CtMethod getTargetMethodImpl = new CtMethod(getTargetMethod, implClass, null);
            getTargetMethodImpl.setBody("{return null;}");
            TransformHelper.addMethod(implClass, getTargetMethodImpl);

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

            CtMethod proceedMethodImpl = new CtMethod(proceedMethod, implClass, null);
            String newMethodName = getNewMethodName(originalMethod, methodIndex);
            proceedMethodImpl.setBody(createImplMethodBody(newMethodName, parameterTypes, methodInfo));
            TransformHelper.addMethod(implClass, proceedMethodImpl);

            CtMethod getTargetMethodImpl = new CtMethod(getTargetMethod, implClass, null);
            getTargetMethodImpl.setBody("{return target;}");
            TransformHelper.addMethod(implClass, getTargetMethodImpl);
        }
        return implClass;
    }

    private static String createImplMethodBodyStatic(String className, String newMethodName, CtClass[] parameterTypes, MethodInfo methodInfo) {
        if (parameterTypes != null && parameterTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append("$0.p").append(i);
                if (i != parameterTypes.length - 1) sb.append(',');
            }
            if (methodInfo.hasReturnValue) {
                if (methodInfo.isCommonlyType) {
                    return "{" + methodInfo.typeName + " value = " + className + "." + newMethodName + "(" + sb.toString() + "); return new " + methodInfo.castedTypeName + "(value);}";
                } else {
                    return "{return " + className + "." + newMethodName + "(" + sb.toString() + ");}";
                }
            } else {
                return "{" + className + "." + newMethodName + "(" + sb.toString() + "); return null;}";
            }
        } else {
            if (methodInfo.hasReturnValue) {
                if (methodInfo.isCommonlyType) {
                    return "{" + methodInfo.typeName + " value = " + className + "." + newMethodName + "(); return new " + methodInfo.castedTypeName + "(value);}";
                } else {
                    return "{return " + className + "." + newMethodName + "();}";
                }
            } else {
                return "{" + className + "." + newMethodName + "(); return null;}";
            }
        }
    }

    private static String createImplMethodBody(String newMethodName, CtClass[] parameterTypes, MethodInfo methodInfo) {
        if (parameterTypes != null && parameterTypes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append("$0.p").append(i);
                if (i != parameterTypes.length - 1) sb.append(',');
            }
            if (methodInfo.hasReturnValue) {
                if (methodInfo.isCommonlyType) {
                    return "{" + methodInfo.typeName + " value = target." + newMethodName + "(" + sb.toString() + "); return new " + methodInfo.castedTypeName + "(value);}";
                } else {
                    return "{return target." + newMethodName + "(" + sb.toString() + ");}";
                }
            } else {
                return "{target." + newMethodName + "(" + sb.toString() + "); return null;}";
            }
        } else {
            if (methodInfo.hasReturnValue) {
                if (methodInfo.isCommonlyType) {
                    return "{" + methodInfo.typeName + " value = target." + newMethodName + "(); return new " + methodInfo.castedTypeName + "(value);}";
                } else {
                    return "{return (" + methodInfo.castedTypeName + ")target." + newMethodName + "();}";
                }
            } else {
                return "{target." + newMethodName + "(); return null;}";
            }
        }
    }


    private static String generateOriginalMethodCode(CtMethod originalMethod, String aspectClassName, String joinPointClassName, MethodInfo methodInfo) throws Exception {
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

        StringBuilder sb = new StringBuilder("{" + QsIAspect.class.getName() + " aspect = new " + aspectClassName + "();");
        if (methodInfo.isStaticMethod) {
            if (methodInfo.hasReturnValue) {
                if (methodInfo.isCommonlyType) {
                    sb.append(methodInfo.castedTypeName).append(" value = (").append(methodInfo.castedTypeName).append(")aspect.around(new ").append(joinPointClassName).append("(").append(args).append("));");
                    sb.append("return value.").append(methodInfo.castMethod).append("();");
                } else {
                    sb.append("return (").append(methodInfo.castedTypeName).append(")aspect.around(new ").append(joinPointClassName).append("(").append(args).append("));");
                }
            } else {
                sb.append("aspect.around(new ").append(joinPointClassName).append("(").append(args).append("));");
            }
        } else {
            if (args.length() == 0) {
                if (methodInfo.hasReturnValue) {
                    if (methodInfo.isCommonlyType) {
                        sb.append(methodInfo.castedTypeName).append(" value = (").append(methodInfo.castedTypeName).append(")aspect.around(new ").append(joinPointClassName).append("($0));");
                        sb.append("return value.").append(methodInfo.castMethod).append("();");
                    } else {
                        sb.append("return (").append(methodInfo.castedTypeName).append(")aspect.around(new ").append(joinPointClassName).append("($0));");
                    }
                } else {
                    sb.append("aspect.around(new ").append(joinPointClassName).append("($0));");
                }
            } else {
                if (methodInfo.hasReturnValue) {
                    if (methodInfo.isCommonlyType) {
                        sb.append(methodInfo.castedTypeName).append(" value = (").append(methodInfo.castedTypeName).append(")aspect.around(new ").append(joinPointClassName).append("($0,").append(args).append("));");
                        sb.append("return value.").append(methodInfo.castMethod).append("();");
                    } else {
                        sb.append("return (").append(methodInfo.castedTypeName).append(")aspect.around(new ").append(joinPointClassName).append("($0,").append(args).append("));");
                    }
                } else {
                    sb.append("aspect.around(new ").append(joinPointClassName).append("($0,").append(args).append("));");
                }
            }
        }
        return sb.append('}').toString();
    }


    private static MethodInfo getReturnInfo(CtMethod originalMethod) throws Exception {
        MethodInfo value = new MethodInfo();
        CtClass returnType = originalMethod.getReturnType();
        value.isStaticMethod = Modifier.isStatic(originalMethod.getModifiers());
        value.typeName = returnType.getName();

        if (returnType == CtClass.voidType) {
            value.hasReturnValue = false;
            return value;
        }
        value.hasReturnValue = true;
        String name = returnType.getName();
        switch (name) {
            case "int":
                value.isCommonlyType = true;
                value.castedTypeName = Integer.class.getName();
                value.castMethod = "intValue";
                break;
            case "long":
                value.isCommonlyType = true;
                value.castedTypeName = Long.class.getName();
                value.castMethod = "longValue";
                break;
            case "float":
                value.isCommonlyType = true;
                value.castedTypeName = Float.class.getName();
                value.castMethod = "floatValue";
                break;
            case "double":
                value.isCommonlyType = true;
                value.castedTypeName = Double.class.getName();
                value.castMethod = "doubleValue";
                break;
            case "byte":
                value.isCommonlyType = true;
                value.castedTypeName = Byte.class.getName();
                value.castMethod = "byteValue";
                break;
            case "short":
                value.isCommonlyType = true;
                value.castedTypeName = Short.class.getName();
                value.castMethod = "shortValue";
                break;
            case "boolean":
                value.isCommonlyType = true;
                value.castedTypeName = Boolean.class.getName();
                value.castMethod = "booleanValue";
                break;
            case "char":
                value.isCommonlyType = true;
                value.castedTypeName = Character.class.getName();
                value.castMethod = "charValue";
                break;
            default:
                value.isCommonlyType = false;
                value.castedTypeName = name;
                value.castMethod = null;
                break;
        }
        return value;
    }

    private static class MethodInfo {
        boolean isStaticMethod;
        boolean hasReturnValue;
        boolean isCommonlyType;
        String  castedTypeName;
        String  typeName;
        String  castMethod;
    }

    /**
     * copy original method body to new method
     */
    private static void addNewMethod(CtClass clazz, CtMethod originalMethod, int methodIndex, MethodInfo methodInfo) throws Exception {
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

    private static String getNewMethodName(CtMethod originalMethod, int methodIndex) {
        return originalMethod.getName() + "_QsAspect_" + methodIndex;
    }

    private static String getAspectClassName(CtMethod method) {
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

package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.plugin.helper.TransformHelper;
import com.qsmaxmin.plugin.model.ModelTransformConfig;

import java.util.HashMap;
import java.util.HashSet;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/8/28 16:09
 * @Description
 */
class ProcessRoute {
    static final String BIND_METHOD_NAME = "bindRouteByQsPlugin";

    static void beginTransform(ModelTransformConfig cacheInfo) throws Exception {
        if (!cacheInfo.hasRouteData()) return;
        CtClass routeClass = TransformHelper.getInstance().get(cacheInfo.engineClassName);
        String rootPath = cacheInfo.engineRootPath;
        HashMap<String, String> routeMap = cacheInfo.classNameRoutes;

        if (routeClass == null) {
            throw new Exception("使用@Route注解时，你的Application需要继承QsApplication或者实现QsIApplication接口，且需要在你的Application类上添加@AutoRoute注解");
        }
        if (routeClass.isFrozen()) routeClass.defrost();

        HashSet<String> routePathList = new HashSet<>();
        StringBuilder sb = new StringBuilder("{");
        for (String routeClassName : routeMap.keySet()) {
            String routePath = routeMap.get(routeClassName);
            sb.append("$1.put(\"").append(routePath).append("\", ").append(routeClassName).append(".class);");
            if (routePathList.contains(routePath)) {
                throw new Exception("@Route注解时使用了相同的值：@Route(" + routePath + ")，需确保该值唯一！");
            }
            routePathList.add(routePath);
        }
        String code = sb.append('}').toString();

        CtMethod engineMethod = TransformHelper.getDeclaredMethod(routeClass, BIND_METHOD_NAME);
        if (engineMethod != null) {
            engineMethod.setBody(code);

        } else {
            CtClass[] paramsClass = new CtClass[]{TransformHelper.getInstance().get(HashMap.class.getName())};
            engineMethod = new CtMethod(CtClass.voidType, BIND_METHOD_NAME, paramsClass, routeClass);
            engineMethod.setModifiers(Modifier.PUBLIC);
            engineMethod.setBody(code);
            routeClass.addMethod(engineMethod);
        }
        routeClass.writeFile(rootPath);
    }
}

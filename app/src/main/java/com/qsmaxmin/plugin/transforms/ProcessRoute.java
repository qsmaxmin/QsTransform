package com.qsmaxmin.plugin.transforms;

import com.qsmaxmin.annotation.route.Route;
import com.qsmaxmin.plugin.helper.TransformHelper;

import java.util.HashMap;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/8/28 16:09
 * @Description
 */
class ProcessRoute {
    static final  String                  BIND_METHOD_NAME = "bindRouteByQsPlugin";
    private final HashMap<String, String> routeMap;
    private       CtClass                 routeClass;
    private       String                  rootPath;

    public ProcessRoute() {
        routeMap = new HashMap<>();
    }

    void addRouteClass(Route route, CtClass clazz) {
        String routePath = route.value();
        String className = clazz.getName();
        routeMap.put(routePath, className);
    }

    void setRouteEngine(CtClass clazz, String rootPath) {
        this.routeClass = clazz;
        this.rootPath = rootPath;
    }

    void beginTransform() throws Exception {
        if (routeMap.isEmpty()) return;
        if (routeClass == null) {
            throw new Exception("你的Application需要继承QsApplication或者实现QsIApplication接口，且需要在你的Application类上添加@AutoRoute注解");
        }
        if (routeClass.isFrozen()) routeClass.defrost();

        StringBuilder sb = new StringBuilder("{");
        for (String routePath : routeMap.keySet()) {
            String routeClassPath = routeMap.get(routePath);
            sb.append("$1.put(\"").append(routePath).append("\", ").append(routeClassPath).append(".class);");
        }
        String code = sb.append('}').toString();

        CtMethod engineMethod = TransformHelper.getDeclaredMethod(routeClass, BIND_METHOD_NAME);
        if (engineMethod != null) {
            engineMethod.insertAfter(code);

        } else {
            CtClass[] paramsClass = new CtClass[]{TransformHelper.getInstance().get(HashMap.class.getName())};
            CtMethod ctMethod = new CtMethod(CtClass.voidType, BIND_METHOD_NAME, paramsClass, routeClass);
            ctMethod.setModifiers(Modifier.PUBLIC);
            ctMethod.setBody(code);
            routeClass.addMethod(ctMethod);
        }
        routeClass.writeFile(rootPath);
    }
}

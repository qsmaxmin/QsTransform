package com.qsmaxmin.plugin.model;

import com.google.gson.Gson;

import org.gradle.api.Project;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.HashMap;

import javassist.CtClass;

/**
 * @CreateBy qsmaxmin
 * @Date 2021/8/27 17:15
 * @Description
 */
public class ModelTransformConfig {
    public String                  engineRootPath;
    public String                  engineClassName;
    /**
     * key：className
     * value: routePath
     */
    public HashMap<String, String> classNameRoutes;

    public static ModelTransformConfig readCacheInfo(Project project) {
        try {
            File file = getCacheDataFile(project);
            return new Gson().fromJson(new FileReader(file), ModelTransformConfig.class);
        } catch (Exception ignored) {
        }
        return null;
    }

    public boolean addRouteClass(String className, String routePath) {
        if (routePath != null && routePath.length() > 0) {
            if (classNameRoutes == null) classNameRoutes = new HashMap<>();
            classNameRoutes.put(className, routePath);
            return true;
        }
        return false;
    }

    public void removeRouteClass(String className) {
        if (classNameRoutes == null || classNameRoutes.isEmpty()) return;
        classNameRoutes.remove(className);
    }

    public void setRouteEngine(CtClass clazz, String rootPath) {
        engineClassName = clazz.getName();
        engineRootPath = rootPath;
    }

    public boolean hasRouteData() {
        return classNameRoutes != null && classNameRoutes.size() > 0;
    }

    public void save(Project project) {
        try {
            File targetFile = getCacheDataFile(project);
            Gson gson = new Gson();
            String data = gson.toJson(this, ModelTransformConfig.class);
            FileOutputStream fos = new FileOutputStream(targetFile);
            fos.write(data.getBytes());
        } catch (Exception ignored) {
        }
    }

    public void reset(Project project) {
        engineRootPath = null;
        engineClassName = null;
        classNameRoutes = null;
        save(project);
    }

    private static File getCacheDataFile(Project project) {
        File buildDir = project.getBuildDir();
        File targetFile = new File(buildDir.getAbsolutePath(), "cache/QsTransform_TransformConfig.txt");
        if (!targetFile.exists()) {
            File parentFile = targetFile.getParentFile();
            if (!parentFile.exists()) {
                boolean mkdirs = parentFile.mkdirs();
            }
        }
        return targetFile;
    }

    @Override public String toString() {
        return "ModelTransformInfo{" +
                "\nengineRootPath='" + engineRootPath + '\'' +
                ", \nengineClassName='" + engineClassName + '\'' +
                ", \nrouteClassNames=" + classNameRoutes +
                '}';
    }
}

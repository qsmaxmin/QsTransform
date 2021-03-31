package com.qsmaxmin.plugin.model;

import com.google.gson.Gson;
import com.qsmaxmin.plugin.helper.TransformHelper;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/9/29 10:54
 * @Description 添加新仓库配置时，需要改动的地方有：
 * 1，projectNames中新增仓库名称{@link ModelConfigInfo#projectNames}
 * 2，create方法给新仓库赋值，其中dependencies数组索引与projectNames数组索引是一致的。{@link ModelConfigInfo#create(String[])}
 * 3，toString方法修改随意
 */
public class ModelConfigInfo {
    private static final String   API_REPOSITORY                = "https://api.github.com/repos/qsmaxmin/%s/releases/latest";
    private static final String   DEPENDENCY_REPOSITORY         = "com.github.qsmaxmin:%1s:%2s";
    private static final String   DEPENDENCY_REPOSITORY_OFFLINE = "com.qsmaxmin.qsbase:%1s:%2s";
    private static final String[] projectNames                  = {"QsBase", "QsAnnotation"};
    public               String   qsBaseDependency;
    public               String   qsAnnotationDependency;
    private              long     currentTime;

    private static ModelConfigInfo create(String[] dependencies) {
        ModelConfigInfo configInfo = new ModelConfigInfo();
        configInfo.qsBaseDependency = dependencies[0];
        configInfo.qsAnnotationDependency = dependencies[1];
        configInfo.currentTime = System.currentTimeMillis();
        return configInfo;
    }

    @Override public String toString() {
        return "ModelConfigInfo{" +
                "qsBaseDependency='" + qsBaseDependency + '\'' +
                ", qsAnnotationDependency='" + qsAnnotationDependency + '\'' +
                ", currentTime=" + currentTime +
                "}";
    }

    @NotNull public static ModelConfigInfo getDebug() {
        String[] dependencies = new String[projectNames.length];
        for (int i = 0; i < dependencies.length; i++) {
            dependencies[i] = String.format(DEPENDENCY_REPOSITORY_OFFLINE, projectNames[i], "9.9.9");
        }
        return create(dependencies);
    }

    @NotNull public static ModelConfigInfo get(@Nonnull Project project) throws Exception {
        Gson gson = new Gson();
        ModelConfigInfo info = readCachedInfo(project, gson);
        if (info != null) {
            System.out.println("\t> use cache config:" + info.toString());
            runOnHttpThread(new SafeRunnable() {
                @Override void safeRun() throws Exception {
                    ModelConfigInfo newInfo = requestOnlineConfigInfo(gson);
                    newInfo.saveConfigInfo(project, gson);
                    System.out.println("\t> async refresh config:" + newInfo.toString());
                }
            });
        } else {
            info = requestOnlineConfigInfo(gson);
            info.saveConfigInfo(project, gson);
            System.out.println("\t> use online config:" + info.toString());
        }
        return info;
    }

    private static ModelConfigInfo readCachedInfo(@Nonnull Project project, Gson gson) {
        File file = getCachedConfigFile(project);
        if (!file.exists()) return null;
        InputStreamReader isr = null;
        try {
            FileInputStream fis = new FileInputStream(file);
            isr = new InputStreamReader(fis);
            ModelConfigInfo info = gson.fromJson(isr, ModelConfigInfo.class);
            if (info != null && info.notTimeout()) {
                return info;
            }
        } catch (Exception ignored) {
        } finally {
            TransformHelper.closeStream(isr);
        }
        return null;
    }


    public void saveConfigInfo(@Nonnull Project project, Gson gson) {
        File file = getCachedConfigFile(project);
        File parentFile = file.getParentFile();
        if (!parentFile.exists() && parentFile.mkdirs()) {
            return;
        }
        String text = gson.toJson(this);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(text.getBytes());
        } catch (Exception ignored) {
        } finally {
            TransformHelper.closeStream(fos);
        }
    }

    private static File getCachedConfigFile(@Nonnull Project project) {
        StartParameter startParameter = project.getGradle().getStartParameter();
        File homeDir = startParameter.getGradleUserHomeDir();
        return new File(homeDir, "caches/QsTransform/config2.txt");
    }


    /**
     * 在线请求配置信息
     */
    @NotNull private static ModelConfigInfo requestOnlineConfigInfo(Gson gson) throws Exception {
        String[] dependencies = new String[projectNames.length];
        AtomicInteger integer = new AtomicInteger(0);
        for (int i = 0; i < projectNames.length; i++) {
            int finalIndex = i;
            runOnHttpThread(new SafeRunnable() {
                @Override void safeRun() throws Exception {
                    try {
                        String projectName = projectNames[finalIndex];
                        ModelRepositoryInfo info = TransformHelper.getModelConfigInfo(String.format(API_REPOSITORY, projectName), gson);
                        dependencies[finalIndex] = String.format(DEPENDENCY_REPOSITORY, projectName, info.tag_name);
                    } finally {
                        if (integer.addAndGet(1) == projectNames.length) {
                            synchronized (projectNames) {
                                projectNames.notifyAll();
                            }
                        }
                    }
                }
            });
        }
        if (integer.get() < projectNames.length) {
            synchronized (projectNames) {
                projectNames.wait();
            }
        }
        for (String dependency : dependencies) {
            if (dependency == null) throw new Exception("request online config info failed......");
        }
        return create(dependencies);
    }

    private static void runOnHttpThread(SafeRunnable action) {
        new Thread(action).start();
    }


    /**
     * 缓存文件只保存24小时
     */
    private boolean notTimeout() {
        return System.currentTimeMillis() - currentTime <= 24 * 3600_000;
    }

    private static abstract class SafeRunnable implements Runnable {
        @Override public void run() {
            try {
                safeRun();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        abstract void safeRun() throws Exception;
    }
}

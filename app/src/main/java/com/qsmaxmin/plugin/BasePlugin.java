package com.qsmaxmin.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.google.gson.Gson;
import com.qsmaxmin.plugin.extension.ModelConfigInfo;
import com.qsmaxmin.plugin.extension.ModelQsBaseInfo;
import com.qsmaxmin.plugin.extension.MyExtension;
import com.qsmaxmin.plugin.transforms.MainTransform;

import org.gradle.StartParameter;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.annotation.Nonnull;

/**
 * @CreateBy administrator
 * @Date 2020/8/5 9:11
 * @Description
 */
abstract class BasePlugin implements Plugin<Project> {
    private static final String API_LAST_VERSION     = "https://api.github.com/repos/qsmaxmin/QsBase/releases/latest";
    private static final String DEPENDENCY_OFFLINE   = "com.qsmaxmin.qsbase:QsBase:9.9.9";
    private static final String DEPENDENCY_ONLINE_ID = "com.github.qsmaxmin:QsBase:";
    private static final String TAG_DEFAULT          = "10.9.8";

    @Override public void apply(@Nonnull Project project) {
        project.getExtensions().create("QsPlugin", MyExtension.class);

        DependencyHandler dependencies = project.getDependencies();
        dependencies.add("implementation", "com.github.qsmaxmin:QsAnnotation:1.0.2");
        if (addQsBaseRepositories()) {
            dependencies.add("implementation", getQsBaseDependency(project));
        }

        if (project.getPlugins().hasPlugin(AppPlugin.class)) {
            MainTransform transform = new MainTransform(project);
            project.getExtensions().getByType(AppExtension.class).registerTransform(transform);
        }
    }

    private String getQsBaseDependency(Project project) {
        if (isDebug()) {
            return DEPENDENCY_OFFLINE;
        } else {
            return getOnLineDependency(project);
        }
    }

    /**
     * 获取QsBase最新版本号
     */
    private String getOnLineDependency(Project project) {
        ModelConfigInfo configInfo = getConfigInfo(project);
        if (configInfo != null && !configInfo.isTimeOut()) {
            System.out.println("\t> use cache dependency '" + configInfo.qsBaseDependency + "'");
            new Thread(() -> {
                ModelConfigInfo info = getModelConfigInfo(project);
                if (info != null) {
                    System.out.println("\t> async refresh dependency successfully! " + info.qsBaseDependency);
                } else {
                    System.out.println("\t> async refresh dependency failed");
                }
            }).start();
            return configInfo.qsBaseDependency;
        }
        ModelConfigInfo newConfig = getModelConfigInfo(project);
        if (newConfig != null) {
            System.out.println("\t> Get the latest version number of 'QsBase' successfully. dependency '" + newConfig.qsBaseDependency + "'");
            return newConfig.qsBaseDependency;
        } else {
            String defaultOnLineDependency = DEPENDENCY_ONLINE_ID + TAG_DEFAULT;
            System.out.println("\t> Failed to get the latest version number of 'QsBase'. use default dependency '" + defaultOnLineDependency + "'");
            return defaultOnLineDependency;
        }
    }

    @Nullable private ModelConfigInfo getModelConfigInfo(Project project) {
        InputStreamReader isr = null;
        ModelConfigInfo newConfig = null;
        try {
            URL url = new URL(API_LAST_VERSION);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.connect();

            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                isr = new InputStreamReader(is);
                Gson gson = new Gson();
                ModelQsBaseInfo info = gson.fromJson(isr, ModelQsBaseInfo.class);

                newConfig = new ModelConfigInfo();
                newConfig.qsBaseDependency = DEPENDENCY_ONLINE_ID + info.tag_name;
                newConfig.currentTime = System.currentTimeMillis();
                saveConfigInfo(project, newConfig, gson);
            }
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeStream(isr);
        }
        return newConfig;
    }

    private void saveConfigInfo(@Nonnull Project project, ModelConfigInfo configInfo, Gson gson) {
        File file = getCachedConfigFile(project);
        File parentFile = file.getParentFile();
        if (!parentFile.exists() && parentFile.mkdirs()) {
            return;
        }
        String text = gson.toJson(configInfo);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(text.getBytes());
        } catch (Exception ignored) {
        } finally {
            closeStream(fos);
        }
    }

    private ModelConfigInfo getConfigInfo(@Nonnull Project project) {
        File file = getCachedConfigFile(project);
        ModelConfigInfo info = null;
        InputStreamReader isr = null;
        if (file.exists()) {
            System.out.println("\t> config file  " + file.getAbsolutePath());
            try {
                FileInputStream fis = new FileInputStream(file);
                isr = new InputStreamReader(fis);
                Gson gson = new Gson();
                info = gson.fromJson(isr, ModelConfigInfo.class);
            } catch (Exception ignored) {
            } finally {
                closeStream(isr);
            }
        }
        return info;
    }

    private File getCachedConfigFile(@Nonnull Project project) {
        StartParameter startParameter = project.getGradle().getStartParameter();
        File homeDir = startParameter.getGradleUserHomeDir();
        return new File(homeDir, "caches/QsTransform/config.txt");
    }

    private void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected abstract boolean isDebug();

    protected abstract boolean addQsBaseRepositories();
}

package com.qsmaxmin.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.qsmaxmin.plugin.extension.MyExtension;
import com.qsmaxmin.plugin.transforms.MainTransform;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import javax.annotation.Nonnull;

/**
 * @CreateBy administrator
 * @Date 2020/8/5 9:11
 * @Description
 */
abstract class BasePlugin implements Plugin<Project> {

    @Override public void apply(@Nonnull Project project) {
        project.getExtensions().create("QsPlugin", MyExtension.class);

        DependencyHandler dependencies = project.getDependencies();
        if (addQsBaseRepositories()) {
            addQsBaseDependencies(dependencies, isDebug());
        }
        addQsPluginDependencies(dependencies, isDebug());

        if (project.getPlugins().hasPlugin(AppPlugin.class)) {
            MainTransform transform = new MainTransform(project);
            project.getExtensions().getByType(AppExtension.class).registerTransform(transform);
        }
    }

    private void addQsBaseDependencies(DependencyHandler dependencies, boolean isDebug) {
        if (isDebug) {
            dependencies.add("implementation", "com.qsmaxmin.qsbase:QsBase:9.9.9");
        } else {
            dependencies.add("implementation", "com.github.qsmaxmin:QsBase:10.0.2");
        }
    }

    private void addQsPluginDependencies(DependencyHandler dependencies, boolean isDebug) {
        if (isDebug) {
            dependencies.add("annotationProcessor", "com.qsmaxmin.qsann:QsPlugin:9.9.9");
        } else {
            dependencies.add("annotationProcessor", "com.github.qsmaxmin:QsPlugin:10.0.1");
        }
    }

    private boolean isDebug() {
        return false;
    }

    protected abstract boolean addQsBaseRepositories();
}

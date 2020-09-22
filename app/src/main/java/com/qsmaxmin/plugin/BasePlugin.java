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
        addQsBaseDependencies(dependencies);
        dependencies.add("implementation", "com.github.qsmaxmin:QsAnnotation:1.0.2");

        if (project.getPlugins().hasPlugin(AppPlugin.class)) {
            MainTransform transform = new MainTransform(project);
            project.getExtensions().getByType(AppExtension.class).registerTransform(transform);
        }
    }

    private void addQsBaseDependencies(DependencyHandler dependencies) {
        if (addQsBaseRepositories()) {
            if (isDebug()) {
                dependencies.add("implementation", "com.qsmaxmin.qsbase:QsBase:9.9.9");
            } else {
                dependencies.add("implementation", "com.github.qsmaxmin:QsBase:10.6.5");
            }
        }
    }

    protected abstract boolean isDebug();

    protected abstract boolean addQsBaseRepositories();
}

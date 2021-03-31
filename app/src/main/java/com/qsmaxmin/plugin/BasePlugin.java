package com.qsmaxmin.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.qsmaxmin.plugin.extension.MyExtension;
import com.qsmaxmin.plugin.model.ModelConfigInfo;
import com.qsmaxmin.plugin.transforms.MainTransform;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.jetbrains.annotations.Nullable;

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
        ModelConfigInfo info = getModelConfigInfo(project);
        if (info != null) {
            dependencies.add("implementation", info.qsAnnotationDependency);
            if (addQsBaseRepositories()) {
                dependencies.add("implementation", info.qsBaseDependency);
            }
        }

        if (project.getPlugins().hasPlugin(AppPlugin.class)) {
            MainTransform transform = new MainTransform(project);
            project.getExtensions().getByType(AppExtension.class).registerTransform(transform);
        }
    }

    @Nullable private ModelConfigInfo getModelConfigInfo(@Nonnull Project project) {
        if (isDebug()) return ModelConfigInfo.getDebug();
        try {
            return ModelConfigInfo.get(project);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected abstract boolean isDebug();

    protected abstract boolean addQsBaseRepositories();
}

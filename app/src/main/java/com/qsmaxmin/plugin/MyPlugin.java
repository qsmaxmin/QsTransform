package com.qsmaxmin.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.annotation.Nonnull;

/**
 * @CreateBy administrator
 * @Date 2020/8/5 9:11
 * @Description
 */
public class MyPlugin implements Plugin<Project> {
    @Override public void apply(@Nonnull Project project) {
        project.getExtensions().create("QsPlugin", MyExtension.class);

        if (project.getPlugins().hasPlugin(AppPlugin.class)) {
            MainTransform transform = new MainTransform(project);
            project.getExtensions().getByType(AppExtension.class).registerTransform(transform);
        }
    }
}

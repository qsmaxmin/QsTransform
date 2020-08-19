package com.qsmaxmin.plugin;

import com.android.build.gradle.AppExtension;

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
        MyParams params = project.getExtensions().create("QsTransform", MyParams.class);

        if (params.enable) {
            System.out.println("> QsTransform {enable:true, showLog:" + params.showLog + "}");
            MainTransform transform = new MainTransform();
            TransformHelper.enableLog(params.showLog);
            project.getExtensions().getByType(AppExtension.class).registerTransform(transform);
        } else {
            System.out.println("> QsTransform disable");
        }
    }
}

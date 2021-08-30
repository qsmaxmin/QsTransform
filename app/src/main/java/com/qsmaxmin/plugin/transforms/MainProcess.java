package com.qsmaxmin.plugin.transforms;

import com.android.SdkConstants;
import com.qsmaxmin.annotation.aspect.QsAspect;
import com.qsmaxmin.annotation.bind.Bind;
import com.qsmaxmin.annotation.bind.BindBundle;
import com.qsmaxmin.annotation.bind.OnClick;
import com.qsmaxmin.annotation.event.Subscribe;
import com.qsmaxmin.annotation.permission.Permission;
import com.qsmaxmin.annotation.presenter.Presenter;
import com.qsmaxmin.annotation.properties.AutoProperty;
import com.qsmaxmin.annotation.properties.Property;
import com.qsmaxmin.annotation.route.AutoRoute;
import com.qsmaxmin.annotation.route.Route;
import com.qsmaxmin.annotation.thread.ThreadPoint;
import com.qsmaxmin.plugin.helper.TransformHelper;
import com.qsmaxmin.plugin.model.DataHolder;
import com.qsmaxmin.plugin.model.ModelTransformConfig;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;

/**
 * @CreateBy qsmaxmin
 * @Date 2021/5/10 16:58
 * @Description
 */
class MainProcess {
    private static final int                  STATE_PROPERTY     = 0b1;
    private static final int                  STATE_PRESENTER    = 0b1 << 1;
    private static final int                  STATE_ROUTE        = 0b1 << 2;
    private static final int                  STATE_EVENT        = 0b1 << 3;
    public static final  int                  STATE_BIND_VIEW    = 0b1 << 4;
    public static final  int                  STATE_ONCLICK      = 0b1 << 5;
    public static final  int                  STATE_BIND_BUNDLE  = 0b1 << 6;
    private static final int                  STATE_PERMISSION   = 0b1 << 7;
    private static final int                  STATE_THREAD_POINT = 0b1 << 8;
    private static final int                  STATE_ASPECT       = 0b1 << 9;
    private final        ModelTransformConfig transformConfig;
    private              ProcessAspect        processAspect;
    private              ProcessEvent         processEvent;
    private              ProcessPermission    processPermission;
    private              ProcessPresenter     processPresenter;
    private              ProcessProperty      processProperty;
    private              ProcessThreadPoint   processThreadPoint;
    private              ProcessViewBind      processViewBind;

    public MainProcess(@Nonnull ModelTransformConfig config) {
        this.transformConfig = config;
    }

    public void processRemovedFile(File inputFile) throws Exception {
        if (isClassFile(inputFile)) {
            CtClass ctClass;
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(inputFile.getAbsolutePath());
                ctClass = TransformHelper.getInstance().makeClass(fis, false);
                if (ctClass != null) {
                    transformConfig.removeRouteClass(ctClass.getName());
                }
            } finally {
                TransformHelper.closeStream(fis);
            }
        }
    }

    boolean processClassFile(String outputDirPath, File outputFile, File inputFile) throws Exception {
        if (isClassFile(inputFile)) {
            CtClass ctClass;
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(inputFile.getAbsolutePath());
                ctClass = TransformHelper.getInstance().makeClass(fis, false);
            } finally {
                TransformHelper.closeStream(fis);
            }
            return processJavaClassFile(outputDirPath, ctClass);

        } else {
            FileUtils.copyFile(inputFile, outputFile);
            return false;
        }
    }

    private boolean processJavaClassFile(String rootPath, CtClass clazz) throws Exception {
        if (clazz == null) return false;
        int state = 0;
        CtField[] declaredFields = clazz.getDeclaredFields();
        CtMethod[] declaredMethods = clazz.getDeclaredMethods();

        List<DataHolder<CtField, Bind>> bindData = null;
        List<DataHolder<CtField, BindBundle>> bindBundleData = null;
        List<DataHolder<CtField, Property>> propertyData = null;

        List<DataHolder<CtMethod, Subscribe>> subscribeData = null;
        List<DataHolder<CtMethod, Permission>> permissionData = null;
        List<DataHolder<CtMethod, ThreadPoint>> threadPointData = null;
        List<DataHolder<CtMethod, QsAspect>> qsAspectData = null;
        List<DataHolder<CtMethod, OnClick>> onClickData = null;

        for (CtField f : declaredFields) {
            Bind bind = TransformHelper.getFiledAnnotation(f, Bind.class);
            if (bind != null) {
                if (bindData == null) bindData = new ArrayList<>();
                bindData.add(new DataHolder<>(f, bind));
            }
            BindBundle bindBundle = TransformHelper.getFiledAnnotation(f, BindBundle.class);
            if (bindBundle != null) {
                if (bindBundleData == null) bindBundleData = new ArrayList<>();
                bindBundleData.add(new DataHolder<>(f, bindBundle));
            }
            Property property = TransformHelper.getFiledAnnotation(f, Property.class);
            if (property != null) {
                if (propertyData == null) propertyData = new ArrayList<>();
                propertyData.add(new DataHolder<>(f, property));
            }
        }
        for (CtMethod m : declaredMethods) {
            Subscribe subscribe = TransformHelper.getMethodAnnotation(m, Subscribe.class);
            if (subscribe != null) {
                if (subscribeData == null) subscribeData = new ArrayList<>();
                subscribeData.add(new DataHolder<>(m, subscribe));
            }

            Permission permission = TransformHelper.getMethodAnnotation(m, Permission.class);
            if (permission != null) {
                if (permissionData == null) permissionData = new ArrayList<>();
                permissionData.add(new DataHolder<>(m, permission));
            }

            ThreadPoint threadPoint = TransformHelper.getMethodAnnotation(m, ThreadPoint.class);
            if (threadPoint != null) {
                if (threadPointData == null) threadPointData = new ArrayList<>();
                threadPointData.add(new DataHolder<>(m, threadPoint));
            }

            QsAspect qsAspect = TransformHelper.getMethodAnnotation(m, QsAspect.class);
            if (qsAspect != null) {
                if (qsAspectData == null) qsAspectData = new ArrayList<>();
                qsAspectData.add(new DataHolder<>(m, qsAspect));
            }

            OnClick onClick = TransformHelper.getMethodAnnotation(m, OnClick.class);
            if (onClick != null) {
                if (onClickData == null) onClickData = new ArrayList<>();
                onClickData.add(new DataHolder<>(m, onClick));
            }
        }

        AutoProperty autoProperty = TransformHelper.getClassAnnotation(clazz, AutoProperty.class);
        if (autoProperty != null && propertyData != null) {
            if (processProperty == null) processProperty = new ProcessProperty();
            processProperty.transform(clazz, propertyData);
            state |= STATE_PROPERTY;
        }

        Presenter presenter = TransformHelper.getClassAnnotation(clazz, Presenter.class);
        if (presenter != null) {
            if (processPresenter == null) processPresenter = new ProcessPresenter();
            processPresenter.transform(clazz);
            state |= STATE_PRESENTER;
        }

        AutoRoute autoRoute = TransformHelper.getClassAnnotation(clazz, AutoRoute.class);
        if (autoRoute != null) {
            transformConfig.setRouteEngine(clazz, rootPath);
        }

        Route route = TransformHelper.getClassAnnotation(clazz, Route.class);
        if (route != null && transformConfig.addRouteClass(clazz.getName(), route.value())) {
            state |= STATE_ROUTE;
        }

        if (subscribeData != null) {
            if (processEvent == null) processEvent = new ProcessEvent();
            processEvent.transform(clazz, subscribeData, rootPath);
            state |= STATE_EVENT;
        }

        if (onClickData != null || bindData != null || bindBundleData != null) {
            if (bindData != null) state |= STATE_BIND_VIEW;
            if (onClickData != null) state |= STATE_ONCLICK;
            if (bindBundleData != null) state |= STATE_BIND_BUNDLE;

            if (processViewBind == null) processViewBind = new ProcessViewBind();
            processViewBind.transform(clazz, bindData, bindBundleData, onClickData, rootPath);
        }

        if (permissionData != null) {
            if (processPermission == null) processPermission = new ProcessPermission();
            processPermission.transform(clazz, permissionData, rootPath);
            state |= STATE_PERMISSION;
        }

        if (threadPointData != null) {
            if (processThreadPoint == null) processThreadPoint = new ProcessThreadPoint();
            processThreadPoint.transform(clazz, threadPointData, rootPath);
            state |= STATE_THREAD_POINT;
        }

        if (qsAspectData != null) {
            if (processAspect == null) processAspect = new ProcessAspect();
            processAspect.transform(clazz, qsAspectData, rootPath);
            state |= STATE_ASPECT;
        }
        clazz.writeFile(rootPath);
        showTransformInfoLog(clazz, state);
        return state != 0;
    }

    private boolean isClassFile(File file) {
        return file.getName().endsWith(SdkConstants.DOT_CLASS);
    }

    private void showTransformInfoLog(CtClass clazz, int state) {
        if (state == 0 || !TransformHelper.isEnableLog()) return;
        boolean tag = false;
        StringBuilder sb = new StringBuilder("\t\t> transform class :");
        sb.append(clazz.getName()).append(" ----- [");
        if ((state & STATE_PROPERTY) == STATE_PROPERTY) {
            sb.append("@AutoProperty");
            tag = true;
        }
        if ((state & STATE_PRESENTER) == STATE_PRESENTER) {
            if (tag) sb.append(", ");
            sb.append("@Presenter");
            tag = true;
        }
        if ((state & STATE_ROUTE) == STATE_ROUTE) {
            if (tag) sb.append(", ");
            sb.append("@Route");
            tag = true;
        }
        if ((state & STATE_EVENT) == STATE_EVENT) {
            if (tag) sb.append(", ");
            sb.append("@Subscribe");
            tag = true;
        }
        if ((state & STATE_PERMISSION) == STATE_PERMISSION) {
            if (tag) sb.append(", ");
            sb.append("@Permission");
            tag = true;
        }
        if ((state & STATE_THREAD_POINT) == STATE_THREAD_POINT) {
            if (tag) sb.append(", ");
            sb.append("@ThreadPoint");
            tag = true;
        }
        if ((state & STATE_BIND_VIEW) == STATE_BIND_VIEW) {
            if (tag) sb.append(", ");
            sb.append("@Bind");
            tag = true;
        }
        if ((state & STATE_ONCLICK) == STATE_ONCLICK) {
            if (tag) sb.append(", ");
            sb.append("@OnClick");
            tag = true;
        }
        if ((state & STATE_BIND_BUNDLE) == STATE_BIND_BUNDLE) {
            if (tag) sb.append(", ");
            sb.append("@BindBundle");
            tag = true;
        }
        if ((state & STATE_ASPECT) == STATE_ASPECT) {
            if (tag) sb.append(", ");
            sb.append("@QsAspect");
        }
        println(sb.append("]").toString());
    }

    private void println(String text) {
        TransformHelper.println(text);
    }
}

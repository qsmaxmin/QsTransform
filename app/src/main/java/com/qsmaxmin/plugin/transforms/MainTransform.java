package com.qsmaxmin.plugin.transforms;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.qsmaxmin.annotation.properties.AutoProperty;
import com.qsmaxmin.plugin.extension.MyExtension;
import com.qsmaxmin.plugin.helper.TransformHelper;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;

public class MainTransform extends Transform {
    private static final int         STATE_PROPERTY     = 0b1;
    private static final int         STATE_PRESENTER    = 0b1 << 1;
    private static final int         STATE_EVENT        = 0b1 << 2;
    public static final  int         STATE_BIND_VIEW    = 0b1 << 3;
    public static final  int         STATE_ONCLICK      = 0b1 << 4;
    public static final  int         STATE_BIND_BUNDLE  = 0b1 << 5;
    private static final int         STATE_PERMISSION   = 0b1 << 6;
    private static final int         STATE_THREAD_POINT = 0b1 << 7;
    private static final int         STATE_ASPECT       = 0b1 << 8;
    private final        Project     project;
    private              MyExtension myExtension;

    public MainTransform(Project project) {
        this.project = project;
    }

    @Override
    public String getName() {
        return "QsTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }


    @Override
    public boolean isIncremental() {
        return true;
    }

    /**
     * 1.每个input都可能有两种文件类型输入，directory和jar，所以都要遍历
     * 2.每个input都需要对应的output，否则就会丢失输出
     * 3.output时的位置，要由TransformOutputProvider通过name、contentTypes、scope和Format决定；name要注意，在同一个contentTypes、scope、Format下name要唯一，
     * 因为输入的Input的name可能重复，导致覆盖同样的文件，一般可以使用文件绝对路径的hascode作为后缀来确保唯一
     * 4.TransformInvocation的referencedInputs，是只用来查看的，而不是output的，其原始内容会自动原样传入到下一个Transform的input中
     */
    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);
        myExtension = project.getExtensions().findByType(MyExtension.class);
        if (myExtension == null || !myExtension.enable) return;
        TransformHelper.enableLog(myExtension.showLog);
        boolean incremental = transformInvocation.isIncremental();
        long startTime = System.currentTimeMillis();

        Collection<TransformInput> inputs = transformInvocation.getInputs();
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();

        println("\t> QsTransform started.......incremental:" + incremental + ", input size:" + inputs.size() + ", params:" + myExtension.toString());
        try {
            appendAndroidJar();

            for (TransformInput input : inputs) {
                Collection<JarInput> jarInputs = input.getJarInputs();
                if (jarInputs != null && jarInputs.size() > 0) {
                    processJarInputs(jarInputs, outputProvider, incremental);
                }
            }

            for (TransformInput input : inputs) {
                Collection<DirectoryInput> dirInputs = input.getDirectoryInputs();
                if (dirInputs != null && dirInputs.size() > 0) {
                    processDirInputs(dirInputs, outputProvider, incremental);
                }
            }
            println("\t> QsTransform ended...... time spent:" + (System.currentTimeMillis() - startTime) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
            throw new TransformException(e);
        }
    }


    /**
     * 目录文件夹是我们的源代码和生成的R文件和BuildConfig文件等
     */
    private void processDirInputs(Collection<DirectoryInput> directoryInputs, TransformOutputProvider outputProvider, boolean incremental) throws Exception {
        HashMap<String, List<CtClass>> totalChangedMap = new HashMap<>();
        int totalChangedSize = 0;
        for (DirectoryInput dirInput : directoryInputs) {
            File inputDir = dirInput.getFile();
            String inputDirPath = inputDir.getAbsolutePath();

            File destDir = outputProvider.getContentLocation(dirInput.getName(), dirInput.getContentTypes(), dirInput.getScopes(), Format.DIRECTORY);
            String destDirPath = destDir.getAbsolutePath();

            appendDirClassPath(inputDirPath);

            ArrayList<CtClass> changedFileList = null;
            if (incremental) {
                Map<File, Status> changedFiles = dirInput.getChangedFiles();
                Set<File> fileSet = changedFiles.keySet();
                for (File f : fileSet) {
                    Status status = changedFiles.get(f);
                    if (status != Status.NOTCHANGED) {
                        String destFilePath = f.getAbsolutePath().replace(inputDirPath, destDirPath);
                        File destFile = new File(destFilePath);
                        if (destFile.exists()) FileUtils.delete(destFile);
                        if (status != Status.REMOVED) {
                            if (!f.exists()) {
                                println(f.getAbsolutePath() + " not exists, Why???");
                                continue;
                            }
                            FileUtils.copyFile(f, destFile);
                            if (changedFileList == null) {
                                changedFileList = new ArrayList<>();
                                totalChangedMap.put(destDirPath, changedFileList);
                            }
                            CtClass ctClass = createCtClass(destFilePath);
                            if (ctClass != null) {
                                totalChangedSize++;
                                changedFileList.add(ctClass);
                            }
                        }
                    }
                }
            } else {
                FileUtils.copyDirectory(inputDir, destDir);
                ArrayList<String> tempList = new ArrayList<>();
                filterOutJavaClass(destDir, tempList);
                totalChangedSize += tempList.size();

                changedFileList = new ArrayList<>();
                for (String path : tempList) {
                    CtClass ctClass = createCtClass(path);
                    if (ctClass != null) {
                        changedFileList.add(ctClass);
                    }
                }
                totalChangedMap.put(destDirPath, changedFileList);
            }
        }

        if (totalChangedSize == 0) {
            println("\t\t> transform class complete, no changed...");
            return;
        }

        int transformedCount = 0;
        for (String rootPath : totalChangedMap.keySet()) {
            List<CtClass> classList = totalChangedMap.get(rootPath);
            for (CtClass ctClass : classList) {
                boolean transformed = processJavaClassFile(rootPath, ctClass);
                if (transformed) transformedCount++;
            }
        }
        println("\t> transform count: " + transformedCount + ", total count: " + totalChangedSize);
    }

    private void filterOutJavaClass(File file, List<String> filePathList) {
        if (file.isFile() && file.getName().endsWith(".class")) {
            filePathList.add(file.getAbsolutePath());
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    filterOutJavaClass(f, filePathList);
                }
            }
        }
    }

    @Nullable private CtClass createCtClass(String filePath) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath);
            return TransformHelper.getInstance().makeClass(fis, false);
        } catch (Exception e) {
            println("can not create CtClass from filePath:" + filePath);
        } finally {
            TransformHelper.closeStream(fis);
        }
        return null;
    }

    private boolean processJavaClassFile(String rootPath, CtClass clazz) throws Exception {
        int state = 0;
        if (clazz == null) {
            return false;
        }

        CtMethod[] declaredMethods = clazz.getDeclaredMethods();
        CtField[] declaredFields = clazz.getDeclaredFields();
        if (clazz.getAnnotation(AutoProperty.class) != null) {
            PropertyTransform.transform(clazz, declaredFields);
            state |= STATE_PROPERTY;
        }
        if (PresenterTransform.transform(clazz)) {
            state |= STATE_PRESENTER;
        }
        if (EventTransform.transform(clazz, declaredMethods, rootPath)) {
            state |= STATE_EVENT;
        }

        state |= ViewBindTransform.transform(clazz, declaredMethods, declaredFields, rootPath);

        if (PermissionTransform.transform(clazz, declaredMethods, rootPath)) {
            state |= STATE_PERMISSION;
        }
        if (ThreadPointTransform.transform(clazz, declaredMethods, rootPath)) {
            state |= STATE_THREAD_POINT;
        }
        if (AspectTransform.transform(clazz, declaredMethods, rootPath)) {
            state |= STATE_ASPECT;
        }
        if (state != 0) {
            clazz.writeFile(rootPath);
        }
        showTransformInfoLog(clazz, state);
        return state != 0;
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
        }
        if ((state & STATE_ASPECT) == STATE_ASPECT) {
            if (tag) sb.append(", ");
            sb.append("@QsAspect");
        }
        println(sb.append("]").toString());
    }


    private void processJarInputs(Collection<JarInput> jarInputs, TransformOutputProvider outputProvider, boolean incremental) throws Exception {
        for (JarInput jarInput : jarInputs) {
            File inputFile = jarInput.getFile();
            File destFile = outputProvider.getContentLocation(jarInput.getName(), jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
            if (incremental) {
                switch (jarInput.getStatus()) {
                    case ADDED:
                        FileUtils.copyFile(inputFile, destFile);
                        break;
                    case REMOVED:
                        if (destFile.exists()) FileUtils.delete(destFile);
                        break;
                    case CHANGED:
                        if (destFile.exists()) FileUtils.delete(destFile);
                        FileUtils.copyFile(inputFile, destFile);
                        break;
                }
            } else {
                FileUtils.copyFile(inputFile, destFile);
            }
            checkShouldAppendJarPath(inputFile);
        }
    }

    /**
     * 添加Android jar
     */
    private void appendAndroidJar() throws NotFoundException {
        FileCollection collection = project.getExtensions().getByType(AppExtension.class).getMockableAndroidJar();
        Set<File> files = collection.getFiles();
        for (File f : files) {
            String path = f.getAbsolutePath();
            TransformHelper.getInstance().appendClassPath(path);
            println("\t> appendClassPath(Android) :" + path);

        }
    }

    private void checkShouldAppendJarPath(File inputFile) throws Exception {
        String jarPath = inputFile.getAbsolutePath();
        if (myExtension.shouldAppendClassPath(jarPath)) {
            println("\t> appendClassPath(jar) :" + jarPath);
            TransformHelper.getInstance().appendClassPath(jarPath);
        }
    }

    private void appendDirClassPath(String dirPath) throws Exception {
        println("\t> appendClassPath(class dir) :" + dirPath);
        TransformHelper.getInstance().appendClassPath(dirPath);
    }

    private void println(String text) {
        TransformHelper.println(text);
    }


}
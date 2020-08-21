package com.qsmaxmin.plugin;

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
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.qsmaxmin.annotation.properties.AutoProperty;
import com.qsmaxmin.plugin.transforms.EventTransform;
import com.qsmaxmin.plugin.transforms.PermissionTransform;
import com.qsmaxmin.plugin.transforms.PropertyTransform;
import com.qsmaxmin.plugin.transforms.ThreadPointTransform;
import com.qsmaxmin.plugin.transforms.ViewBindTransform;

import org.gradle.api.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javassist.ClassPath;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;

public class MainTransform extends Transform {
    private final Project     project;
    private       MyExtension myExtension;

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
        myExtension = (MyExtension) project.property("QsPlugin");
        if (myExtension == null || !myExtension.enable) return;

        TransformHelper.enableLog(myExtension.showLog);
        boolean incremental = transformInvocation.isIncremental();
        long t = System.currentTimeMillis();
        println("\t> QsTransform started.......incremental:" + incremental + myExtension.toString());

        Collection<TransformInput> inputs = transformInvocation.getInputs();
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();

        ArrayList<ClassPath> totalAppendList = new ArrayList<>();
        try {
            for (TransformInput input : inputs) {
                List<ClassPath> appendedJarList = processJarInputs(input.getJarInputs(), outputProvider, incremental);
                totalAppendList.addAll(appendedJarList);

                List<ClassPath> appendedDirList = processDirInputs(input.getDirectoryInputs(), outputProvider, incremental);
                totalAppendList.addAll(appendedDirList);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new TransformException(e);
        } finally {
            for (ClassPath cp : totalAppendList) {
                removeDirClassPath(cp);
            }
            TransformHelper.release();
        }
        println("\t> QsTransform ended...... use time:" + (System.currentTimeMillis() - t) + " ms");
    }

    /**
     * 目录文件夹是我们的源代码和生成的R文件和BuildConfig文件等
     */
    private List<ClassPath> processDirInputs(Collection<DirectoryInput> directoryInputs, TransformOutputProvider outputProvider, boolean incremental) throws Exception {
        ArrayList<ClassPath> appendList = new ArrayList<>();
        HashMap<String, List<String>> changedData = new HashMap<>();

        for (DirectoryInput dirInput : directoryInputs) {
            File inputDir = dirInput.getFile();
            File destDir = outputProvider.getContentLocation(inputDir.getAbsolutePath(), dirInput.getContentTypes(), dirInput.getScopes(), Format.DIRECTORY);
            ClassPath appendedClassPath = appendDirClassPath(inputDir.getAbsolutePath());
            appendList.add(appendedClassPath);

            ArrayList<String> changedFileList = null;
            if (incremental) {
                Map<File, Status> changedFiles = dirInput.getChangedFiles();
                Set<File> fileSet = changedFiles.keySet();
                for (File f : fileSet) {
                    Status status = changedFiles.get(f);
                    if (status != Status.NOTCHANGED) {
                        String destFilePath = f.getAbsolutePath().replace(inputDir.getAbsolutePath(), destDir.getAbsolutePath());
                        File destFile = new File(destFilePath);
                        if (destFile.exists()) FileUtils.delete(destFile);
                        if (status == Status.ADDED || status == Status.CHANGED) {
                            FileUtils.copyFile(f, destFile);
                            if (changedFileList == null) {
                                changedFileList = new ArrayList<>();
                                changedData.put(destDir.getAbsolutePath(), changedFileList);
                            }
                            changedFileList.add(destFilePath);
                        }
                    }

                }
            } else {
                FileUtils.copyDirectory(inputDir, destDir);
                changedFileList = new ArrayList<>();
                filterOutJavaClass(destDir, changedFileList);
                changedData.put(destDir.getAbsolutePath(), changedFileList);
            }
        }

        for (String rootPath : changedData.keySet()) {
            List<String> strings = changedData.get(rootPath);
            processJavaClassFile(rootPath, strings);
        }
        return appendList;
    }

    private void filterOutJavaClass(File file, List<String> filePathList) {
        if (file.isFile()) {
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


    private void processJavaClassFile(String rootPath, List<String> filePathList) throws Exception {
        println("\t> total changed class count: " + filePathList.size());
        int transformedCount = 0;
        for (String filePath : filePathList) {
            FileInputStream fis = new FileInputStream(filePath);
            CtClass clazz = TransformHelper.getClassPool().makeClass(fis, false);
            fis.close();

            if (clazz == null) {
                println("\t\t> class not found: " + filePath);
                continue;
            }

            if (clazz.getAnnotation(AutoProperty.class) != null) {
                transformedCount++;
                PropertyTransform.transform(clazz, rootPath, filePath);
            } else {
                CtMethod[] declaredMethods = clazz.getDeclaredMethods();
                CtField[] declaredFields = clazz.getDeclaredFields();

                boolean hasEvent = EventTransform.transform(clazz, declaredMethods, filePath);
                boolean hasViewBind = ViewBindTransform.transform(clazz, declaredMethods, declaredFields, filePath);
                boolean hasPermission = PermissionTransform.transform(clazz, declaredMethods, rootPath, filePath);
                boolean hasThreadPoint = ThreadPointTransform.transform(clazz, declaredMethods, rootPath, filePath);

                if (hasEvent || hasViewBind || hasPermission || hasThreadPoint) {
                    transformedCount++;
                    clazz.writeFile(rootPath);
                }
            }
        }
        println("\t> transformed class count: " + transformedCount);
    }

    private List<ClassPath> processJarInputs(Collection<JarInput> jarInputs, TransformOutputProvider outputProvider, boolean incremental) throws Exception {
        ArrayList<ClassPath> appendedList = new ArrayList<>();
        for (JarInput jarInput : jarInputs) {
            File inputFile = jarInput.getFile();
            File destFile = outputProvider.getContentLocation(inputFile.getAbsolutePath(), jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
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
            ClassPath classPath = checkShouldAppendJarPath(inputFile);
            if (classPath != null) appendedList.add(classPath);
        }
        return appendedList;
    }

    private ClassPath checkShouldAppendJarPath(File inputFile) throws Exception {
        String jarPath = inputFile.getAbsolutePath();
        if (shouldAppendClassPath(jarPath)) {
            println("\t> appendClassPath(jar) :" + jarPath);
            return TransformHelper.getClassPool().appendClassPath(jarPath);
        }
        return null;
    }

    private ClassPath appendDirClassPath(String dirPath) throws Exception {
        println("\t> appendClassPath(class dir) :" + dirPath);
        return TransformHelper.getClassPool().appendClassPath(dirPath);
    }

    private void println(String text) {
        TransformHelper.println(text);
    }

    private void removeDirClassPath(ClassPath classPath) {
        TransformHelper.getClassPool().removeClassPath(classPath);
    }

    public boolean shouldAppendClassPath(String classPath) {
        return myExtension.shouldAppendClassPath(classPath);
    }

}
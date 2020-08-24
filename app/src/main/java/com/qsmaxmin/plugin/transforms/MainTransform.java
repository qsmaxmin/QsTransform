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
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.qsmaxmin.annotation.properties.AutoProperty;
import com.qsmaxmin.plugin.extension.MyExtension;
import com.qsmaxmin.plugin.helper.TransformHelper;

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
        myExtension = project.getExtensions().findByType(MyExtension.class);
        if (myExtension == null || !myExtension.enable) return;

        TransformHelper.enableLog(myExtension.showLog);
        boolean incremental = transformInvocation.isIncremental();
        long t0 = System.currentTimeMillis();

        Collection<TransformInput> inputs = transformInvocation.getInputs();
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();

        println("\t> QsTransform started.......incremental:" + incremental + ", input size:" + inputs.size() + ", params:" + myExtension.toString());
        try {
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
            println("\t> QsTransform ended...... time spent:" + (System.currentTimeMillis() - t0) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
            throw new TransformException(e);
        }
    }

    /**
     * 目录文件夹是我们的源代码和生成的R文件和BuildConfig文件等
     */
    private void processDirInputs(Collection<DirectoryInput> directoryInputs, TransformOutputProvider outputProvider, boolean incremental) throws Exception {
        HashMap<String, List<String>> totalChangedMap = new HashMap<>();
        int totalChangedSize = 0;

        for (DirectoryInput dirInput : directoryInputs) {
            File inputDir = dirInput.getFile();
            File destDir = outputProvider.getContentLocation(inputDir.getAbsolutePath(), dirInput.getContentTypes(), dirInput.getScopes(), Format.DIRECTORY);
            appendDirClassPath(inputDir.getAbsolutePath());

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
                        if (status != Status.REMOVED) {
                            FileUtils.copyFile(f, destFile);
                            if (changedFileList == null) {
                                changedFileList = new ArrayList<>();
                                totalChangedMap.put(destDir.getAbsolutePath(), changedFileList);
                            }
                            totalChangedSize++;
                            changedFileList.add(destFilePath);
                        }
                    }

                }
            } else {
                FileUtils.copyDirectory(inputDir, destDir);
                changedFileList = new ArrayList<>();
                filterOutJavaClass(destDir, changedFileList);
                totalChangedSize += changedFileList.size();
                totalChangedMap.put(destDir.getAbsolutePath(), changedFileList);
            }
        }

        if (totalChangedSize == 0) {
            println("\t\t> transform class complete, no changed...");
            return;
        }

        int transformedCount = 0;
        for (String rootPath : totalChangedMap.keySet()) {
            List<String> strings = totalChangedMap.get(rootPath);
            for (String filePath : strings) {
                boolean transformed = processJavaClassFile(rootPath, filePath);
                if (transformed) transformedCount++;
            }
        }
        println("\t> transform count: " + transformedCount + ", total count: " + totalChangedSize);
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


    private boolean processJavaClassFile(String rootPath, String filePath) throws Exception {
        boolean transformed = false;
        FileInputStream fis = new FileInputStream(filePath);
        CtClass clazz = TransformHelper.getInstance().makeClass(fis, false);
        fis.close();

        if (clazz == null) {
            println("\t\t> class not found: " + filePath);
            return false;
        }

        if (clazz.getAnnotation(AutoProperty.class) != null) {
            transformed = true;
            PropertyTransform.transform(clazz, rootPath, filePath);
        } else {
            CtMethod[] declaredMethods = clazz.getDeclaredMethods();
            CtField[] declaredFields = clazz.getDeclaredFields();

            boolean hasEvent = EventTransform.transform(clazz, declaredMethods, filePath);
            boolean hasViewBind = ViewBindTransform.transform(clazz, declaredMethods, declaredFields, filePath);
            boolean hasPermission = PermissionTransform.transform(clazz, declaredMethods, rootPath, filePath);
            boolean hasThreadPoint = ThreadPointTransform.transform(clazz, declaredMethods, rootPath, filePath);

            if (hasEvent || hasViewBind || hasPermission || hasThreadPoint) {
                transformed = true;
                clazz.writeFile(rootPath);
            }
        }
        return transformed;
    }


    private void processJarInputs(Collection<JarInput> jarInputs, TransformOutputProvider outputProvider, boolean incremental) throws Exception {
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
            checkShouldAppendJarPath(inputFile, destFile);
        }
    }

    private void checkShouldAppendJarPath(File inputFile, File destFile) throws Exception {
        String jarPath = inputFile.getAbsolutePath();
        if (shouldAppendClassPath(jarPath)) {
            println("\t> appendClassPath(jar) :" + jarPath);
            TransformHelper.getInstance().appendClassPath(destFile.getAbsolutePath());
        }
    }

    private void appendDirClassPath(String dirPath) throws Exception {
        println("\t> appendClassPath(class dir) :" + dirPath);
        TransformHelper.getInstance().appendClassPath(dirPath);
    }

    private void println(String text) {
        TransformHelper.println(text);
    }

    public boolean shouldAppendClassPath(String classPath) {
        return myExtension.shouldAppendClassPath(classPath);
    }

}
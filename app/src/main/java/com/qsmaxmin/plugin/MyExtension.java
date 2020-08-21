package com.qsmaxmin.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @CreateBy administrator
 * @Date 2020/8/17 16:57
 * @Description
 */
public class MyExtension {
    public boolean enable  = true;
    public boolean showLog = true;
    List<String> includes = new ArrayList<>();

    public MyExtension() {
        includes.add(".*QsBase.*");
        includes.add(".*QsAnnotation.*");
    }

    public void include(String... filters) {
        if (filters != null && filters.length > 0) {
            includes.addAll(Arrays.asList(filters));
        }
    }

    @Override public String toString() {
        return "QsPlugin{" +
                "enable=" + enable +
                ", showLog=" + showLog +
                ", includes=" + includes +
                '}';
    }

    public boolean shouldAppendClassPath(String classPath) {
        for (String filter : includes) {
            Pattern compile = Pattern.compile(filter);
            if (compile.matcher(classPath).matches()) {
                return true;
            }
        }
        return false;
    }
}

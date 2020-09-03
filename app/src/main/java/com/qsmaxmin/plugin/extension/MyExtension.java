package com.qsmaxmin.plugin.extension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * @CreateBy administrator
 * @Date 2020/8/17 16:57
 * @Description
 */
public class MyExtension {
    public  boolean         enable        = true;
    public  boolean         showLog       = true;
    public  boolean         fullClassPath = true;
    private HashSet<String> includes      = new HashSet<>();

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
                ", fullClassPath=" + fullClassPath +
                ", includes=" + includes +
                '}';
    }

    public boolean shouldAppendClassPath(String classPath) {
        if (fullClassPath) {
            return true;
        } else {
            for (String filter : includes) {
                Pattern compile = Pattern.compile(filter);
                if (compile.matcher(classPath).matches()) {
                    return true;
                }
            }
            return false;
        }
    }
}

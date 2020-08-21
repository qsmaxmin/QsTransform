package com.qsmaxmin.plugin;

import java.util.concurrent.Callable;

/**
 * @CreateBy administrator
 * @Date 2020/8/21 14:11
 * @Description
 */
public class CustomCall implements Callable<Boolean> {

    @Override public Boolean call() throws Exception {
        return true;
    }
}

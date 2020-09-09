package com.qsmaxmin.plugin;

/**
 * @CreateBy administrator
 * @Date 2020/8/5 9:11
 * @Description
 */
public class MainSimplePlugin extends BasePlugin {
    @Override protected boolean isDebug() {
        return false;
    }

    @Override protected boolean addQsBaseRepositories() {
        return false;
    }
}

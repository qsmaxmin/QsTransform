package com.qsmaxmin.plugin;

/**
 * @CreateBy administrator
 * @Date 2020/9/9 11:47
 * @Description
 */
public class MainCompletePluginDebug extends BasePlugin {

    @Override protected boolean isDebug() {
        return true;
    }

    @Override protected boolean addQsBaseRepositories() {
        return true;
    }
}

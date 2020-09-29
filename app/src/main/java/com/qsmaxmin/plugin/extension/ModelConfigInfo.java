package com.qsmaxmin.plugin.extension;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/9/29 10:54
 * @Description
 */
public class ModelConfigInfo {
    public String qsBaseDependency;
    public long   currentTime;

    /**
     * 缓存文件只保存24小时
     */
    public boolean isTimeOut() {
        return System.currentTimeMillis() - currentTime > 24 * 3600_000;
    }
}

package com.qsmaxmin.plugin.thread;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @CreateBy qsmaxmin
 * @Date 2017/3/21 20:11
 * @Description 线程池管理类，对外提供api
 */

public class ThreadPollHelper {
    public static final String             NAME_WORK_THREAD = "WorkThreadPoll";
    private static      ThreadPollHelper   helper;
    private             ThreadPoolExecutor workThreadPoll;


    private static ThreadPollHelper getInstance() {
        if (helper == null) {
            synchronized (ThreadPollHelper.class) {
                if (helper == null) {
                    helper = new ThreadPollHelper();
                }
            }
        }
        return helper;
    }

    public static void runOnWorkThread(Runnable action) throws Exception {
        getWorkThreadPoll().execute(action);
    }


    private static ThreadPoolExecutor getWorkThreadPoll() {
        if (getInstance().workThreadPoll == null) {
            synchronized (ThreadPollHelper.class) {
                if (getInstance().workThreadPoll == null) getInstance().workThreadPoll = createWorkThreadPool();
            }
        }
        return getInstance().workThreadPoll;
    }


    public static void release() {
        if (helper != null) {
            if (helper.workThreadPoll != null) {
                helper.workThreadPoll.shutdown();
                helper.workThreadPoll = null;
            }
            helper = null;
        }
    }

    private static ThreadPoolExecutor createWorkThreadPool() {
        return new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), generateThread());
    }

    private static ThreadFactory generateThread() {
        return runnable -> {
            Thread t = new Thread(runnable, NAME_WORK_THREAD);
            if (!t.isDaemon()) t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY);
            return t;
        };
    }
}

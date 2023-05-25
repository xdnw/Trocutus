package link.locutus.util.scheduler;

import java.util.concurrent.Callable;

public abstract class CaughtRunnable implements Runnable, CaughtTask {
    @Override
    public final void run() {
        try {
            runUnsafe();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public abstract void runUnsafe() throws Exception;

    public static Runnable wrap(Runnable runnable) {
        if (runnable instanceof CaughtRunnable) return runnable;
        return new CaughtRunnable() {
            @Override
            public void runUnsafe() {
                runnable.run();;
            }
        };
    }

    public static Runnable wrap(CaughtTask task) {
        return new CaughtRunnable() {
            @Override
            public void runUnsafe() throws Exception {
                task.runUnsafe();
            }
        };
    }
    public static Runnable wrap(Callable<?> callable) {
        return new CaughtRunnable() {
            @Override
            public void runUnsafe() throws Exception {
                callable.call();
            }
        };
    }


}

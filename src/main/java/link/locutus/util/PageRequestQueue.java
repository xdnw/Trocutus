package link.locutus.util;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class PageRequestQueue {

    private final ScheduledExecutorService service;
    // field of priority queue
    private int millisecondsPerPage;
    private PriorityQueue<PageRequestTask<?>> queue;


    public PageRequestQueue(int millisecondsPerPage) {
        // ScheduledExecutorService service
        this.service = Executors.newScheduledThreadPool(1);
        this.millisecondsPerPage = millisecondsPerPage;
        this.queue = new PriorityQueue<>((o1, o2) -> Integer.compare(o2.getPriority(), o1.getPriority()));
        service.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    PageRequestQueue.this.run();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, millisecondsPerPage, millisecondsPerPage, TimeUnit.MILLISECONDS);
    }

    public void run() {
        PageRequestTask task = queue.poll();
        if (task != null) {
            Supplier supplier = task.getTask();
            task.complete(supplier.get());
        }
    }

    public <T> PageRequestTask<T> submit(Supplier<T> task, int priority) {
        PageRequestTask<T> request = new PageRequestTask<T>(task, priority);
        queue.add(request);
        return request;
    }

    public static class PageRequestTask<T> extends CompletableFuture<T> {
        private final Supplier<T> task;
        private final int priority;

        public PageRequestTask(Supplier<T> task, int priority) {
            this.task = task;
            this.priority = priority;
        }

        public Supplier<T> getTask() {
            return task;
        }

        public int getPriority() {
            return priority;
        }
    }
}

package link.locutus.util.scheduler;

public interface QuadConsumer<A, B, C, D> {
    void consume(A a, B b, C c, D d);
}

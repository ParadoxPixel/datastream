package nl.iobyte.datastream;

import nl.iobyte.datastream.interfaces.IExecutor;
import nl.iobyte.datastream.interfaces.DataStreamProvider;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataStream<T> {

    //State
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicBoolean waiting = new AtomicBoolean(false);
    private final SynchronousQueue<T> handoffQueue;
    private final ArrayDeque<T> queue;

    //Parameters
    private final DataStreamProvider<T> provider;
    private final IExecutor executor;
    private final int size, min;
    private int page;

    public DataStream(DataStreamProvider<T> provider, IExecutor executor, int size, int min) {
        if(size <= min)
            throw new IllegalArgumentException("size should be bigger than minimum size");

        handoffQueue = new SynchronousQueue<>(false);
        queue = new ArrayDeque<>();

        this.provider = provider;
        this.executor = executor;
        this.size = size;
        this.min = min;
        this.page = 0;
    }

    /**
     * Check if stream has next value
     * @return Boolean
     */
    public boolean hasNext() {
        return !done.get() || !queue.isEmpty();
    }

    /**
     * Get next value
     * @param timeout Long
     * @param timeUnit TimeUnit
     * @return T
     * @throws Exception on timeout
     */
    public T next(long timeout, TimeUnit timeUnit) throws Exception {
        if(!done.get()) {
            if (queue.size() <= min) {
                if (!loading.get()) {
                    loading.set(true);
                    executor.async(this::load);
                    return next(timeout, timeUnit);
                }
            }
        } else {
            if(queue.isEmpty())
                throw new IllegalStateException("no more data in stream");
        }

        T entry = queue.poll();
        if(entry != null)
            return entry;

        waiting.set(true);
        entry = handoffQueue.poll(
                timeout,
                timeUnit
        );
        waiting.set(false);
        if (entry == null && !done.get())
            throw new TimeoutException("next timed out while waiting on offer");

        return entry;
    }

    /**
     * Get iterator for  data stream
     * @param timeout Long
     * @param timeUnit TimeUnit
     * @return Iterator<T>
     */
    public Iterator<T> iterator(long timeout, TimeUnit timeUnit) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return DataStream.this.hasNext();
            }

            @Override
            public T next() {
                try {
                    return DataStream.this.next(timeout, timeUnit);
                } catch (Exception e) {
                    throw new IllegalStateException(e.getMessage());
                }
            }
        };
    }

    /**
     * Get iterable for  data stream
     * @param timeout Long
     * @param timeUnit TimeUnit
     * @return Iterable<T>
     */
    public Iterable<T> iterate(long timeout, TimeUnit timeUnit) {
        return () -> iterator(timeout, timeUnit);
    }

    /**
     * Load data
     */
    private void load() {
        List<T> l = provider.page(page++, size);
        if (l == null || l.isEmpty()) {
            done.set(true);
            loading.set(false);
            page--;
            return;
        }

        if(waiting.get()) {
            T entry = l.remove(0);
            queue.addAll(l);
            handoffQueue.offer(entry);
        } else {
            queue.addAll(l);
        }
        loading.set(false);
    }

}

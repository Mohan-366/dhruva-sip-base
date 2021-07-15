package com.cisco.dsb.common.executor;

import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.cisco.wx2.util.MonitoredExecutorProvider;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Map;
import java.util.concurrent.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;

public class DhruvaExecutorService extends MonitoredExecutorProvider {

  private static Logger LOG = DhruvaLoggerFactory.getLogger(ExecutorService.class);

  private final ConcurrentMap<String, ExecutorService> executorMap = new ConcurrentHashMap<>();

  private final ConcurrentMap<String, CustomScheduledThreadPoolExecutor> scheduledExecutorMap =
      new ConcurrentHashMap<>();

  private final String servername;

  public static final String Executor = "DhruvaExecutorThreadPool";

  public DhruvaExecutorService(
      final String servername,
      Environment env,
      MetricRegistry metricRegistry,
      int applicationInstanceIndex,
      boolean isEnableMonitoredExecutorServiceMetricsToInfluxFromStatsD) {
    super(
        env,
        metricRegistry,
        applicationInstanceIndex,
        isEnableMonitoredExecutorServiceMetricsToInfluxFromStatsD);
    this.servername = servername;
  }

  public void startExecutorService(final ExecutorType type, final int maxThreads) {
    String name = type.getExecutorName(this.servername);
    if (isExecutorServiceRunning(name)) {
      LOG.info("Executor service {} already running on {}", this, this.servername);
      return;
    }
    // maxThreads => minThread + 10.We dont want fixed size pool
    startExecutorService(name, maxThreads, maxThreads + 10);
  }

  /**
   * provide the thread name that you want to assign
   *
   * @param name Name of thread
   * @param maxThreads Accepts the max threads to be configured as input
   */
  public void startExecutorService(String name, int minThreads, int maxThreads) {
    ExecutorService e =
        this.executorMap.compute(
            name,
            (key, value) -> {
              if (value != null) {
                throw new RuntimeException(
                    "An executor service with the name " + key + " is already running!");
              }
              // Pick from CSB
              return this.newExecutorService(key, null, minThreads, maxThreads, 100, 60, false);
            });

    LOG.info(
        "Starting executor service name={}, corePoolSize={}, maxPoolSize={}",
        name,
        minThreads,
        maxThreads);
  }

  public void startScheduledExecutorService(final ExecutorType type, final int maxThreads) {
    String name = type.getExecutorName(this.servername);
    if (isScheduledExecutorServiceRunning(name)) {
      LOG.info("Executor service {} already running on {}", this, this.servername);
      return;
    }
    startScheduledExecutorService(name, maxThreads);
  }
  /**
   * provide the thread name that you want to assign
   *
   * @param name Name of thread
   * @param maxThreads Accepts the max threads to be configured as input
   */
  public void startScheduledExecutorService(String name, int maxThreads) {
    CustomScheduledThreadPoolExecutor e =
        this.scheduledExecutorMap.compute(
            name,
            (key, value) -> {
              if (value != null) {
                throw new RuntimeException(
                    "An executor service with the name " + key + " is already running!");
              }
              ThreadFactoryBuilder tfb = new ThreadFactoryBuilder();
              tfb.setNameFormat(name + "-%d");
              tfb.setDaemon(true);
              // return this.newScheduledExecutorService(key, null, maxThreads);
              return new CustomScheduledThreadPoolExecutor(maxThreads, tfb.build());
            });

    e.setRemoveOnCancelPolicy(true);

    LOG.info(
        "Starting Scheduled executor service name={}, corePoolSize={}, maxPoolSize={}",
        name,
        maxThreads,
        maxThreads);
  }

  public boolean isExecutorServiceRunning(String name) {
    return this.executorMap.containsKey(name);
  }

  public boolean isScheduledExecutorServiceRunning(String name) {
    return this.scheduledExecutorMap.containsKey(name);
  }

  public boolean isExecutorServiceRunning(ExecutorType name) {
    return isExecutorServiceRunning(name.getExecutorName(servername));
  }

  ScheduledThreadPoolExecutor getScheduledExecutor(final ExecutorType type) {
    return getScheduledExecutor(type.getExecutorName(this.servername));
  }

  private ScheduledThreadPoolExecutor getScheduledExecutor(String executorName) {
    return this.scheduledExecutorMap.get(executorName);
  }

  public ExecutorService getExecutorThreadPool(final ExecutorType type) {
    return getExecutor(type);
  }

  public ScheduledThreadPoolExecutor getScheduledExecutorThreadPool(final ExecutorType type) {
    return getScheduledExecutor(type);
  }

  /**
   * Use this API to fetch the executor based on the type
   *
   * @param type Executor type for which the executor thread pool was created
   * @return Executor object that was already created
   */
  ExecutorService getExecutor(final ExecutorType type) {
    return getExecutor(type.getExecutorName(this.servername));
  }

  ExecutorService getExecutor(String name) {
    return this.executorMap.get(name);
  }

  static class CustomThreadPoolExecutor extends ThreadPoolExecutor {

    private ConcurrentMap<Thread, Runnable> running = Maps.newConcurrentMap();

    public CustomThreadPoolExecutor(
        int corePoolSize,
        int maximumPoolSize,
        long keepAliveTime,
        TimeUnit unit,
        BlockingQueue<Runnable> workQueue) {
      super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    public void execute(Runnable command) {
      super.execute(wrapWithMdcContext(command));
    }

    public static Runnable wrapWithMdcContext(Runnable task) {
      // save the current MDC context
      Map<String, String> contextMap = LOG.getMDCMap();
      return () -> {
        setMDCContext(contextMap);
        try {
          task.run();
        } finally {
          LOG.clearMDC();
        }
      };
    }

    public static <T> Callable<T> wrapWithMdcContext(Callable<T> task) {
      // save the current MDC context
      Map<String, String> contextMap = LOG.getMDCMap();
      return () -> {
        setMDCContext(contextMap);
        try {
          return task.call();
        } finally {
          // once the task is complete, clear MDC
          LOG.clearMDC();
        }
      };
    }

    public static void setMDCContext(Map<String, String> contextMap) {
      LOG.clearMDC();
      if (contextMap != null) {
        LOG.setMDC(contextMap);
      }
    }

    /**
     * Middleware for keeping track of tasks Can customize in future based on needs
     *
     * @param r
     * @param t
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      super.afterExecute(r, t);
      running.remove(Thread.currentThread());
    }

    /**
     * Pre execution call
     *
     * @param t
     * @param r
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
      Runnable oldPut = running.put(t, r);
      assert oldPut == null : "inconsistency for thread " + t;
      super.beforeExecute(t, r);
    }

    /**
     * Fetch the map holding the runnable job and associated thread
     *
     * @return
     */
    public ConcurrentMap<Thread, Runnable> getRunningTasks() {
      return running;
    }
  }

  static class CustomScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    private ConcurrentMap<Thread, Runnable> running = Maps.newConcurrentMap();

    static class CustomScheduledTask<V> implements RunnableScheduledFuture<V> {

      Runnable runnable;
      Callable<V> callable;
      RunnableScheduledFuture<V> task;
      Map<String, String> mdcMap;

      public CustomScheduledTask(Runnable runnable, RunnableScheduledFuture<V> task) {
        this.runnable = runnable;
        this.task = task;
      }

      public CustomScheduledTask(Callable<V> callable, RunnableScheduledFuture<V> task) {
        this.callable = callable;
        this.task = task;
      }

      public void setMdcMap(Map<String, String> mdcMap) {
        this.mdcMap = mdcMap;
      }

      /**
       * Returns {@code true} if this task is periodic. A periodic task may re-run according to some
       * schedule. A non-periodic task can be run only once.
       *
       * @return {@code true} if this task is periodic
       */
      @Override
      public boolean isPeriodic() {
        return task.isPeriodic();
      }

      /**
       * Returns the remaining delay associated with this object, in the given time unit.
       *
       * @param unit the time unit
       * @return the remaining delay; zero or negative values indicate that the delay has already
       *     elapsed
       */
      @Override
      public long getDelay(@NotNull TimeUnit unit) {
        return task.getDelay(unit);
      }

      /**
       * Compares this object with the specified object for order. Returns a negative integer, zero,
       * or a positive integer as this object is less than, equal to, or greater than the specified
       * object.
       *
       * <p>The implementor must ensure <tt>sgn(x.compareTo(y)) == -sgn(y.compareTo(x))</tt> for all
       * <tt>x</tt> and <tt>y</tt>. (This implies that <tt>x.compareTo(y)</tt> must throw an
       * exception iff <tt>y.compareTo(x)</tt> throws an exception.)
       *
       * <p>The implementor must also ensure that the relation is transitive:
       * <tt>(x.compareTo(y)&gt;0 &amp;&amp; y.compareTo(z)&gt;0)</tt> implies
       * <tt>x.compareTo(z)&gt;0</tt>.
       *
       * <p>Finally, the implementor must ensure that <tt>x.compareTo(y)==0</tt> implies that
       * <tt>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</tt>, for all <tt>z</tt>.
       *
       * <p>It is strongly recommended, but <i>not</i> strictly required that
       * <tt>(x.compareTo(y)==0) == (x.equals(y))</tt>. Generally speaking, any class that
       * implements the <tt>Comparable</tt> interface and violates this condition should clearly
       * indicate this fact. The recommended language is "Note: this class has a natural ordering
       * that is inconsistent with equals."
       *
       * <p>In the foregoing description, the notation <tt>sgn(</tt><i>expression</i><tt>)</tt>
       * designates the mathematical <i>signum</i> function, which is defined to return one of
       * <tt>-1</tt>, <tt>0</tt>, or <tt>1</tt> according to whether the value of <i>expression</i>
       * is negative, zero or positive.
       *
       * @param o the object to be compared.
       * @return a negative integer, zero, or a positive integer as this object is less than, equal
       *     to, or greater than the specified object.
       * @throws NullPointerException if the specified object is null
       * @throws ClassCastException if the specified object's type prevents it from being compared
       *     to this object.
       */
      @Override
      public int compareTo(@NotNull Delayed o) {
        return task.compareTo(o);
      }

      /** Sets this Future to the result of its computation unless it has been cancelled. */
      @Override
      public void run() {
        task.run();
      }

      /**
       * Attempts to cancel execution of this task. This attempt will fail if the task has already
       * completed, has already been cancelled, or could not be cancelled for some other reason. If
       * successful, and this task has not started when {@code cancel} is called, this task should
       * never run. If the task has already started, then the {@code mayInterruptIfRunning}
       * parameter determines whether the thread executing this task should be interrupted in an
       * attempt to stop the task.
       *
       * <p>After this method returns, subsequent calls to {@link #isDone} will always return {@code
       * true}. Subsequent calls to {@link #isCancelled} will always return {@code true} if this
       * method returned {@code true}.
       *
       * @param mayInterruptIfRunning {@code true} if the thread executing this task should be
       *     interrupted; otherwise, in-progress tasks are allowed to complete
       * @return {@code false} if the task could not be cancelled, typically because it has already
       *     completed normally; {@code true} otherwise
       */
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return task.cancel(mayInterruptIfRunning);
      }

      /**
       * Returns {@code true} if this task was cancelled before it completed normally.
       *
       * @return {@code true} if this task was cancelled before it completed
       */
      @Override
      public boolean isCancelled() {
        return task.isCancelled();
      }

      /**
       * Returns {@code true} if this task completed.
       *
       * <p>Completion may be due to normal termination, an exception, or cancellation -- in all of
       * these cases, this method will return {@code true}.
       *
       * @return {@code true} if this task completed
       */
      @Override
      public boolean isDone() {
        return task.isDone();
      }

      /**
       * Waits if necessary for the computation to complete, and then retrieves its result.
       *
       * @return the computed result
       * @throws CancellationException if the computation was cancelled
       * @throws ExecutionException if the computation threw an exception
       * @throws InterruptedException if the current thread was interrupted while waiting
       */
      @Override
      public V get() throws InterruptedException, ExecutionException {
        return task.get();
      }

      /**
       * Waits if necessary for at most the given time for the computation to complete, and then
       * retrieves its result, if available.
       *
       * @param timeout the maximum time to wait
       * @param unit the time unit of the timeout argument
       * @return the computed result
       * @throws CancellationException if the computation was cancelled
       * @throws ExecutionException if the computation threw an exception
       * @throws InterruptedException if the current thread was interrupted while waiting
       * @throws TimeoutException if the wait timed out
       */
      @Override
      public V get(long timeout, @NotNull TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        return task.get(timeout, unit);
      }
    }

    public CustomScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
      super(corePoolSize, threadFactory);
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        Runnable r, RunnableScheduledFuture<V> task) {
      CustomScheduledTask customScheduledTask = new CustomScheduledTask<V>(r, task);
      customScheduledTask.mdcMap = LOG.getMDCMap();
      return customScheduledTask;
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        Callable<V> c, RunnableScheduledFuture<V> task) {
      CustomScheduledTask customScheduledTask = new CustomScheduledTask<V>(c, task);
      customScheduledTask.mdcMap = LOG.getMDCMap();
      return customScheduledTask;
    }

    public static void setMDCContext(Map<String, String> contextMap) {
      LOG.clearMDC();
      if (contextMap != null) {
        LOG.setMDC(contextMap);
      }
    }

    /**
     * Fetch the map holding the runnable job and associated thread
     *
     * @return
     */
    public ConcurrentMap<Thread, Runnable> getRunningTasks() {
      return running;
    }

    /**
     * Middleware for keeping track of tasks Can customize in future based on needs
     *
     * @param r
     * @param t
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      super.afterExecute(r, t);
      running.remove(Thread.currentThread());
      if (r
          instanceof DhruvaExecutorService.CustomScheduledThreadPoolExecutor.CustomScheduledTask) {
        LOG.clearMDC();
      }
    }

    /**
     * Pre execution call
     *
     * @param t
     * @param r
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
      Runnable oldPut = running.put(t, r);
      assert oldPut == null : "inconsistency for thread " + t;
      super.beforeExecute(t, r);
      if (r instanceof CustomScheduledThreadPoolExecutor.CustomScheduledTask) {
        CustomScheduledTask customScheduledTask = (CustomScheduledTask) r;
        Map<String, String> mdcMap = customScheduledTask.mdcMap;

        // Adding Task Scheduled Time in seconds to MDC
        // DSB TODO, ScheduledTaskDelay is from caffienne stack which is not required now in DSB
        //        if (customScheduledTask.runnable instanceof ScheduledTaskDelay) {
        //          mdcMap.put(
        //              "taskScheduledDelayInSeconds",
        //              Long.toString(
        //                  ((ScheduledTaskDelay) customScheduledTask.runnable)
        //                      .getTaskDelay(TimeUnit.SECONDS)));
        //        }
        setMDCContext(mdcMap);
      }
    }
  }

  @Override
  public ThreadPoolExecutor getThreadPoolExecutor(
      int min, int max, int keepalive, BlockingQueue<Runnable> queue) {
    return new DhruvaExecutorService.CustomThreadPoolExecutor(
        min, max, (long) keepalive, TimeUnit.SECONDS, queue);
  }
}

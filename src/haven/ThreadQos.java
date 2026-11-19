package haven;

import java.lang.reflect.Method;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/** Asks macOS to run frame threads on performance cores. A no-op everywhere else, and when the
 * native helper is absent. Disable with -Dhearthborn.qos=false. */
public final class ThreadQos {
    private static final Method SET = method();
    private static volatile int applied;
    private ThreadQos() {}
    private static Method method() {
	if(!System.getProperty("os.name", "").toLowerCase().contains("mac") || "false".equals(System.getProperty("hearthborn.qos")))
	    return(null);
	try {
	    return(Class.forName("haven.ffi.misc.DarwinQos").getMethod("set", int.class));
	} catch(Throwable e) {
	    return(null);
	}
    }
    private static boolean set(int qos) {
	if(SET == null)
	    return(false);
	try {
	    return(Boolean.TRUE.equals(SET.invoke(null, qos)));
	} catch(Throwable e) {
	    return(false);
	}
    }
    /** The calling thread drives frames or waits for the thread that does. */
    public static void interactive() {
	if(set(0x21))
	    applied++;
    }
    public static int applied() {return(applied);}
    /** Workers of the common pool run the parallel object ticks that the frame thread waits on. */
    public static final class Factory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
	public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
	    ForkJoinWorkerThread th = new ForkJoinWorkerThread(pool) {
		    protected void onStart() {
			super.onStart();
			interactive();
		    }
		};
	    th.setDaemon(true);
	    return(th);
	}
    }
    /** Must run before anything touches the common pool. */
    public static void install() {
	if(SET != null && System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory") == null)
	    System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", Factory.class.getName());
    }
}

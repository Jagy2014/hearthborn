package haven.ffi.misc;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/** Quality-of-service class of the calling thread on macOS. Apple Silicon places threads on
 * performance or efficiency cores by this class, so the frame thread must not run as a plain
 * default-class thread beside busy background processes. */
public final class DarwinQos {
    public static final int USER_INTERACTIVE = 0x21, USER_INITIATED = 0x19, DEFAULT = 0x15, UTILITY = 0x11, BACKGROUND = 0x09;
    private static final MethodHandle SET = lookup();
    private DarwinQos() {}
    private static MethodHandle lookup() {
	try {
	    Linker linker = Linker.nativeLinker();
	    MemorySegment symbol = linker.defaultLookup().find("pthread_set_qos_class_self_np").orElse(null);
	    if(symbol == null)
		return(null);
	    return(linker.downcallHandle(symbol, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)));
	} catch(Throwable e) {
	    return(null);
	}
    }
    /** Returns whether the system accepted the class; false off macOS. */
    public static boolean set(int qos) {
	if(SET == null)
	    return(false);
	try {
	    return(((int)SET.invokeExact(qos, 0)) == 0);
	} catch(Throwable e) {
	    return(false);
	}
    }
}

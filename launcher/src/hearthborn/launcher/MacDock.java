package hearthborn.launcher;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * While the game runs the launcher has no window, yet macOS keeps its icon in the Dock next to the game's own, and
 * clicking it does nothing. This turns the launcher into a background application for that time and back into a normal
 * one when its window has to come back (a failed start). It does nothing on other systems and never fails the launcher.
 */
final class MacDock {
    private static final int REGULAR = 0, ACCESSORY = 1;
    private MacDock() {}
    static void hide() { policy(ACCESSORY); }
    static void show() { policy(REGULAR); }
    private static void policy(int value) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return;
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global());
            MethodHandle getClass = linker.downcallHandle(objc.find("objc_getClass").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle selector = linker.downcallHandle(objc.find("sel_registerName").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment send = objc.find("objc_msgSend").orElseThrow();
            MethodHandle sendObject = linker.downcallHandle(send, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle sendPolicy = linker.downcallHandle(send, FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment application = (MemorySegment) sendObject.invoke(
                        (MemorySegment) getClass.invoke(arena.allocateFrom("NSApplication")),
                        (MemorySegment) selector.invoke(arena.allocateFrom("sharedApplication")));
                sendPolicy.invoke(application, (MemorySegment) selector.invoke(arena.allocateFrom("setActivationPolicy:")), (long) value);
            }
        } catch (Throwable ignored) { /* the icon stays; nothing else depends on it */ }
    }
}

package net.virtualvoid.io.uring;

import com.sun.jna.Native;

public class LibC2 {
    static {
        Native.register("c");
    }
    public static native int syscall(int number, int fd, int toSubmit, int toComplete, int flags, long other);
}

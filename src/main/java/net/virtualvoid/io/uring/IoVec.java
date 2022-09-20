package net.virtualvoid.io.uring;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/*
struct iovec {
   void  *iov_base;    /* Starting address
   size_t iov_len;     /* Number of bytes to transfer
};
 */
@Structure.FieldOrder({"iov_base", "iov_len"})
public class IoVec extends Structure {
    public static class ByReference extends IoVec implements Structure.ByReference { }

    public Pointer iov_base;
    public long iov_len;
}

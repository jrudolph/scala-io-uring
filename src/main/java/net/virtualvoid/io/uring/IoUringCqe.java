package net.virtualvoid.io.uring;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/*
struct io_uring_cqe {
        __u64   user_data;      /* sqe->data submission passed back
        __s32   res;            /* result code for this event
        __u32   flags;

        /*
         * If the ring is initialized with IORING_SETUP_CQE32, then this field
         * contains 16-bytes of padding, doubling the size of the CQE.

        __u64 big_cqe[];
        };
}
 */
@Structure.FieldOrder({"user_data", "res", "flags"})
public class IoUringCqe extends Structure {
    public long user_data;
    public int res;
    public int flags;

    public IoUringCqe(Pointer p) { super(p); read(); }
}

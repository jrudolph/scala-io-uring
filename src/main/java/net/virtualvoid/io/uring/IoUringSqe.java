package net.virtualvoid.io.uring;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/*
struct io_uring_sqe {
__u8 opcode;
__u8 flags;
__u16 ioprio;
__s32 fd;
__u64 off;
__u64 addr;
__u32 len;
union {
__kernel_rwf_t rw_flags;
__u32 fsync_flags;
__u16 poll_events;
__u32 sync_range_flags;
__u32 msg_flags;
};
__u64 user_data;
union {
__u16 buf_index;
__u64 __pad2[3];
};
};
 */
@Structure.FieldOrder({"opcode", "flags", "ioprio", "fd", "off", "addr", "len", "op_flags", "user_data", "bufAndPersonality", "spliceOrFileIndex", "pad1", "pad2"})
public class IoUringSqe extends Structure {
    public static class ByReference extends IoUringSqe implements Structure.ByReference {
        public ByReference(Pointer p) { super(p); }
    }

    public byte opcode, flags;
    public short ioprio;
    public int fd;
    public long off;
    public Pointer addr;
    public int len;
    public int op_flags;
    public long user_data;
    public int bufAndPersonality;
    public int spliceOrFileIndex;
    public long pad1;
    public long pad2;

    public IoUringSqe() { super(); }
    public IoUringSqe(Pointer p) {
        super(p);
        System.out.println("sqe p: "+p);
        System.out.println("sqep: "+getPointer());
        read();
    }
}

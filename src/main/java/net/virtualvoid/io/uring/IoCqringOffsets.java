package net.virtualvoid.io.uring;

import com.sun.jna.Structure;

/*
struct io_cqring_offsets {
    __u32 head;
    __u32 tail;
    __u32 ring_mask;
    __u32 ring_entries;
    __u32 overflow;
    __u32 cqes;
    __u32 flags;
    __u32 resv[3];
};
 */
@Structure.FieldOrder({"head", "tail", "ring_mask", "ring_entries", "overflow", "cqes", "flags", "resv1", "resv2", "resv3"})
public class IoCqringOffsets extends Structure {
    public int head, tail, ring_mask, ring_entries, overflow, cqes, flags, resv1, resv2, resv3;
}

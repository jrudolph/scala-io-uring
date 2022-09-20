package net.virtualvoid.io.uring;

import com.sun.jna.Structure;

/*
 *  struct io_sqring_offsets {
 __u32 head;
 __u32 tail;
 __u32 ring_mask;
 __u32 ring_entries;
 __u32 flags;
 __u32 dropped;
 __u32 array;
 __u32 resv1;
 __u64 resv2;
};
 */
@Structure.FieldOrder({"head", "tail", "ring_mask", "ring_entries", "flags", "dropped", "array", "resv1", "resv2_h", "resv2_l"})
public class IoSqringOffsets extends Structure  {
 public int head, tail, ring_mask, ring_entries, flags, dropped, array, resv1, resv2_h, resv2_l;
}

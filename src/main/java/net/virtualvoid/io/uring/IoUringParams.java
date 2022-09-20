package net.virtualvoid.io.uring;

import com.sun.jna.Structure;

/**
 * struct io_uring_params {
 * __u32 sq_entries;
 * __u32 cq_entries;
 * __u32 flags;
 * __u32 sq_thread_cpu;
 * __u32 sq_thread_idle;
 * __u32 resv[5];
 * struct io_sqring_offsets sq_off;
 * struct io_cqring_offsets cq_off;
 * };
 */
@Structure.FieldOrder({"sq_entries", "cq_entries", "flags", "sq_thread_cpu", "sq_thread_idle", "resv1", "resv2", "resv3", "resv4", "resv5", "sq_off", "cq_off"})
public class IoUringParams extends Structure {
    public static class ByReference extends IoUringParams implements Structure.ByReference { }

    public int sq_entries, cq_entries, flags, sq_thread_cpu, sq_thread_idle, resv1, resv2, resv3, resv4, resv5;
    public IoSqringOffsets sq_off;
    public IoCqringOffsets cq_off;
}

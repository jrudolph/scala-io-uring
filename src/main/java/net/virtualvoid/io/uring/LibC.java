package net.virtualvoid.io.uring;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

public interface LibC extends Library {
  int SYSCALL_IO_URING_SETUP = 425;
  int SYSCALL_IO_URING_ENTER = 426;
  int SYSCALL_IO_URING_REGISTER = 427;

  long IORING_OFF_SQ_RING = 0;
  long IORING_OFF_CQ_RING = 0x8000000;
  long IORING_OFF_SQES = 0x10000000;

  int PROT_READ = 0x1;
  int PROT_WRITE = 0x2;
  int PROT_EXEC = 0x4;
  int PROT_NONE = 0x0;

  int MAP_SHARED = 0x01;
  int MAP_POPULATE = 0x08000;

  int syscall(int number, Object... args);
  Pointer mmap(Pointer addr, long length, int prot, int flags, int fd, long offset);
  long readv(int fd, Structure.ByReference iov, int iovcnt);
}

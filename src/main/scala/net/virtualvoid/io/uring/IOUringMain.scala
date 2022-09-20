package net.virtualvoid.io.uring

import com.sun.jna.Structure.FieldOrder
import com.sun.jna.{Native, Pointer, Structure}
import sun.misc.Unsafe

import java.io.{FileDescriptor, RandomAccessFile}
import java.lang.invoke.MethodHandles
import java.nio.ByteOrder

object IOUringMain extends App {
  val fdOf: RandomAccessFile => Int = {
    val fdField = classOf[RandomAccessFile].getDeclaredField("fd")
    fdField.setAccessible(true)
    val fdFdField = classOf[FileDescriptor].getDeclaredField("fd")
    fdFdField.setAccessible(true)

    { raf =>
      val fd = fdField.get(raf)
      fdFdField.get(fd).asInstanceOf[Int]
    }
  }

  val libC = Native.load("c", classOf[LibC])
  val params = new IoUringParams
  val uringFd = libC.syscall(LibC.SYSCALL_IO_URING_SETUP, 32, params)
  println(params)

  /*
struct app_sq_ring sqring;
void *ptr;
ptr = mmap(NULL, p→sq_off.array + p→sq_entries * sizeof(__u32),
PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE,
ring_fd, IORING_OFF_SQ_RING);
sring→head = ptr + p→sq_off.head;
sring→tail = ptr + p→sq_off.tail;
sring→ring_mask = ptr + p→sq_off.ring_mask;
sring→ring_entries = ptr + p→sq_off.ring_entries;
sring→flags = ptr + p→sq_off.flags;
sring→dropped = ptr + p→sq_off.dropped;
sring→array = ptr + p→sq_off.array;
return sring;
 */
  println(s"uring_fd: $uringFd")
  val length = params.sq_off.array + params.sq_entries * 4
  //val length = 4096
  //println(s"length: $length")
  val sqRingPointer = libC.mmap(new Pointer(0), length, LibC.PROT_READ | LibC.PROT_WRITE, LibC.MAP_SHARED | LibC.MAP_POPULATE, uringFd, LibC.IORING_OFF_SQ_RING)
  val cqRingPointer = libC.mmap(new Pointer(0), params.cq_off.cqes + params.cq_entries * 16 /* sizeOf(IoUringCqe) */ , LibC.PROT_READ | LibC.PROT_WRITE, LibC.MAP_SHARED | LibC.MAP_POPULATE, uringFd, LibC.IORING_OFF_CQ_RING)
  val sqePointer = libC.mmap(new Pointer(0), params.sq_entries * 64 /* sizeOf(IoUsingSqe) */ , LibC.PROT_READ | LibC.PROT_WRITE, LibC.MAP_SHARED | LibC.MAP_POPULATE, uringFd, LibC.IORING_OFF_SQES)
  println(s"mmapped addr sqRingPointer: $sqRingPointer")
  println(s"mmapped addr sqePointer: $sqePointer")
  println(s"mmapped addr cqePointer: $cqRingPointer")
  println(Native.getLastError)

  println(sqRingPointer.getInt(params.sq_off.head), sqRingPointer.getInt(params.sq_off.tail), sqRingPointer.getInt(params.sq_off.ring_mask), sqRingPointer.getInt(params.sq_off.ring_entries), sqRingPointer.getInt(params.sq_off.flags))

  /*
  sqe→opcode = IORING_OP_READV;
 sqe→fd = fd;
 sqe→off = 0;
 sqe→addr = &iovec;
 sqe→len = 1;
 sqe→user_data = some_value;
 write_barrier();
/* ensure previous writes are seen before tail write */
 sqring→tail = sqring→tail + 1;
 write_barrier();
/* ensure tail write is seen */
   */
  //println(sqHeadPointer.getInt(0), sqTailPointer.getInt(0))

  val IORING_OP_READV = 1: Byte

  val myFile = new RandomAccessFile("build.sbt", "r")
  val fd = fdOf(myFile)
  println(fd)

  /*
struct io_uring_sqe *sqe;
unsigned tail, index;
tail = sqring→tail;
index = tail & (*sqring→ring_mask);
sqe = &sqring→sqes[index];/* this call fills in the sqe entries for this IO */
init_io(sqe);/* fill the sqe index into the SQ ring array */
sqring→array[index] = index;
tail++;
write_barrier();
sqring→tail = tail;
write_barrier();
   */
  @volatile var x = 0

  // FIXME: check against overflow
  val curTail = sqRingPointer.getInt(params.sq_off.tail)
  val index = curTail & sqRingPointer.getInt(params.sq_off.ring_mask)

  val sqeIndex = 0
  val sqe = new IoUringSqe.ByReference(sqePointer.share(64 * sqeIndex)) // sqe = &sqring→sqes[index]
  sqe.opcode = IORING_OP_READV
  sqe.flags = 0
  sqe.fd = fd
  sqe.off = 0
  val iovec = new IoVec.ByReference
  val resPointer = new Pointer(Native.malloc(100))
  println(resPointer.getByteArray(0, 100).toSeq)
  iovec.iov_base = resPointer
  iovec.iov_len = 100
  iovec.write()
  sqe.addr = iovec.getPointer
  sqe.len = 1
  sqe.user_data = 0xdeadbeef
  sqe.write()

  MethodHandles.byteBufferViewVarHandle(classOf[Array[Long]], ByteOrder.LITTLE_ENDIAN).

  val sqe2 = new IoUringSqe.ByReference(sqePointer.share(64 * sqeIndex)) // sqe = &sqring→sqes[index]
  println(s"sqe: $sqe")
  println(s"sqe2: $sqe2")

  sqRingPointer.setInt(params.sq_off.array + 4 * index, sqeIndex) // sqring→array[index] = index;
  // write_barrier()
  x = 12 // emulate write_barrier?
  sqRingPointer.setInt(params.sq_off.tail, curTail + 1)
  x = 14 // emulate write_barrier?
  // write_barrier()

  val IORING_ENTER_GETEVENTS = 1

  // check cqes before call
  println(s"head: ${cqRingPointer.getInt(params.cq_off.head)} tail: ${cqRingPointer.getInt(params.cq_off.tail)}")

  val res = libC.syscall(LibC.SYSCALL_IO_URING_ENTER, uringFd, 1, 1, 1, 0, 0)
  println(res)

  val cqeP = new IoUringCqe(cqRingPointer.share(params.cq_off.cqes))
  val cqes = cqeP.toArray(params.cq_entries).asInstanceOf[Array[IoUringCqe]]
  println(x)
  println(s"head: ${cqRingPointer.getInt(params.cq_off.head)} tail: ${cqRingPointer.getInt(params.cq_off.tail)}")
  println(cqes(0))
  println(new String(resPointer.getByteArray(0, cqes(0).res)))

  //val readvres = libC.readv(fd, iovec, 1)
  //println(readvres)
  //println(resPointer.getByteArray(0, 100).toSeq)

}

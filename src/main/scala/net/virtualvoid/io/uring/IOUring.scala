package net.virtualvoid.io.uring

import com.sun.jna.{ Native, Pointer }
import net.virtualvoid.io.uring.Utils.fdOf

import java.io.RandomAccessFile
import java.nio.ByteBuffer

trait IOUring {
  def submitGlobal(op: Op): Unit
  def submitGlobalWithUserData(op: Op, userData: Long): Unit
  def context: IOContext
  def loop(): Unit
}

object IOUring {
  val libC = Native.load("c", classOf[LibC])
  //def debug(str: String): Unit = println(str)
  def debug(str: => String): Unit = {}

  val IORING_OP_NOP = 0: Byte
  val IORING_OP_READV = 1: Byte
  val IORING_OP_ACCEPT = 13: Byte
  val IORING_OP_READ = 22: Byte
  val IORING_OP_WRITE = 23: Byte
  val IORING_OP_PROVIDE_BUFFERS = 31: Byte

  val IOSQE_IO_DRAIN = 2: Byte
  val IOSQE_IO_LINK = 4: Byte
  val IOSQE_ASYNC = 16: Byte
  val IOSQE_BUFFER_SELECT = 32: Byte

  val IORING_ENTER_GETEVENTS = 1

  val IORING_CQE_BUFFER_SHIFT = 16

  val IORING_CQE_F_BUFFER = 1

  def apply(sizeOfRing: Int, perBufferSize: Int): IOUring = new IOUring {
    val params = new IoUringParams
    val uringFd = libC.syscall(LibC.SYSCALL_IO_URING_SETUP, sizeOfRing, params)
    println(params)

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

    val sqRingPointerByteBuffer = sqRingPointer.getByteBuffer(0, length)
    val cqRingPointerByteBuffer = cqRingPointer.getByteBuffer(0, params.cq_off.cqes + params.cq_entries * 16)

    val sqes = sqePointer.getByteBuffer(0, 64 * params.sq_entries)
    def submitGlobal(op: Op): Unit = {
      require(op.userData != 0L)
      // FIXME: check against overflow
      val curTail = sqTail()
      val index = curTail & sqMask
      sqes.clear()
      sqes.position(64 * index)
      op.prepareSQE(sqes)
      sqRingPointerByteBuffer.putInt(params.sq_off.array + 4 * index, index)
      // FIXME: write barrier needed?
      sqRingPointerByteBuffer.putInt(params.sq_off.tail, curTail + 1)
      // FIXME: write barrier needed?
    }
    def submitGlobalWithUserData(op: Op, userData: Long): Unit = {
      //require(op.userData != 0L)
      // FIXME: check against overflow
      val curTail = sqTail()
      val index = curTail & sqMask
      sqes.clear()
      sqes.position(64 * index)
      op.prepareSQE(sqes)
      sqes.putLong(64 * index + 32, userData)
      sqRingPointerByteBuffer.putInt(params.sq_off.array + 4 * index, index)
      // FIXME: write barrier needed?
      sqRingPointerByteBuffer.putInt(params.sq_off.tail, curTail + 1)
      // FIXME: write barrier needed?
    }
    val specialTag = -1L
    val numBuffers = 500
    val buffers = Native.malloc(numBuffers * perBufferSize)
    val buffersPointer = new Pointer(buffers)
    submitGlobal(ProvideBuffersOp(0, numBuffers, perBufferSize, buffers, 0x1234, specialTag))
    val EmptyByteBuffer = ByteBuffer.allocate(0)
    val TheWriteBuffer = Native.malloc(1000)
    val TheWriteBufferP = new Pointer(TheWriteBuffer)
    val TheWriteBufferByteBuffer = TheWriteBufferP.getByteBuffer(0, 1000)

    val buffersByteBuffer = buffersPointer.getByteBuffer(0, numBuffers * perBufferSize)

    def sqHead() = sqRingPointerByteBuffer.getInt(params.sq_off.head)
    def sqTail() = sqRingPointerByteBuffer.getInt(params.sq_off.tail)
    lazy val sqMask = sqRingPointerByteBuffer.getInt(params.sq_off.ring_mask)

    def cqHead() = cqRingPointerByteBuffer.getInt(params.cq_off.head)
    def cqTail() = cqRingPointerByteBuffer.getInt(params.cq_off.tail)
    val cqMask = cqRingPointerByteBuffer.getInt(params.cq_off.ring_mask)

    val context = new IOContext {
      val outstandingEntries = new scala.collection.mutable.LongMap[(Int, Int) => Unit](1024)
      private var nextId = 1L
      val theBufferGroup = 0x1234: Short
      def resultBuffer(res: Int, flags: Int): ByteBuffer = {
        val bufId = flags >> IORING_CQE_BUFFER_SHIFT
        //require(bufId == remainingBuffers - 1, s"Didn't get last buffer, bufId: $bufId, expected: ${remainingBuffers - 1}")
        //remainingBuffers -= 1
        //println(s"rem: $remainingBuffers")
        //buffersPointer.getByteBuffer(bufId * perBuffer, res)
        val off = bufId * perBufferSize
        buffersByteBuffer.clear().position(off).limit(off + res)
        buffersByteBuffer
      }

      private def submit(op: Op)(completion: (Int, Int) => Unit): Unit = {
        val id = nextId
        nextId += 1
        debug(s"Registering $id for op ${op.getClass}")
        outstandingEntries.put(id, completion)
        submitGlobalWithUserData(op, id | (op.opId.toLong << 32))
      }
      //def handle(cqe: IoUringCqe): Unit = handle(cqe.user_data, cqe.res, cqe.flags)
      def handle(userData: Long, res: Int, flags: Int): Unit = if (userData != specialTag) {
        val id = userData & 0xffffffffL
        val op = userData >> 32
        import scala.collection.JavaConverters._
        if (!outstandingEntries.contains(id)) println(s"Missing handler for userData: $id existing: ${outstandingEntries.keys}")
        else {
          val handler = outstandingEntries.remove(id).get
          debug(s"Looking for ${id} found: $handler")
          op match {
            case IORING_OP_READ   => handler(res, flags)
            case IORING_OP_WRITE  => handler(res, flags)
            case IORING_OP_ACCEPT => handler(res, flags)
            case IORING_OP_NOP    => handler(res, flags)
          }
        }
      }

      override def spawn(f: => Unit): Unit =
        submit(NopOp(0, 0))((_, _) => f)

      override def accept(fd: Int)(handleSocket: Int => Unit): Unit =
        submit(AcceptOp(IOSQE_ASYNC, fd, 0, 0, 0))((res, _) => handleSocket(res))

      override def read(fd: Int, offset: Long, length: Int)(handleRead: ByteBuffer => Unit): Unit =
        submit(ReadOp((IOSQE_BUFFER_SELECT | IOSQE_ASYNC).toByte, fd, offset, 0, length, 0, theBufferGroup)) { (res, flags) =>
          if (res == -105 /*ENOBUFS */ ) read(fd, offset, length)(handleRead) // just retry
          else {
            if (res < 0) throw new RuntimeException(s"Result was negative: ${res}")
            else if (res == 0) handleRead(EmptyByteBuffer)
            else handleRead(resultBuffer(res, flags))
            // give buffer back
            submitGlobal(ProvideBuffersOp(0, 1, perBufferSize, buffers + perBufferSize * (flags >> IORING_CQE_BUFFER_SHIFT), 0x1234, specialTag))
          }
        }

      override def write(fd: Int, offset: Long, buffer: ByteBuffer)(handleWrite: Int => Unit): Unit = {
        val len = buffer.remaining()
        TheWriteBufferByteBuffer.clear()
        TheWriteBufferByteBuffer.put(buffer)
        submit(WriteOp(IOSQE_ASYNC, fd, offset, TheWriteBuffer, len, 0)) { (res, _) =>
          require(res == len)
          handleWrite(res)
        }
      }
    }

    def loop(): Unit = while (true) {
      // call uring enter to submit / collect completions
      // run completions one by one
      // on IO: add submission entry (and enter?)
      val toSubmit = sqTail() - sqHead()
      debug(s"sqHead: ${sqHead()} sqTail: ${sqTail()} toSubmit: $toSubmit")
      //val res = LibC2.syscall(LibC.SYSCALL_IO_URING_ENTER, uringFd, toSubmit, 1, IORING_ENTER_GETEVENTS, 0)
      val res = libC.syscall(LibC.SYSCALL_IO_URING_ENTER, uringFd, toSubmit, 1, IORING_ENTER_GETEVENTS, 0)
      //require(toSubmit == res)

      //x += 1

      var cqIdx = cqHead()
      val last = cqTail()

      debug(s"cqHead: $cqIdx cqTail: $last")
      while (cqIdx < last) {
        debug(s"At $cqIdx")
        val idx = cqIdx & cqMask
        //val cqeP = new IoUringCqe(cqRingPointer.share(params.cq_off.cqes))
        //val cqes = cqeP.toArray(params.cq_entries).asInstanceOf[Array[IoUringCqe]]
        //val cqe = new IoUringCqe(cqRingPointer.share(params.cq_off.cqes + idx * 16))
        //debug(cqe.toString)
        val off = params.cq_off.cqes + idx * 16
        context.handle(cqRingPointerByteBuffer.getLong(off), cqRingPointerByteBuffer.getInt(off + 8), cqRingPointerByteBuffer.getInt(off + 12))
        cqIdx += 1
      }
      cqRingPointerByteBuffer.putInt(params.cq_off.head, last)
    }
  }
}

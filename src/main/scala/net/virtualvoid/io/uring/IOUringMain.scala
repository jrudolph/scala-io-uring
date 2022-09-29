package net.virtualvoid.io.uring

import com.sun.jna.{ Native, Pointer }

import java.io.{ FileDescriptor, RandomAccessFile }
import java.net.{ InetSocketAddress, ServerSocket, SocketAddress, SocketImpl, SocketOptions, StandardSocketOptions }
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.util

object IOUringMain extends App {
  val fdOf: RandomAccessFile => Int = {
    /*val fdField = classOf[RandomAccessFile].getDeclaredField("fd")
    fdField.setAccessible(true)
    val fdFdField = classOf[FileDescriptor].getDeclaredField("fd")
    fdFdField.setAccessible(true)*/

    { raf =>
      val fd = raf.getFD
      //fdFdField.get(fd).asInstanceOf[Int]
      sun.nio.ch.IOUtil.fdVal(fd)
    }
  }
  val fdOfServerSocket: ServerSocket => Int = {
    val implField = classOf[ServerSocket].getDeclaredField("impl")
    implField.setAccessible(true)
    val fdField = classOf[SocketImpl].getDeclaredField("fd")
    fdField.setAccessible(true)

    { socket =>
      val impl = implField.get(socket)
      val fd = fdField.get(impl).asInstanceOf[FileDescriptor]
      require(fd ne null)
      sun.nio.ch.IOUtil.fdVal(fd)
    }
  }
  val fdOfServerSocketChannel: ServerSocketChannel => Int = {
    val fdField = Class.forName("sun.nio.ch.ServerSocketChannelImpl").getDeclaredField("fd")
    fdField.setAccessible(true)

    { ch =>
      val fd = fdField.get(ch).asInstanceOf[FileDescriptor]
      require(fd ne null)
      sun.nio.ch.IOUtil.fdVal(fd)
    }
  }

  val libC = Native.load("c", classOf[LibC])

  class UringThread extends Thread {
    val params = new IoUringParams
    val uringFd = libC.syscall(LibC.SYSCALL_IO_URING_SETUP, 1024, params)
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

    val sqRingPointerByteBuffer = sqRingPointer.getByteBuffer(0, length)
    val cqRingPointerByteBuffer = cqRingPointer.getByteBuffer(0, params.cq_off.cqes + params.cq_entries * 16)
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

    val IORING_OP_NOP = 0: Byte
    val IORING_OP_READV = 1: Byte
    val IORING_OP_ACCEPT = 13: Byte
    val IORING_OP_READ = 22: Byte
    val IORING_OP_WRITE = 23: Byte
    val IORING_OP_PROVIDE_BUFFERS = 31: Byte

    val IOSQE_IO_DRAIN = 2: Byte
    val IOSQE_IO_LINK = 4: Byte
    val IOSQE_BUFFER_SELECT = 32: Byte

    val IORING_ENTER_GETEVENTS = 1

    val IORING_CQE_BUFFER_SHIFT = 16

    val IORING_CQE_F_BUFFER = 1

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

    sealed abstract class Op(val opId: Byte) {
      def prepareSQE(buffer: ByteBuffer): Unit
      def userData: Long
      def withUserData(newUserData: Long): Op
    }
    case class NopOp(
        flags:    Byte,
        userData: Long
    ) extends Op(IORING_OP_NOP) {
      override def prepareSQE(buffer: ByteBuffer): Unit = {
        buffer.put(opId)
        buffer.put(flags)
        buffer.putShort(0 /* ioprio */ )
        buffer.putInt(0)
        buffer.putLong(0)
        buffer.putLong(0)
        buffer.putLong(0)
        buffer.putLong(userData)
        buffer.putLong(0)
        buffer.putLong(0)
        buffer.putLong(0)
      }

      override def withUserData(newUserData: Long): NopOp = copy(userData = newUserData)
    }
    case class ProvideBuffersOp(
        flags:         Byte,
        numBuffers:    Int,
        sizePerBuffer: Int,
        addr:          Long,
        bufferGroup:   Short,
        userData:      Long
    ) extends Op(IORING_OP_PROVIDE_BUFFERS) {
      override def prepareSQE(buffer: ByteBuffer): Unit = {
        buffer.put(opId)
        buffer.put(flags)
        buffer.putShort(0 /* ioprio */ )
        buffer.putInt(numBuffers)
        buffer.putLong(0)
        buffer.putLong(addr)
        buffer.putInt(sizePerBuffer)
        buffer.putInt(0)
        buffer.putLong(userData)
        buffer.putShort(bufferGroup)
        buffer.putShort(0)
        buffer.putInt(0)
        buffer.putLong(0)
        buffer.putLong(0)
      }

      override def withUserData(newUserData: Long): ProvideBuffersOp = copy(userData = newUserData)
    }

    case class AcceptOp(
        flags:    Byte,
        socketFd: Int,
        sockAddr: Long,
        addrLen:  Long,
        userData: Long) extends Op(IORING_OP_ACCEPT) {
      override def prepareSQE(buffer: ByteBuffer): Unit = {
        buffer.put(opId)
        buffer.put(flags)
        buffer.putShort(0 /* ioprio */ )
        buffer.putInt(socketFd)
        buffer.putLong(addrLen)
        buffer.putLong(sockAddr)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putLong(userData)
        buffer.putLong(0)
        buffer.putLong(0)
        buffer.putLong(0)
      }
      override def withUserData(newUserData: Long): AcceptOp = copy(userData = newUserData)
    }

    case class ReadOp(
        flags:             Byte,
        fd:                Int,
        destinationOffset: Long,
        targetBufferAddr:  Long,
        targetBufferSize:  Int,
        userData:          Long,
        bufferGroup:       Short = 0 // FIXME: buffer selection should be abstracted
    ) extends Op(IORING_OP_READ) {
      override def prepareSQE(buffer: ByteBuffer): Unit = {
        buffer.put(opId)
        buffer.put(flags)
        buffer.putShort(0 /* ioprio */ )
        buffer.putInt(fd)
        buffer.putLong(destinationOffset)
        buffer.putLong(targetBufferAddr)
        buffer.putInt(targetBufferSize)
        buffer.putInt(0)
        buffer.putLong(userData)
        buffer.putShort(bufferGroup)
        buffer.putShort(0)
        buffer.putInt(0)
        buffer.putLong(0)
        buffer.putLong(0)
      }
      override def withUserData(newUserData: Long): ReadOp = copy(userData = newUserData)
    }
    case class WriteOp(
        flags:             Byte,
        fd:                Int,
        destinationOffset: Long,
        srcBufferAddr:     Long,
        srcBufferSize:     Int,
        userData:          Long
    ) extends Op(IORING_OP_WRITE) {
      override def prepareSQE(buffer: ByteBuffer): Unit = {
        buffer.put(opId)
        buffer.put(flags)
        buffer.putShort(0 /* ioprio */ )
        buffer.putInt(fd)
        buffer.putLong(destinationOffset)
        buffer.putLong(srcBufferAddr)
        buffer.putInt(srcBufferSize)
        buffer.putInt(0)
        buffer.putLong(userData)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putLong(0)
        buffer.putLong(0)
      }
      override def withUserData(newUserData: Long): WriteOp = copy(userData = newUserData)
    }
    val sqes = sqePointer.getByteBuffer(0, 64 * params.sq_entries)
    def submitGlobal(op: Op): Unit = {
      require(op.userData != 0L)
      // FIXME: check against overflow
      val curTail = sqTail()
      val index = curTail & sqMask
      //op.prepareSQE(sqePointer.getByteBuffer(64 * index, 64))
      sqes.clear()
      sqes.position(64 * index)
      op.prepareSQE(sqes)
      sqRingPointerByteBuffer.putInt(params.sq_off.array + 4 * index, index)
      //sqRingPointer.setInt(params.sq_off.array + 4 * index, index) // should use unsafe.putIntVolatile
      x = 23 // write barrier (?)
      //sqRingPointer.setInt(params.sq_off.tail, curTail + 1) // should use unsafe.putIntVolatile for the correct barrier
      sqRingPointerByteBuffer.putInt(params.sq_off.tail, curTail + 1)
      x = 42 // write barrier (?)
    }
    def submitGlobalWithUserData(op: Op, userData: Long): Unit = {
      //require(op.userData != 0L)
      // FIXME: check against overflow
      val curTail = sqTail()
      val index = curTail & sqMask
      //op.prepareSQE(sqePointer.getByteBuffer(64 * index, 64))
      sqes.clear()
      sqes.position(64 * index)
      op.prepareSQE(sqes)
      sqes.putLong(64 * index + 32, userData)
      sqRingPointerByteBuffer.putInt(params.sq_off.array + 4 * index, index)
      //sqRingPointer.setInt(params.sq_off.array + 4 * index, index) // should use unsafe.putIntVolatile
      x = 23 // write barrier (?)
      //sqRingPointer.setInt(params.sq_off.tail, curTail + 1) // should use unsafe.putIntVolatile for the correct barrier
      sqRingPointerByteBuffer.putInt(params.sq_off.tail, curTail + 1)
      x = 42 // write barrier (?)
    }

    //val sqeIndex = 0
    /*val sqe = new IoUringSqe.ByReference(sqePointer.share(64 * sqeIndex)) // sqe = &sqring→sqes[index]
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
  sqe.write()*/
    val specialTag = -1L
    val numBuffers = 200
    val perBuffer = 4096
    val buffers = Native.malloc(numBuffers * perBuffer)
    val buffersPointer = new Pointer(buffers)
    submitGlobal(ProvideBuffersOp(0, numBuffers, perBuffer, buffers, 0x1234, specialTag))

    //val resAddr = Native.malloc(100)
    //val resPointer = new Pointer(resAddr)
    /*val read = ReadOp(IOSQE_BUFFER_SELECT, fd, 0, 0, 100, 0xdeadbeef, 0x1234)
  submit(read)*/

    //val resAddr2 = Native.malloc(50)
    //val resPointer2 = new Pointer(resAddr2)
    /*val read2 = ReadOp(IOSQE_BUFFER_SELECT, fd, 100, 0, 50, 0xcafebabe, 0x1234)
  submit(read2)

  val sqe2 = new IoUringSqe.ByReference(sqePointer.share(64 * 0)) // sqe = &sqring→sqes[index]
  println(s"sqe2: $sqe2")*/

    //sqRingPointer.setInt(params.sq_off.array + 4 * index, sqeIndex) // sqring→array[index] = index;
    // write_barrier()
    //x = 12 // emulate write_barrier?
    //sqRingPointer.setInt(params.sq_off.tail, curTail + 1)
    //x = 14 // emulate write_barrier?
    // write_barrier()

    val ssc = ServerSocketChannel.open()
    ssc.setOption[java.lang.Boolean](StandardSocketOptions.SO_REUSEPORT, true)
    ssc.bind(new InetSocketAddress(8081))
    ssc.configureBlocking(true)
    //val s = ssc.socket()
    //println(s"block: ${s.getChannel.isBlocking}")
    val sfd = fdOfServerSocketChannel(ssc)
    println(s"socket fd $sfd")
    //submitGlobal(AcceptOp(0, sfd, 0, 0, 0xcafebabe))
    //submitGlobal(AcceptOp(0, sfd, 0, 0, 0xcafebab1))

    //val IORING_ENTER_GETEVENTS = 1

    //val res = libC.syscall(LibC.SYSCALL_IO_URING_ENTER, uringFd, 2, 0, 0, 0)

    /*while (true) {
    // check cqes before call
    println(s"cqes head: ${cqRingPointer.getInt(params.cq_off.head)} tail: ${cqRingPointer.getInt(params.cq_off.tail)}")

    val res = libC.syscall(LibC.SYSCALL_IO_URING_ENTER, uringFd, 0, 1, IORING_ENTER_GETEVENTS, 0)
    println(s"enter result: $res")
    println(s"after enter sq tail: ${sqRingPointer.getInt(params.sq_off.tail)} sq head: ${sqRingPointer.getInt(params.sq_off.head)}")

    sqRingPointer.setInt(params.cq_off.head, sqRingPointer.getInt(params.cq_off.head) + 1)

    val cqeP = new IoUringCqe(cqRingPointer.share(params.cq_off.cqes))
    val cqes = cqeP.toArray(params.cq_entries).asInstanceOf[Array[IoUringCqe]]
    println(x)
    println(s"cq head: ${cqRingPointer.getInt(params.cq_off.head)} tail: ${cqRingPointer.getInt(params.cq_off.tail)}")
    println(cqes(0))
    println(cqes(1))
    println(cqes(2))
  }*/

    /*{
    val cqId = 1
    val bufId = cqes(cqId).flags >> IORING_CQE_BUFFER_SHIFT
    println(bufId)
    println(new String(buffersPointer.getByteArray(bufId * perBuffer, cqes(cqId).res)))
  }
  {
    val cqId = 2
    val bufId = cqes(cqId).flags >> IORING_CQE_BUFFER_SHIFT
    println(bufId)
    println(new String(buffersPointer.getByteArray(bufId * perBuffer, cqes(cqId).res)))
  }*/
    //println(new String(resPointer.getByteArray(0, cqes(0).res)))
    //println(new String(resPointer2.getByteArray(0, cqes(1).res)))

    //val readvres = libC.readv(fd, iovec, 1)
    //println(readvres)
    //println(resPointer.getByteArray(0, 100).toSeq)

    //def debug(str: String): Unit = println(str)
    def debug(str: => String): Unit = {}

    val EmptyByteBuffer = ByteBuffer.allocate(0)
    val TheWriteBuffer = Native.malloc(1000)
    val TheWriteBufferP = new Pointer(TheWriteBuffer)
    val TheWriteBufferByteBuffer = TheWriteBufferP.getByteBuffer(0, 1000)

    val buffersByteBuffer = buffersPointer.getByteBuffer(0, numBuffers * perBuffer)

    trait IOContext {
      def spawn(f: => Unit): Unit
      def accept(fd: Int)(handleSocket: Int => Unit): Unit
      def read(fd: Int, offset: Long, length: Int)(handleRead: ByteBuffer => Unit): Unit
      def write(fd: Int, offset: Long, buffer: ByteBuffer)(handleWrite: Int => Unit): Unit

      def handle(userData: Long, res: Int, flags: Int): Unit
    }
    val uringIoContext = new IOContext {
      val outstandingEntries = new scala.collection.mutable.LongMap[(Int, Int) => Unit](1024)
      private var nextId = 1L
      val theBufferGroup = 0x1234: Short
      def resultBuffer(res: Int, flags: Int): ByteBuffer = {
        val bufId = flags >> IORING_CQE_BUFFER_SHIFT
        //require(bufId == remainingBuffers - 1, s"Didn't get last buffer, bufId: $bufId, expected: ${remainingBuffers - 1}")
        //remainingBuffers -= 1
        //println(s"rem: $remainingBuffers")
        //buffersPointer.getByteBuffer(bufId * perBuffer, res)
        val off = bufId * perBuffer
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
        submit(AcceptOp(0, sfd, 0, 0, 0))((res, _) => handleSocket(res))

      override def read(fd: Int, offset: Long, length: Int)(handleRead: ByteBuffer => Unit): Unit =
        submit(ReadOp(IOSQE_BUFFER_SELECT, fd, offset, 0, length, 0, theBufferGroup)) { (res, flags) =>
          if (res == -105 /*ENOBUFS */ ) read(fd, offset, length)(handleRead) // just retry
          else {
            if (res < 0) throw new RuntimeException(s"Result was negative: ${res}")
            else if (res == 0) handleRead(EmptyByteBuffer)
            else handleRead(resultBuffer(res, flags))
            // give buffer back
            submitGlobal(ProvideBuffersOp(0, 1, perBuffer, buffers + perBuffer * (flags >> IORING_CQE_BUFFER_SHIFT), 0x1234, specialTag))
          }
        }

      override def write(fd: Int, offset: Long, buffer: ByteBuffer)(handleWrite: Int => Unit): Unit = {
        val len = buffer.remaining()
        TheWriteBufferByteBuffer.clear()
        TheWriteBufferByteBuffer.put(buffer)
        submit(WriteOp(0, fd, offset, TheWriteBuffer, len, 0)) { (res, _) =>
          require(res == len)
          handleWrite(res)
        }
      }
    }

    def sqHead() = sqRingPointerByteBuffer.getInt(params.sq_off.head)
    def sqTail() = sqRingPointerByteBuffer.getInt(params.sq_off.tail)
    lazy val sqMask = sqRingPointerByteBuffer.getInt(params.sq_off.ring_mask)

    def cqHead() = cqRingPointerByteBuffer.getInt(params.cq_off.head)
    def cqTail() = cqRingPointerByteBuffer.getInt(params.cq_off.tail)
    val cqMask = cqRingPointerByteBuffer.getInt(params.cq_off.ring_mask)

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
        uringIoContext.handle(cqRingPointerByteBuffer.getLong(off), cqRingPointerByteBuffer.getInt(off + 8), cqRingPointerByteBuffer.getInt(off + 12))
        cqIdx += 1
      }
      cqRingPointerByteBuffer.putInt(params.cq_off.head, last)
    }

    val theResponse = "HTTP/1.1 200 OK\r\nContent-Length: 12\r\nConnection: keep-alive\r\nContent-Type: text/plain\r\n\r\nHello World!".getBytes

    def webserver(socketFd: Int, ctx: IOContext): Unit =
      ctx.accept(socketFd) { socketFd =>
        debug(s"Got socket: $socketFd")
        ctx.spawn {
          def handleReq(existing: String = null): Unit =
            ctx.read(socketFd, 0, perBuffer) { buf =>
              val b = new Array[Byte](buf.remaining())
              buf.get(b)
              val str = new String(b)
              val req = if (existing eq null) str else existing + str // FIXME: split unicode
              if (req.endsWith("\r\n\r\n")) {
                debug(s"Got request: '$req'")
                ctx.write(socketFd, 0, ByteBuffer.wrap(theResponse))(_ => ())
                handleReq(null)
              } else handleReq(req)
            }
          handleReq()
        }
        webserver(socketFd, ctx)
      }

    override def run(): Unit = {
      webserver(sfd, uringIoContext)
      loop()
    }
  }
  (0 until 1).map { i =>
    val t = new UringThread
    t.start()
    t
  }.foreach(_.join)
}

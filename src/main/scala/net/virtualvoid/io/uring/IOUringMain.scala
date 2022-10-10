package net.virtualvoid.io.uring

import java.net.{ InetSocketAddress, StandardSocketOptions }
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel

object IOUringMain extends App {
  import IOUring._
  import Utils._

  val numThreads = 1

  class UringThread extends Thread {

    val ssc = ServerSocketChannel.open()
    ssc.setOption[java.lang.Boolean](StandardSocketOptions.SO_REUSEPORT, true)
    ssc.bind(new InetSocketAddress(8081))
    ssc.configureBlocking(true)
    val serverSocketFd = fdOfServerSocketChannel(ssc)
    println(s"socket fd $serverSocketFd")

    val bufferSize = 4096
    val uring = IOUring(1024, bufferSize)

    val theResponse = "HTTP/1.1 200 OK\r\nContent-Length: 12\r\nConnection: keep-alive\r\nContent-Type: text/plain\r\n\r\nHello World!".getBytes

    def webserver(socketFd: Int, ctx: IOContext): Unit =
      ctx.accept(socketFd) { socketFd =>
        debug(s"Got socket: $socketFd")
        ctx.spawn {
          def handleReq(existing: String = null): Unit =
            ctx.read(socketFd, 0, bufferSize) { buf =>
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
      webserver(serverSocketFd, uring.context)
      uring.loop()
    }
  }
  (1 to numThreads).map { i =>
    val t = new UringThread
    t.start()
    t
  }.foreach(_.join)
}

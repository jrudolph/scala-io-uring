package net.virtualvoid.io.uring

import java.io.{ FileDescriptor, RandomAccessFile }
import java.net.{ ServerSocket, SocketImpl }
import java.nio.channels.ServerSocketChannel

object Utils {
  val fdOf: RandomAccessFile => Int = {
    { raf =>
      val fd = raf.getFD
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
}
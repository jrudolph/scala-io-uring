package net.virtualvoid.io.uring

import java.nio.ByteBuffer

trait IOContext {
  def spawn(f: => Unit): Unit
  def accept(fd: Int)(handleSocket: Int => Unit): Unit
  def read(fd: Int, offset: Long, length: Int)(handleRead: ByteBuffer => Unit): Unit
  def write(fd: Int, offset: Long, buffer: ByteBuffer)(handleWrite: Int => Unit): Unit

  def handle(userData: Long, res: Int, flags: Int): Unit
}
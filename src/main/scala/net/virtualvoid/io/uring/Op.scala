package net.virtualvoid.io.uring

import java.nio.ByteBuffer
import IOUring._

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
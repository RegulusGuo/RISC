package utils

import chisel3._
import chisel3.util._

object MaskData {
  def apply(oldData: UInt, newData: UInt, fullmask: UInt) = {
    (newData & fullmask) | (oldData & (~fullmask).asUInt())
  }
}

object CSRMap {

  def WritableMask = Fill(64, true.B)

  def UnwritableMask = 0.U(64.W)

  def Unwritable = null

  def NoSideEffect: UInt => UInt = (x => x)

  def isIllegalAddr(mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)], addr: UInt):Bool = {
    val illegalAddr = Wire(Bool())
    val chiselMapping = mapping.map { case (a, (r, wm, w, rm)) => (a.U, r, wm, w, rm) }
    illegalAddr := LookupTreeDefault(addr, true.B, chiselMapping.map { case (a, r, wm, w, rm) => (a, false.B) })
    illegalAddr
  }

  def apply(addr : Int,
            reg  : UInt,
            wmask: UInt = WritableMask,
            wfn  : UInt => UInt = x => x,
            rmask: UInt = WritableMask) = (addr, (reg, wmask, wfn, rmask))

  def generate(mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)], raddr: UInt, rdata: UInt,
               waddr  : UInt, wen: Bool, wdata: UInt): Unit = {
    val chiselMapping = mapping.map { case (a, (r, wm, w, rm)) => (a.U, r, wm, w, rm) }
    rdata := LookupTree(raddr, chiselMapping.map { case (a, r, wm, w, rm) => (a, r & rm) })
    chiselMapping.map { case (a, r, wm, w, rm) =>
      if (w != null && wm != UnwritableMask) when(wen && waddr === a) {
        r := w(MaskData(r, wdata, wm))
      }
    }
  }


  def generate(mapping: Map[Int, (UInt, UInt, UInt => UInt, UInt)], addr: UInt, rdata: UInt,
               wen    : Bool, wdata: UInt): Unit = generate(mapping, addr, rdata, addr, wen, wdata)
}

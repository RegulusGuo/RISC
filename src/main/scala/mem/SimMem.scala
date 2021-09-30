package mem

import chisel3._
import chisel3.util._
import mycore._

class ROM_D extends BlackBox with HasBlackBoxResource {
    val io = IO(new ImemIO)
    addResource("/ROM_D.v")
}

class RAM_B extends BlackBox with HasBlackBoxResource {
    val io = IO(new DmemIO)
    addResource("/RAM_B.v")
}
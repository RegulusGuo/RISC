package tile

import chisel3._
import chisel3.util._
import chisel3.stage._
import mycore._
import fu._
import isa._
import mem._
import mycore._
import config.Config
import scala.annotation.switch

class TileIO extends Bundle with Config {
    val step_clk   = Input(Bool())
    val step_en    = Input(Bool())
    val debug_addr = Input(UInt(7.W))
    val debug_data = Output(UInt(XLEN.W))
    val interrupt  = Input(Bool())
}

class Tile extends Module with Config {
    val io = IO(new TileIO)

    val core = Module(new Core)
    val imem = Module(new ROM_D)
    val dmem = Module(new RAM_B)
    
    core.io.imem      <> imem.io
    core.io.dmem      <> dmem.io
    core.io.interrupt <> io.interrupt
    // core.io.clk := Mux(io.step_en, io.step_clk, clock)
    
    core.io.debug.bd.debug_addr := io.debug_addr
    when (io.debug_addr(5)) {
        io.debug_data := MuxLookup (
            io.debug_addr(4, 0), 0.U,
            Seq(
                0.U  -> core.io.debug.fd.pc_if,
                4.U  -> core.io.debug.fd.pc_id,
                8.U  -> core.io.debug.bd.pc_is,
                12.U -> core.io.debug.bd.pc_ex,
                16.U -> core.io.debug.bd.pc_wb
            )
        )
    }.otherwise {
        io.debug_data := core.io.debug.bd.reg_data
    }
}

object GenT {
  def main(args: Array[String]): Unit = {
    val packageName = this.getClass.getPackage.getName
    (new chisel3.stage.ChiselStage).execute(
      Array("-td", "build/verilog/"+packageName, "-X", "verilog"),
      Seq(ChiselGeneratorAnnotation(() => new Tile)))
  }
}
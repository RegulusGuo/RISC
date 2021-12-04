package tile

import chisel3._
import chisel3.util._
import chisel3.stage._
import mycore._
import fu._
import isa._
import cache._
import mycore._
import config.Config
import scala.annotation.switch

class TileIO extends Bundle with Config {
    val step_clk   = Input(Bool())
    val step_en    = Input(Bool())
    val debug_addr = Input(UInt(7.W))
    val debug_data = Output(UInt(XLEN.W))
    val sim_uart_char_out = Output(UInt(8.W))
    val sim_uart_char_valid = Output(Bool())
    val cache_hit  = Output(Bool())
    val interrupt  = Input(Bool())
}

class Tile extends Module with Config {
    val io = IO(new TileIO)

    val core   = Module(new Core)
    val dcache = Module(new DCache)
    val xbar = Module(new XBar)
    val dmem = Module(new RAM_B)
    val imem = Module(new ROM_D)
    
    core.io.imem      <> imem.io
    // core.io.dmem      <> dmem.io
    core.io.dcache    <> dcache.io.cpu
    xbar.io.dcache    <> dcache.io.bar
    xbar.io.dmem      <> dmem.io
    core.io.interrupt <> io.interrupt
    io.cache_hit := dcache.io.dbg.hit
    io.sim_uart_char_out   := dmem.io.sim_uart_char_out
    io.sim_uart_char_valid := dmem.io.sim_uart_char_valid
    
    core.io.debug.bd.debug_addr := io.debug_addr
    when (io.debug_addr(5)) {
        io.debug_data := MuxLookup (
            io.debug_addr(4, 0), 0.U,
            Seq(
                0.U  -> core.io.debug.fd.pc_if,
                4.U  -> core.io.debug.fd.pc_id,
                8.U  -> core.io.debug.bd.pc_is,
                12.U -> core.io.debug.bd.pc_ex,
                16.U -> core.io.debug.bd.pc_wb,
                20.U -> core.io.debug.bd.stall,
                21.U -> core.io.debug.bd.redirect,
                24.U -> core.io.debug.bd.trap_redirect,
                25.U -> core.io.debug.bd.is_ecall
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
package fu

import chisel3._
import chisel3.util._
import config.Config
import isa._
import utils._

class CSREventIO extends Bundle with Config {
    // exception
    val is_ecall         = Input(Bool())
    val illegal_inst     = Input(Bool())
    val mem_access_fault = Input(Bool())
    // interrupt
    val external_int     = Input(Bool())
    // tvec
    val redirect_pc      = Output(UInt(XLEN.W))
}

class CSRCommonIO extends Bundle with Config {
    val wen  = Input(Bool())
    val din  = Input(UInt(XLEN.W))
    val rd   = Input(UInt(CSRNumBits.W))
    val dout = Output(UInt(XLEN.W))
}

class CSRIO extends Bundle with Config {
    val event_io  = new CSREventIO
    val common_io = new CSRCommonIO
}

class CSR extends Module with Config {
    val io = IO(new CSRIO)

    val current_mode = RegInit(Privilege.M)

    // CSRs
    // defaults and not implemented
    val misa      = RegInit(0.U(MXLEN.W)) // return 0 as not implemented
    val mvendorid = RegInit(0.U(32.W))    // return 0 as non-commercial implementation
    val marchid   = RegInit(0.U(MXLEN.W)) // return 0 as not implemented
    val mimpid    = RegInit(0.U(MXLEN.W)) // return 0 as not implemented
    val mhartid   = RegInit(0.U(MXLEN.W)) // single core (reference page.31 of privileged manual)

    val mstatus    = RegInit((new MstatusStruct).init())
    val medeleg    = RegInit(0.U(MXLEN.W))
    val mideleg    = RegInit(0.U(MXLEN.W))
    val mie        = RegInit(0.U.asTypeOf(new MieStruct).asUInt())
    val mtvec      = RegInit(0.U.asTypeOf(new MtevcStruct).asUInt())
    val mcounteren = RegInit(0.U.asTypeOf(new MScounterenStruct).asUInt())

    val mscratch = RegInit(0.U(MXLEN.W))
    val mepc     = RegInit(0.U(MXLEN.W))
    val mcause   = RegInit(0.U.asTypeOf(new McauseStruct).asUInt())
    val mtval    = RegInit(0.U(MXLEN.W))

    val mipReg  = RegInit(0.U.asTypeOf(new MipStruct).asUInt())
    val mipWire = WireDefault(0.U.asTypeOf(new MipStruct).asUInt())
    val mipOut  = WireDefault(0.U.asTypeOf(new MipStruct).asUInt())
    val mip     = (mipWire | mipReg).asTypeOf(new MipStruct).asUInt()

    // ...
    val mtime         = RegInit(0.U(64.W))
    val mtimecmp      = RegInit(0.U(64.W))
    val mcountinhibit = RegInit(0.U.asTypeOf(new McountinhibitStruct).asUInt())

    // CSRs mapping, including addr, reg, wmask, write function, rmask
    val mapping = Map(
        // machine mode CSRs
        CSRMap(CSRNumber.misa,      misa,      CSRMap.UnwritableMask, CSRMap.Unwritable),
        CSRMap(CSRNumber.mvendorid, mvendorid, CSRMap.UnwritableMask, CSRMap.Unwritable),
        CSRMap(CSRNumber.marchid,   marchid,   CSRMap.UnwritableMask, CSRMap.Unwritable),
        CSRMap(CSRNumber.mimpid,    mimpid,    CSRMap.UnwritableMask, CSRMap.Unwritable),
        CSRMap(CSRNumber.mhartid,   mhartid,   CSRMap.UnwritableMask, CSRMap.Unwritable),
        CSRMap(CSRNumber.mtvec,     mtvec),
        CSRMap(CSRNumber.medeleg,   medeleg,   "hbbff".U), // reference page 40 of privileged manual
        CSRMap(CSRNumber.mideleg,   mideleg,   "h222".U), // reference page 40 of privileged manual
        CSRMap(CSRNumber.mip,       mip,       CSRMap.UnwritableMask, CSRMap.Unwritable),
        CSRMap(CSRNumber.mip,       mipReg,    CSRMask.mip_RW),
        CSRMap(CSRNumber.mie,       mie),
        CSRMap(CSRNumber.mcounteren, mcounteren),
        CSRMap(CSRNumber.mcountinhibit, mcountinhibit),
        CSRMap(CSRNumber.mscratch, mscratch),
        CSRMap(CSRNumber.mepc,   mepc),
        CSRMap(CSRNumber.mcause, mcause),
        CSRMap(CSRNumber.mtval, mtval),
        CSRMap(CSRNumber.mstatus,   mstatus,   CSRMap.WritableMask,
          (new MstatusStruct).updateSideEffect, CSRMap.WritableMask),
    )

    // Read / Write CSR
    val wdata = io.common_io.din
    val wen   = io.common_io.wen
    val addr  = io.common_io.rd
    CSRMap.generate(mapping, addr, io.common_io.dout, wen, wdata)
}
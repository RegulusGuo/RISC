package fu

import chisel3._
import chisel3.util._
import config.Config
import isa._
import utils._

class CSREventIO extends Bundle with Config {
    // inst
    val is_mret          = Input(Bool())
    // exception
    val is_ecall         = Input(Bool())
    val illegal_inst     = Input(Bool())
    val inst             = Input(UInt(XLEN.W))
    val mem_access_fault = Input(Bool())
    val bad_address      = Input(UInt(XLEN.W))
    val epc              = Input(UInt(XLEN.W))
    // interrupt
    val external_int     = Input(Bool())
    // redirect
    val trap_redirect    = Output(Bool())
    val redirect_pc      = Output(UInt(XLEN.W))
    // from pipeline
    val deal_with_int    = Input(Bool())
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

    io.event_io.redirect_pc := 0.U(XLEN.W) // FIXME: For compile

    // Branch
    val ret_target = WireDefault(0.U(XLEN.W))

    when(io.event_io.is_mret) {
        val old_mstatus = WireDefault(mstatus.asTypeOf(new MstatusStruct))
        val new_mstatus = WireDefault(mstatus.asTypeOf(new MstatusStruct))
        new_mstatus.MIE := old_mstatus.MPIE
        new_mstatus.MPP := Privilege.U
        new_mstatus.MPIE := 1.U
        current_mode := old_mstatus.MPP
        mstatus := new_mstatus.asUInt()
        ret_target := mepc
    }

    // Exception & interrupt
    val excp_vec = Wire(Vec(16, Bool()))
    excp_vec.foreach(_ := false.B)
    excp_vec(ExceptionCode.ecall_M) := current_mode === Privilege.M && io.event_io.is_ecall
    excp_vec(ExceptionCode.load_access_fault) := io.event_io.mem_access_fault
    excp_vec(ExceptionCode.illegal_inst) := io.event_io.illegal_inst
    val excp_no = ExceptionCode.ExcpPriority.foldRight(0.U)((i: Int, sum: UInt) => Mux(excp_vec(i), i.U, sum))

    val intr_vec = Wire(Vec(16, Bool()))
    intr_vec.foreach(_ := false.B)
    intr_vec(InterruptCode.M_external) := current_mode === Privilege.M && io.event_io.external_int
    val intr_no = InterruptCode.InterruptPriority.foldRight(0.U)((i: UInt, sum: UInt) => Mux(intr_vec(i), i, sum))

    val has_excp = excp_vec.asUInt.orR
    val has_intr = intr_vec.asUInt.orR
    val cause_no = Wire(UInt(XLEN.W))
    cause_no := Mux(has_intr, intr_no, excp_no)
    when (io.event_io.deal_with_int || has_excp) {
        val old_mstatus = WireDefault(mstatus.asTypeOf(new MstatusStruct))
        val new_mstatus = WireDefault(mstatus.asTypeOf(new MstatusStruct))

        val tval = MuxLookup(
            cause_no, 0.U(XLEN.W),
            Seq(
                ExceptionCode.load_access_fault.U -> io.event_io.bad_address,
                ExceptionCode.illegal_inst.U      -> io.event_io.inst
            )
        )

        mcause := Cat(has_intr, cause_no(MXLEN - 2, 0))
        new_mstatus.MPP := current_mode
        new_mstatus.MPIE := old_mstatus.MIE
        new_mstatus.MIE := 0.U
        mepc := io.event_io.epc
        current_mode := Privilege.M
        mtval := Mux(io.event_io.deal_with_int, 0.U(XLEN.W), tval)
        mstatus := new_mstatus.asUInt()
    }
    val tvec        = mtvec
    val trap_target = tvec & ~"h11".U(XLEN.W) + (tvec(0).asUInt() << 2) * cause_no
    io.event_io.redirect_pc := Mux(has_excp || has_intr, trap_target, ret_target)
    io.event_io.trap_redirect := io.event_io.deal_with_int || io.event_io.is_mret || has_excp
}
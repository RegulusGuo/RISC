package isa

import chisel3._
import chisel3.util._
import config.Config

object Privilege {
    val U = 0.U(2.W)
    val S = 1.U(2.W)
    val H = 2.U(2.W)
    val M = 3.U(2.W)
}

object ExceptionCode {
    val inst_addr_misaligned  = 0
    val inst_access_fault     = 1
    val illegal_inst          = 2
    val breakpoint            = 3
    val load_addr_misaligned  = 4
    val load_access_fault     = 5
    val store_addr_misaligned = 6
    val store_access_fault    = 7
    val ecall_U               = 8
    val ecall_S               = 9
    val ecall_M               = 11
    val inst_page_fault       = 12
    val load_page_fault       = 13
    val store_page_fault      = 15

    val ExcpPriority = Seq(
        breakpoint,
        inst_page_fault,
        inst_access_fault,
        illegal_inst,
        inst_addr_misaligned,
        ecall_U, ecall_S, ecall_M,
        store_addr_misaligned,
        load_addr_misaligned,
        store_page_fault,
        load_page_fault,
        store_access_fault,
        load_access_fault
    )
}

object InterruptCode {
  // def UEIP  = 0
  val S_soft = 1.U(4.W)
  val M_soft = 3.U(4.W)

  // def UTIP  = 4
  val S_time = 5.U(4.W)
  val M_time = 7.U(4.W)

  // def USIP  = 8
  val S_external = 9.U(4.W)
  val M_external = 11.U(4.W)

  val InterruptPriority = Seq(
    M_external,
    M_soft,
    M_time,
    S_external,
    S_soft,
    S_time
    // UEIP, USIP, UTIP
  )
}

object CSRNumber {
  // User Trap Setup
  val ustatus = 0x000
  val uie     = 0x004
  val utvec   = 0x005

  // User Trap Handling
  val uscratch = 0x040
  val uepc     = 0x041
  val ucause   = 0x042
  val utval    = 0x043
  val uip      = 0x044

  // User Floating-Point CSRs (not implemented)
  val fflags = 0x001
  val frm    = 0x002
  val fcsr   = 0x003

  // User Counter/Timers
  val cycle   = 0xC00
  val time    = 0xC01
  val instret = 0xC02

  // Supervisor Trap Setup
  val sstatus    = 0x100
  val sedeleg    = 0x102
  val sideleg    = 0x103
  val sie        = 0x104
  val stvec      = 0x105
  val scounteren = 0x106

  // Supervisor Trap Handling
  val sscratch = 0x140
  val sepc     = 0x141
  val scause   = 0x142
  val stval    = 0x143
  val sip      = 0x144

  // Supervisor Protection and Translation
  val satp = 0x180

  // Machine Information Registers 
  val mvendorid = 0xF11
  val marchid   = 0xF12
  val mimpid    = 0xF13
  val mhartid   = 0xF14

  // Machine Trap Setup
  val mstatus       = 0x300
  val misa          = 0x301
  val medeleg       = 0x302
  val mideleg       = 0x303
  val mie           = 0x304
  val mtvec         = 0x305
  val mcounteren    = 0x306
  val mcountinhibit = 0x320

  // Machine Trap Handling
  val mscratch = 0x340
  val mepc     = 0x341
  val mcause   = 0x342
  val mtval    = 0x343
  val mip      = 0x344

  // Machine Memory Protection
  // TBD
  val pmpcfg0     = 0x3A0
  val pmpcfg1     = 0x3A1
  val pmpcfg2     = 0x3A2
  val pmpcfg3     = 0x3A3
  val pmpaddrBase = 0x3B0
}


// CSR masks
object CSRMask {
  val sstatus_W = "hC6122".U
  val sstatus_R = sstatus_W | "h8000000300018000".U
  val sip_RW    = "h222".U
  val sie_RW    = "h222".U
  val mip_RW    = "h77F".U
}


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//              Complex CSR Structs
//----------------------------------------------
//  Define the structure of the complex CSRs.
//
//    * NOTICE:
//    All of the WPRIx fields returns 0 with
//  x being the starting bit in little endian
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

class MstatusStruct extends Bundle with Config {
  val SD     = UInt(1.W)
  val WPRI23 = UInt(8.W)
  val TSR    = UInt(1.W)
  val TW     = UInt(1.W)
  val TVM    = UInt(1.W)
  val MXR    = UInt(1.W)
  val SUM    = UInt(1.W)
  val MPRV   = UInt(1.W)
  val XS     = UInt(2.W)
  val FS     = UInt(2.W)
  val MPP    = UInt(2.W)
  val WPRI9  = UInt(2.W)
  val SPP    = UInt(1.W)
  val MPIE   = UInt(1.W)
  val WPRI6  = UInt(1.W)
  val SPIE   = UInt(1.W)
  val UPIE   = UInt(1.W)
  val MIE    = UInt(1.W)
  val WPRI2  = UInt(1.W)
  val SIE    = UInt(1.W)
  val UIE    = UInt(1.W)

  def init(): UInt = {
    val bundle = WireDefault(0.U.asTypeOf(new MstatusStruct))
    bundle.MPP := Privilege.M // initialize MPP field
    bundle.MIE := 1.U
    bundle.MPIE := 1.U
    bundle.asUInt()
  }

  def updateSideEffect(mstatus: UInt): UInt = {
    val mstatus_old = WireInit(mstatus.asTypeOf(new MstatusStruct))
    val mstatus_new = Cat(mstatus_old.FS === "b11".U, mstatus(XLEN - 2, 0)) //P27 of riscv-privileged manual
    mstatus_new
  }
}

class MtvecStruct extends Bundle with Config {
  val BASE = UInt((MXLEN - 2).W)
  val MODE = UInt(2.W)
}

class MipStruct extends Bundle with Config {
  val WPRI12 = UInt((MXLEN - 12).W)
  val MEIP   = UInt(1.W)
  val WPRI10 = UInt(1.W)
  val SEIP   = UInt(1.W)
  val UEIP   = UInt(1.W)
  val MTIP   = UInt(1.W)
  val WPRI6  = UInt(1.W)
  val STIP   = UInt(1.W)
  val UTIP   = UInt(1.W)
  val MSIP   = UInt(1.W)
  val WPRI2  = UInt(1.W)
  val SSIP   = UInt(1.W)
  val USIP   = UInt(1.W)
}

class MieStruct extends Bundle with Config {
  val WPRI12 = UInt((MXLEN - 12).W)
  val MEIE   = UInt(1.W)
  val WPRI10 = UInt(1.W)
  val SEIE   = UInt(1.W)
  val UEIE   = UInt(1.W)
  val MTIE   = UInt(1.W)
  val WPRI6  = UInt(1.W)
  val STIE   = UInt(1.W)
  val UTIE   = UInt(1.W)
  val MSIE   = UInt(1.W)
  val WPRI2  = UInt(1.W)
  val SSIE   = UInt(1.W)
  val USIE   = UInt(1.W)
}

// for both M and S
class MScounterenStruct extends Bundle {
  val HPM = UInt(29.W)
  val IR  = UInt(1.W)
  val TM  = UInt(1.W)
  val CY  = UInt(1.W)
}

class McountinhibitStruct extends Bundle {
  val HPM  = UInt(29.W)
  val IR   = UInt(1.W)
  val ZERO = UInt(1.W)
  val CY   = UInt(1.W)
}

class McauseStruct extends Bundle with Config {
  val Interrupt = UInt(1.W)
  val Code      = UInt((MXLEN - 1).W)
}


// class SstatusStruct extends Bundle with Config {
//   val SD     = UInt(1.W)
//   val WPRI34 = UInt((SXLEN - 35).W)
//   val UXL    = UInt(2.W)
//   val WPRI20 = UInt(12.W)
//   val MXR    = UInt(1.W)
//   val SUM    = UInt(1.W)
//   val WPRI17 = UInt(1.W)
//   val XS     = UInt(2.W)
//   val FS     = UInt(2.W)
//   val WPRI9  = UInt(4.W)
//   val SPP    = UInt(1.W)
//   val WPRI6  = UInt(2.W)
//   val SPIE   = UInt(1.W)
//   val UPIE   = UInt(1.W)
//   val WPRI2  = UInt(2.W)
//   val SIE    = UInt(1.W)
//   val UIE    = UInt(1.W)
// }

class StvecStruct extends Bundle with Config {
  val BASE = UInt((SXLEN - 2).W)
  val MODE = UInt(2.W)
}


class SipStruct extends Bundle with Config {
  val WPRI10 = UInt((SXLEN - 10).W)
  val SEIP   = UInt(1.W)
  val UEIP   = UInt(1.W)
  val WPRI6  = UInt(2.W)
  val STIP   = UInt(1.W)
  val UTIP   = UInt(1.W)
  val WPRI2  = UInt(2.W)
  val SSIP   = UInt(1.W)
  val USIP   = UInt(1.W)
}


class SieStruct extends Bundle with Config {
  val WPRI10 = UInt((SXLEN - 10).W)
  val SEIE   = UInt(1.W)
  val UEIE   = UInt(1.W)
  val WPRI6  = UInt(2.W)
  val STIE   = UInt(1.W)
  val UTIE   = UInt(1.W)
  val WPRI2  = UInt(2.W)
  val SSIE   = UInt(1.W)
  val USIE   = UInt(1.W)
}

class ScauseStruct extends Bundle with Config {
  val Interrupt = UInt(1.W)
  val Code      = UInt((SXLEN - 1).W)
}

// class SatpStruct extends Bundle {
//   val MODE = UInt(4.W)
//   val ASID = UInt(16.W)
//   val PPN  = UInt(44.W)

//   def checkMode(satp: UInt): Bool = {
//     val mode = satp.asTypeOf(new SatpStruct).MODE
//     mode === 0.U || mode === 8.U
//   }
// }


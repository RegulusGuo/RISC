package fu

import chisel3._
import chisel3.util._
import config.Config
import isa._
import mycore.RegFile
import utils._
import scala.annotation.switch

class BPUReq extends Bundle with Config {
    val pc = Input(UInt(XLEN.W))
}

class BPUResp extends Bundle with Config {
    val taken  = Output(Bool())
    val target = Output(UInt(XLEN.W))
}

class BPUUpdate extends Bundle with Config {
    val pc          = Input(UInt(XLEN.W))
    val inst_type   = Input(UInt(2.W))
    val real_taken  = Input(Bool())
    val real_target = Input(UInt(XLEN.W))
}

class BPUIO extends Bundle with Config {
    val req    = new BPUReq
    val resp   = new BPUResp
    val update = new BPUUpdate
}

class BPU extends Module with Config {
    val io = IO(new BPUIO)

    val tag = Module(new RegFile(nregs = bhtEntryNum, len = bhtTagBits + 1, nread = 1, nwrite = 1)) // valid + tag
    val bht = Module(new RegFile(nregs = bhtEntryNum, len = 2,              nread = 2, nwrite = 1)) // 2-bit state predict
    val btb = Module(new RegFile(nregs = bhtEntryNum, len = XLEN - 2,       nread = 1, nwrite = 1)) //

    val pc_tag_req    = io.req.pc(XLEN - 1, bhtIndexBits + 2)
    val pc_index_req  = io.req.pc(bhtIndexBits + 1, 2)
    tag.io.rs_addr_vec(0) := pc_index_req
    bht.io.rs_addr_vec(0) := pc_index_req
    btb.io.rs_addr_vec(0) := pc_index_req
    val valid        = tag.io.rs_data_vec(0)(bhtTagBits)
    val fetch_tag    = tag.io.rs_data_vec(0)(bhtTagBits - 1, 0)
    val fetch_taken  = bht.io.rs_data_vec(0)
    val fetch_target = btb.io.rs_data_vec(0)

    val pc_tag_update   = io.update.pc(XLEN - 1, bhtIndexBits + 2)
    val pc_index_update = io.update.pc(bhtIndexBits + 1, 2)
        
    // query
    when (valid && fetch_tag === pc_tag_req) { // hit
        io.resp.taken  := fetch_taken === 2.U || fetch_taken === 3.U
        io.resp.target := fetch_target
    }.otherwise { // not in table
        io.resp.taken  := false.B
        io.resp.target := 0.U
    }

    // update
    val need_update = io.update.inst_type =/= bpuOTHER.U
    tag.io.wen_vec(0)     := need_update
    tag.io.rd_addr_vec(0) := pc_index_update
    tag.io.rd_data_vec(0) := Cat(true.B, pc_tag_update)

    btb.io.wen_vec(0)     := need_update && io.update.real_taken
    btb.io.rd_addr_vec(0) := pc_index_update
    btb.io.rd_data_vec(0) := io.update.real_target(XLEN - 1, 2)

    bht.io.wen_vec(0)     := need_update
    bht.io.rd_addr_vec(0) := pc_index_update
    bht.io.rs_addr_vec(1) := pc_index_update
    val current_bht = bht.io.rs_data_vec(1)
    when (io.update.real_taken) {
        bht.io.rd_data_vec(0) := Mux(io.update.inst_type === bpuJAL.U, 3.U,
            MuxLookup(current_bht, current_bht + 1.U,
            Seq( 3.U -> current_bht )
        ))
    }.otherwise {
        bht.io.rd_data_vec(0) := MuxLookup(current_bht, current_bht - 1.U,
            Seq( 0.U -> current_bht )
        )
    }
}
package mycore

import chisel3._
import chisel3.util._
import fu._
import isa._
import mem._
import config.Config

class Core extends Module with Config {
    val io = IO(new CoreIO)

    val f = Module(new Frontend)
    val b = Module(new Backend)
    f.io.fb     <> b.io.bf
    io.debug.fd <> f.io.fd
    io.debug.bd <> b.io.bd
    io.imem     <> f.io.imem
    io.dmem     <> b.io.dmem
}
sbt "runMain tile.GenT"
cat ./build/verilog/tile/RAM_B.v >> ./build/verilog/tile/Tile.v
cat ./build/verilog/tile/ROM_D.v >> ./build/verilog/tile/Tile.v
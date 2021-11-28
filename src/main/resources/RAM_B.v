module RAM_B (
	input wire clk,
	input wire rst,
	input wire cs,
	input wire we,
	input wire [31:0] addr,
	input wire [31:0] din,
	output wire [31:0] dout,
	output wire stall,
	output reg ack = 0,
	output [2:0] ram_state
	);
	
	parameter
		ADDR_WIDTH = 11;

	localparam
		S_IDLE = 0,
		S_READING1 = 1,
		S_READING2 = 2,
		S_READ = 3,
		S_WRITING1 = 4,
		S_WRITING2 = 5,
		S_WRITE = 6;
	
	reg [31:0] data [0:(1<<ADDR_WIDTH)-1];
	
	integer i = 0;
	initial	begin
		for (i = 12; i < (1<<ADDR_WIDTH); i = i + 1) begin
			data[i] <= 32'b0;
		end
		$readmemh("ram.hex", data);
	end

	reg [2:0]state = 0;
	reg [31:0] out = 0;
	assign ram_state = state;

	reg [2:0]next_state = 0;

	always @ (posedge clk) begin
		if (rst) begin
			state = 0;
		end
		else begin
			state <= next_state;
		end
	end

	always @ (*) begin
		if (cs) begin
			if (we) begin
				if(state == S_IDLE)
					next_state = S_WRITING1;
				else if (S_WRITING1 <= state && state <= S_WRITING2)
					next_state = state + 1;
				else if (state == S_WRITE)
					next_state = S_IDLE;
				else
					next_state = 3'bxxx;
			end
			else begin
				if (S_IDLE <= state && state <= S_READING2)
					next_state = state + 1;
				else if (state == S_READ)
					next_state = S_IDLE;
				else
					next_state = 3'bxxx;
			end
		end
		else begin
			next_state = S_IDLE;
		end
	end

	always @ (posedge clk) begin
		if (state != S_READ && state != S_WRITE) begin
			ack <= 0;
			out <= 0;
		end

		else if (state == S_READ) begin
			ack <= 1;
			out <= data[addr[ADDR_WIDTH+1:2]];
		end

		else if (state == S_WRITE) begin
			ack <= 1;
			data[addr[ADDR_WIDTH+1:2]] <= din;
		end

		else begin
			ack <= 0;
			out <= 0;
		end
	end

	assign dout = out;
	assign stall = cs & ~ack;
	
endmodule

// module RAM_B(
//     input [31:0] addra,
//     input clka,      // normal clock
//     input[31:0] dina,
//     input wea, 
//     output[31:0] douta,
//     output [7:0] sim_uart_char_out,
//     output sim_uart_char_valid,
//     input[2:0] mem_u_b_h_w
// );
//     localparam SIZE = 256;
//     localparam ADDR_LINE = $clog2(SIZE);
//     localparam SIM_UART_ADDR = 32'h10000000;

//     reg[7:0] data[0:SIZE-1];

//     initial	begin
//         $readmemh("ram.hex", data);
//     end

//     always @ (negedge clka) begin
//         if (wea & (addra != SIM_UART_ADDR)) begin
//             data[addra[ADDR_LINE-1:0]] <= dina[7:0];
//             if(mem_u_b_h_w[0] | mem_u_b_h_w[1])
//                 data[addra[ADDR_LINE-1:0] + 1] <= dina[15:8];
//             if(mem_u_b_h_w[1]) begin
//                 data[addra[ADDR_LINE-1:0] + 2] <= dina[23:16];
//                 data[addra[ADDR_LINE-1:0] + 3] <= dina[31:24];
//             end
//         end
//     end

    
//     assign douta = addra == SIM_UART_ADDR ? 32'b0 :
//         mem_u_b_h_w[1] ? {data[addra[ADDR_LINE-1:0] + 3], data[addra[ADDR_LINE-1:0] + 2],
//                     data[addra[ADDR_LINE-1:0] + 1], data[addra[ADDR_LINE-1:0]]} :
//         mem_u_b_h_w[0] ? {mem_u_b_h_w[2] ? 16'b0 : {16{data[addra[ADDR_LINE-1:0] + 1][7]}},
//                     data[addra[ADDR_LINE-1:0] + 1], data[addra[ADDR_LINE-1:0]]} :
//         {mem_u_b_h_w[2] ? 24'b0 : {24{data[addra[ADDR_LINE-1:0]][7]}}, data[addra[ADDR_LINE-1:0]]};
    
//     reg uart_addr_valid;
//     reg [7:0] uart_char;
//     initial begin
//         uart_addr_valid <= 0;
//     end
//     assign sim_uart_char_valid = uart_addr_valid;
//     assign sim_uart_char_out   = uart_char;
//     always @(posedge clka) begin
//         uart_addr_valid <= wea & (addra == SIM_UART_ADDR);
//         uart_char <= dina[7:0];
//         if (sim_uart_char_valid) begin
//             $write("%c", sim_uart_char_out);
//         end
//     end
// endmodule

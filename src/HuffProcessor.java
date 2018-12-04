
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		while (true){
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(BITS_PER_WORD, val);
		}
		out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}

	/**
	 * @param root  HuffNode that's the root to the tree
	 * @param input Bit input stream
	 * @param output Bit output stream
	 * takes the input bitstream and traverses the tree to decompress the file
	 */
	private void readCompressedBits(HuffNode root, BitInputStream input, BitOutputStream output) {
		HuffNode current = root;
		while(true) {
			int bits = input.readBits(1);	//read the next bit - acts like a scanner
			if(bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if(bits == 0) current = current.myLeft;	//traverses the tree
				else current = current.myRight;
				
				if(current.myLeft == null && current.myRight == null) {	//checking that it's a leaf node
					if(current.myValue == PSEUDO_EOF) break;
					else {
						output.writeBits(BITS_PER_WORD, current.myValue);	//adds the next 8 bits to code myValue
						current = root;	//starts back at the beginning of the tree
					}
				}
			}
		}
	}

	/**
	 * @param in   input bitstream
	 * @return HuffNode root of the constructed header tree
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);		//reads the next bit
		if(bit == -1) {
			throw new HuffException("illegal bit");
		}
		if(bit == 0) {
			HuffNode left = readTreeHeader(in);	//recursive call to the left
			HuffNode right = readTreeHeader(in);	//recursive call to the right
			
			return new HuffNode(0, 0, left, right);	//returns an interior node
		}
		else {
			int letterBits = in.readBits(BITS_PER_WORD + 1);	//takes 9 bits assigned to the character stored in the node
			return new HuffNode(letterBits, 0, null, null);		//returns a leaf node
		}
	}
}
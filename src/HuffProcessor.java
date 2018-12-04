import java.util.PriorityQueue;

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

//		while (true){
//			int val = in.readBits(BITS_PER_WORD);
//			if (val == -1) break;
//			out.writeBits(BITS_PER_WORD, val);
//		}
//		out.close();
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			String code = codings[in.readBits(BITS_PER_WORD)];
			if(code == null) break;		//TODO probs won't work but hey, why not give it a go
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code, 2));
		
	}

	/**
	 * @param root  root node of the encoding tree
	 * @param out   output bitstream
	 * writes the encoding tree to the output bitstream with a "0" indicating an internal node and a "1"
	 * indicating a leaf node followed by 9 bits for the character
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if(root.myLeft == null && root.myRight == null) {		//base case-it's a leaf node
			out.writeBits(1, 0);	//writes the 1 cueing it being a leaf node
			out.writeBits(BITS_PER_WORD + 1, root.myValue);	//write the 9 (cuz of PSEUDO possible 257) character bits
			return;
		}
		out.writeBits(1, 0);	//write the 0, cueing an internal node
		writeHeader(root.myLeft, out);	//recursive call for pre-order traversal
		writeHeader(root.myRight, out);
	}

	/**
	 * @param root  root node of the encoding tree
	 * @return  String[] of all the character encodings
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		return treeRecursion(root, "", encodings);
	}
	
	/**
	 * @param root starting root from the tree
	 * @param path String of the path taken so far
	 * @param encodings   String[] of the paths for each character
	 * @return String[] of the encoding paths
	 * preorder traversal of the encoding tree and finds all the path associated with each character
	 */
	private String[] treeRecursion(HuffNode root, String path, String[] encodings) {
		if(root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
		}
		treeRecursion(root.myLeft, path + "0", encodings);
		treeRecursion(root.myRight, path + "1", encodings);
		return encodings;
	}

	/**
	 * 
	 * @param counts   integer array of frequencie with the character as the index
	 * @return root of the created trie
	 * creates a HuffNode for every nonzero frequency entry, adds it to a priority queue
	 * repeatedly creates a new tree from the two smallest weight nodes, summing their weights
	 * until finally returning one node with all the subtrees
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int k = 0; k < counts.length; k++) {
			if(counts[k] == 0) continue;
			pq.add(new HuffNode(k, counts[k], null, null));	//creates a priority queue with every nonzero frequency
		}
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);	//create a new tree with summed weights
			pq.add(t);
		}
		return pq.remove(); //return the last node, the root of the created tree
	}

	/**
	 * @param in   Bit input stream
	 * @return integer array with index = character and value of character frequency
	 * creates and returns an array of character frequencies
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] all = new int[ALPH_SIZE+1];
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1) break;
			all[bits] += 1;
		}
		all[PSEUDO_EOF] = 1;
		return all;
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
			int letterBits = in.readBits(BITS_PER_WORD + 1);	//takes 9 bits assigned to the character stored in the node - 9 because possibly 257 cuz of PSEUDO character
			return new HuffNode(letterBits, 0, null, null);		//returns a leaf node
		}
	}
}
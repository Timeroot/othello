import java.io.PrintStream;
import java.util.*;

public class MerchaPlayer
	implements OthelloPlayer
{
	private OthelloSide side;
	private OthelloSide opponentSide;
	
	private OthelloBoard board = new OthelloBoard();
	private Random rand = new Random();
	
	int MONTE_RUNS = 14;
	float INFLATION_FACTOR = 2.0f;
	float SCORE_WEIGHT = 1.5f;
	float WINS_WEIGHT = 0.4f;
	float REPROX_WEIGHT = 5f+INFLATION_FACTOR*1.1f;
	boolean INIT_INFLATE = true;
	int ALPHA_DEPTH = 3;
	boolean PASS_INFLATE = false;
	boolean ADD_DEPTH = true;
	
	boolean initialized = false;
	
	public void init(OthelloSide paramOthelloSide)
	{
		this.side = paramOthelloSide;
		if (paramOthelloSide == OthelloSide.BLACK) {
			this.opponentSide = OthelloSide.WHITE;
		} else {
			this.opponentSide = OthelloSide.BLACK;
		}
	}
	
	public Move doMove(Move paramMove, long paramLong)
	{
		if(!initialized){
			if(paramLong > 30000){
				MONTE_RUNS = 40;
				INFLATION_FACTOR = 2.0f;
				SCORE_WEIGHT = 1.5f;
				WINS_WEIGHT = 0.4f;
				REPROX_WEIGHT = 5f+INFLATION_FACTOR*1.1f;
				INIT_INFLATE = true;
				ALPHA_DEPTH = 4;
				PASS_INFLATE = true;
				ADD_DEPTH = true;
			} else {
				MONTE_RUNS = 14;
				INFLATION_FACTOR = 2.0f;
				SCORE_WEIGHT = 1.5f;
				WINS_WEIGHT = 0.4f;
				REPROX_WEIGHT = 5f+INFLATION_FACTOR*1.1f;
				INIT_INFLATE = true;
				ALPHA_DEPTH = 3;
				PASS_INFLATE = false;
				ADD_DEPTH = true;
			}
			initialized = true;
			System.out.println("MERCHA IS "+this.side+" {"+MONTE_RUNS+","+INFLATION_FACTOR+","+SCORE_WEIGHT+","+WINS_WEIGHT+","+REPROX_WEIGHT+","+INIT_INFLATE+","+ALPHA_DEPTH+","+PASS_INFLATE+","+ADD_DEPTH+"}");
		}
		
		
		if (paramMove != null) {
			this.board.move(paramMove, this.opponentSide);
		}
		
		Move res = chooseMove(paramLong);
		if(res!=null)
			this.board.move(res, this.side);
		return res;
	}
	
	private Move abBestMove;
	private Move chooseMove(long paramLong){
		LinkedList<Move> moveList = getMoveList(this.board, this.side);
		if(moveList.size() == 0){
			System.out.println("PASS");
			return null;
		}
		if(moveList.size()==1){
			return moveList.get(0);
		}
		
		/*
		HashMap<Long,OthelloBoard> transMap = new HashMap<>();
		visited=0;
		genPositions(4,this.board,transMap,this.side);
		System.out.println("Produced "+transMap.size()+" of "+visited);
		*/
		
		int pieces = this.board.taken.cardinality();
		
		int dDepth = 0;
		if(paramLong < 5000){
			INFLATION_FACTOR = 1.0f;
			dDepth++;
		}
		
		if(pieces <= 46){
			float confidence = alphaBeta(ALPHA_DEPTH-dDepth, this.board, this.side, -1e30f, 1e30f, 1f, true, INIT_INFLATE, ADD_DEPTH);
			//System.out.println("Expected = "+confidence);
			return abBestMove;
		} else {
			int tEstimate = nPositions(6,this.board,this.side,0);
			System.out.println("n="+tEstimate+", t="+paramLong);
			
			float confidence;
			
			if(tEstimate <= 80000 && paramLong > 10000){
				confidence = solve(this.board, this.side, -1e30f, 1e30f, true, 0, false);
				System.out.println("[M]Solve A");
			}else if(pieces > 52){
				confidence = solve(this.board, this.side, -1e30f, 1e30f, true, 0, false);
				System.out.println("[M]Solve B");
			}else{
				confidence = alphaBeta(ALPHA_DEPTH-dDepth, this.board, this.side, -1e30f, 1e30f, 1f, true, INIT_INFLATE, ADD_DEPTH);
				System.out.println("[M]Solve C");
			}
			
			System.out.println("[M]Expected = "+confidence);
			return abBestMove;
		}

	}
	
	//Evaluate the board in favor of [side], if [side] were to move right now.
	//Alpha is current lower bound, Beta is current upper bound. (for [side].)
	//Inflation is how many times extra runs to do.
	private float alphaBeta(int depth, OthelloBoard board, OthelloSide side, float alpha, float beta, float inflation, boolean writeBest, boolean doReprox, boolean addLayer){
		if(depth==0){
			return evaluateBoard(board, side, inflation);
		}
		
		LinkedList<Move> moveList = getMoveList(board, side);
		//if(depth==3)
		//	System.out.println("AB on "+moveList.size()+" options.");
		if(moveList.size() == 0){//I can't move
			if(writeBest){
				abBestMove = null;
			}
			if(getMoveList(board, side.opposite()).size()==0){ //Neither can they, see who won
				return winnerVal(board, side);
			} else {
				return -alphaBeta(depth-1, board, side.opposite(), -beta, -alpha, inflation, false, false, addLayer); //Try the board, unchanged
			}
		}
		
		int n = moveList.size();
		float[] ordValues = new float[n];
		float origAlpha = alpha;//Unmodified, for the reevaluation
		
		float b = -1e30f;
		int loc = 0;
		Move bestMove = null;
		for(Move move : moveList){
			OthelloBoard localOthelloBoard = board.copy();
			localOthelloBoard.move(move, side);
			float j = -alphaBeta(depth-1, localOthelloBoard, side.opposite(), -beta, -alpha, inflation, false, false, false);
			
			ordValues[loc++] = j;
			if(j > alpha)
				alpha = j;
			if (j > b){
				b = j;
				bestMove = move;
			}
			if(beta <= alpha){
				if(!doReprox)
					break;
			}
		}
		
		if(loc<=1 || !doReprox){ //We only actually got one thing out before it ended up superfluous.
			if(writeBest){
				if(bestMove==null)
					try{
					 new RuntimeException();
					}catch(RuntimeException re){
					 re.printStackTrace();
					}
				abBestMove = bestMove;
			}
			return b;
		}
		
		alpha = origAlpha;
		//Now we know there's more than one option, do a runoff between the top two.
		float[] sortValues = ordValues.clone();
		Arrays.sort(sortValues);
		
		int move0 = -1;
		int move1 = -1;
		for(int i=0;i<n;i++)
			if(ordValues[i]==sortValues[n-1])
				move0=i;
		for(int i=0;i<n;i++)
			if((ordValues[i]==sortValues[n-2])&&(i!=move0))
				move1=i;
		//move0 should now have the best move, and move1 should have the second best.
		
		//Now we'll redo them, with twice the accuracy:
		
		//The double-checking is done with an extra alpha-beta step.
		if(addLayer)
			depth++;
		
		float score0;
		{
			Move move = moveList.get(move0);
			OthelloBoard localOthelloBoard = board.copy();
			localOthelloBoard.move(move, side);
			score0 = -alphaBeta(depth-1, localOthelloBoard, side.opposite(), -beta, -alpha, inflation*INFLATION_FACTOR, false, PASS_INFLATE, false);
			score0 = (REPROX_WEIGHT*score0+0*sortValues[n-1])/(0+REPROX_WEIGHT);
		}
		
		float score1;
		{
			Move move = moveList.get(move1);
			OthelloBoard localOthelloBoard = board.copy();
			localOthelloBoard.move(move, side);
			score1 = -alphaBeta(depth-1, localOthelloBoard, side.opposite(), -beta, -alpha, inflation*INFLATION_FACTOR, false, PASS_INFLATE, false);
			score1 = (REPROX_WEIGHT*score1+0*sortValues[n-2])/(0+REPROX_WEIGHT);
		}
		
		if(depth==ALPHA_DEPTH){
			//System.out.println("Adjusted ("+sortValues[n-1]+","+sortValues[n-2]+") to ("+score0+", "+score1+")");
			//System.out.println((score1>score0) ? "Y" : "N");
		}
		
		if(score1 > score0){
			//The reevaluation indicates that our initial answer was wrong.
			score0 = score1;
			bestMove = moveList.get(move1);
		} else {
			bestMove = moveList.get(move0);
		}
		
		if(writeBest)
			abBestMove = bestMove;
		return score0;
	}
	
	OthelloBoard[] tempBoards = new OthelloBoard[80];
	{
		for(int i=0;i<tempBoards.length;i++){
			tempBoards[i] = new OthelloBoard();
		}
	}
	
	private float solve(OthelloBoard board, OthelloSide side, float alpha, float beta, boolean writeBest, int tBoard, boolean hard){
		LinkedList<Move> moveList = getMoveList(board, side);
		if(moveList.size() == 0){//I can't move
			if(writeBest){
				abBestMove = null;
			}
			if(getMoveList(board, side.opposite()).size()==0){ //Neither can they, see who won
				return (hard ? winnerPlain(board, side) : winnerVal(board, side));
			} else {
				return -solve(board, side.opposite(), -beta, -alpha, false, tBoard+1, hard); //Try the board, unchanged
			}
		}
		
		OthelloBoard tempBoard = tempBoards[tBoard];
		
		float b = -1e30f;
		Move bestMove = null;
		for(Move move : moveList){
			copyBoard(board, tempBoard);
			tempBoard.move(move, side);
			float j = -solve(tempBoard, side.opposite(), -beta, -alpha, false, tBoard+1, hard);
			if(j > alpha)
				alpha = j;
			if (j > b){
				b = j;
				bestMove = move;
			}
			if(beta <= alpha){
				break;
			}
		}
		if(writeBest)
			abBestMove = bestMove;
		return b;
	}
	
	int visited = 0;
	private void genPositions(int depth, OthelloBoard board, HashMap<Long,OthelloBoard> dest, OthelloSide side)
	{
		if (depth == 0) {
			visited++;
			//System.out.println("GEN: "+hashBoard(board));
			dest.put(hashBoard(board),board);
			return;
		}

		for(Move localMove : getMoveList(board, side)){
				OthelloBoard modBoard = board.copy();
				modBoard.move(localMove, side);
				genPositions(depth-1,modBoard,dest,side.opposite());
		}
	}
	
	private int nPositions(int depth, OthelloBoard board, OthelloSide side, int tBoard)
	{
		if (depth == 0) {
			return 1;
		}
		if( depth == 1){
			return getMoveList(board, side).size();
		}

		int sum=0;
		OthelloBoard tempBoard = tempBoards[tBoard];
		for(Move localMove : getMoveList(board, side)){
			copyBoard(board, tempBoard);
			tempBoard.move(localMove, side);
			sum+=nPositions(depth-1,tempBoard,side.opposite(),tBoard+1);
		}
		return sum;
	}
	
	private long hashBoard(OthelloBoard board){
		long hash = 1;
		for (int j = 0; j <= 7; j++) {
			for (int k = 0; k <= 7; k++) {
				if (board.get(this.side, j, k)) {
					hash *= 5;
				} else if (board.get(this.opponentSide, j, k)) {
					hash *= 9;
				} else {
					hash *= 17;
				}
				hash++;
			}
		}
		return hash;
	}
	
	private OthelloBoard randomCopy = new OthelloBoard();
	//Evaluate a position using Monte-Carlo, with value for mySide, assuming it's mySide next.
	private float evaluateBoard(OthelloBoard board, OthelloSide mySide, float inflation)
	{
		int ADJ_RUNS = (int)(MONTE_RUNS*inflation);
		//System.out.println("Run @ "+ADJ_RUNS);
		
		int accScore = 0;
		int accSquare = 0;
		int wins = 0;
		
		for(int run = 0; run < ADJ_RUNS; run++){
			copyBoard(board, randomCopy);
			boolean lastPass = false, currPass = false;
			OthelloSide toMove = mySide;
			while(!(lastPass && currPass)){
				lastPass = currPass;
				
				Move nextMove = getRandomMove(randomCopy, toMove);
				if(nextMove==null){
					currPass = true;
				} else {
					currPass = false;
					randomCopy.move(nextMove, toMove);
				}
				toMove = toMove.opposite();
			}
			
			//ASSERT
			/*if(!randomCopy.isDone()){
				throw new RuntimeException("Two passes but not done?!");
			}*/
			
			int differential = (randomCopy.countBlack() - randomCopy.countWhite());
			accScore += differential;
			accSquare += differential*differential;
			wins += (differential > 0 ? 1 : (differential < 0 ? -1 : 0));
		}
		
		accScore *= (mySide == OthelloSide.BLACK ? 1 : -1);
		wins *= (mySide == OthelloSide.BLACK ? 1 : -1);
		
		float adjScore = (float)(accScore)/ADJ_RUNS;
		float stdDev = (float)Math.sqrt((float)(accSquare)/ADJ_RUNS - adjScore*adjScore);
		float adjWins = (float)(wins)/ADJ_RUNS;
		
		adjScore = WINS_WEIGHT*adjWins + SCORE_WEIGHT*adjScore;
		
		//System.out.println("[A] Got an average of "+adjScore+", "+wins+" wins, stddev "+stdDev+" from "+((float)(accSquare)/MONTE_RUNS));
		
		return adjScore;
	}
	
	private float winnerVal(OthelloBoard board, OthelloSide side){
		return (board.countBlack() - board.countWhite()) * (side == OthelloSide.BLACK ? 1 : -1) * 1e10f;
	}
	
	private int winnerPlain(OthelloBoard board, OthelloSide side){
		int differential = board.countBlack() - board.countWhite();
		return (differential > 0 ? 1 : (differential < 0 ? -1 : 0)) * (side == OthelloSide.BLACK ? 1 : -1);
	}
	
	private void copyBoard(OthelloBoard src, OthelloBoard dest){
		dest.taken.clear();
		dest.black.clear();
		dest.taken.or(src.taken);
		dest.black.or(src.black);
	}
	
	private Move getRandomMove(OthelloBoard board, OthelloSide paramOthelloSide){
		int startI = rand.nextInt(8);
		int startJ = rand.nextInt(8);
		
		for (int j = startJ; j <= 7; j++){
			if (checkMove(startI, j, paramOthelloSide, board)) {
				return new Move(startI, j);
			}
		}
		
		for (int i = startI+1; i <= 7; i++) {
			for (int j = 0; j <= 7; j++) {
				if (checkMove(i, j, paramOthelloSide, board)) {
					return new Move(i, j);
				}
			}
		}
		
		for (int i = 0; i < startI; i++) {
			for (int j = 0; j <= 7; j++) {
				if (checkMove(i, j, paramOthelloSide, board)) {
					return new Move(i, j);
				}
			}
		}
		
		for (int j = 0; j < startJ; j++){
			if (checkMove(startI, j, paramOthelloSide, board)) {
				return new Move(startI, j);
			}
		}
		
		return null;
	}
	
	private LinkedList<Move> getMoveList(OthelloBoard board, OthelloSide paramOthelloSide)
	{
		LinkedList<Move> localLinkedList = new LinkedList<Move>();
		for (int i = 0; i <= 7; i++) {
			for (int j = 0; j <= 7; j++)
			{
				if (checkMove(i, j, paramOthelloSide, board)) {
					localLinkedList.add(new Move(i,j));
				}
			}
		}
		return localLinkedList;
	}
	
	private boolean checkMove(int X, int Y, OthelloSide turn, OthelloBoard board) {
      // Make sure the square hasn't already been taken.
      if(board.occupied(X, Y))
         return false;

      OthelloSide other = turn.opposite();
      for (int dx = -1; dx <= 1; dx++) {
         for (int dy = -1; dy <= 1; dy++) {
            //for each direction
            if (dy == 0 && dx == 0)
               continue;

            //is there a capture in that direction?
            int x = X + dx;
            int y = Y + dy;
            if (board.onBoard(x,y) && board.get(other,x,y)) {
               do {
                  x += dx;
                  y += dy;
               } while (board.onBoard(x,y) && board.get(other,x,y));
               if (board.onBoard(x,y) && board.get(turn,x,y)) {
                  return true;
               }
            }
         }
      }
      return false;
   }
}

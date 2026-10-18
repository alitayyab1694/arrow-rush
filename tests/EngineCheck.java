import com.revolution.arrowrush.PuzzleEngine;
import java.util.*;

public class EngineCheck {
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    public static void main(String[] args) {
        int verified=0, moves=0;
        for(int n=1;n<=3000;n++) {
            PuzzleEngine.Level level=PuzzleEngine.generate(n);
            check(level.pieces.size()>0,"empty level " + n);
            Set<String> used=new HashSet<>();
            for(PuzzleEngine.Piece p:level.pieces) check(used.add(p.row+":"+p.col),"duplicate cell " + n);
            List<Integer> solution=PuzzleEngine.solve(level);
            check(solution!=null,"unsolvable level " + n);
            check(solution.size()==level.pieces.size(),"incomplete solver " + n);
            List<PuzzleEngine.Piece> board=new ArrayList<>(level.pieces);
            for(Integer id:solution) {
                PuzzleEngine.Piece chosen=null;
                for(PuzzleEngine.Piece p:board)if(p.id==id)chosen=p;
                check(chosen!=null&&PuzzleEngine.canExit(chosen,board),"illegal win path on " + n);
                board.remove(chosen);
                moves++;
            }
            check(board.isEmpty(),"not empty " + n);
            // Validate the unique Pulse Chain mechanic is actually achievable
            // during a legal winning playthrough of EVERY generated level.
            board=new ArrayList<>(level.pieces);
            int triggered=0;
            for(int id=level.pieces.size()-1;id>=0;id--) {
                PuzzleEngine.Piece chosen=null;
                for(PuzzleEngine.Piece p:board)if(p.id==id)chosen=p;
                if(chosen==null)continue; // earlier chain may have cleared it
                check(PuzzleEngine.canExit(chosen,board),"invalid backward proof path on " + n);
                board.remove(chosen);
                if(!PuzzleEngine.pulseChain(chosen,board).isEmpty())triggered++;
            }
            check(board.isEmpty(),"chain playthrough unfinished " + n);
            check(triggered>=1,"no Pulse Chain achievable on level " + n);
            // Simulate a player choosing any permitted move, with occasional pulse clears.
            board=new ArrayList<>(level.pieces);
            Random rand=new Random(n);
            while(!board.isEmpty()) {
                ArrayList<PuzzleEngine.Piece> legal=new ArrayList<>();
                for(PuzzleEngine.Piece p:board)if(PuzzleEngine.canExit(p,board))legal.add(p);
                check(!legal.isEmpty(),"deadlock after legal moves on " + n);
                PuzzleEngine.Piece chosen=legal.get(rand.nextInt(legal.size()));
                board.remove(chosen);
                PuzzleEngine.pulseChain(chosen,board);
            }
            check(PuzzleEngine.generate(n).pieces.size()==level.pieces.size(),"not deterministic " + n);
            verified++;
        }
        PuzzleEngine.Level daily=PuzzleEngine.generate(20346,true);
        check(PuzzleEngine.solve(daily)!=null,"daily not solvable");
        // A 4-arrow cycle must be identified as unsolvable.
        List<PuzzleEngine.Piece> impossible=Arrays.asList(
            new PuzzleEngine.Piece(1,0,0,PuzzleEngine.Direction.RIGHT,0,false),
            new PuzzleEngine.Piece(2,0,1,PuzzleEngine.Direction.DOWN,0,false),
            new PuzzleEngine.Piece(3,1,1,PuzzleEngine.Direction.LEFT,0,false),
            new PuzzleEngine.Piece(4,1,0,PuzzleEngine.Direction.UP,0,false));
        check(PuzzleEngine.solvePieces(impossible)==null,"failed to reject an actual cycle");
        System.out.println("PASS: "+verified+" deterministic levels; "+moves+" verified legal moves; randomized win paths + invalid-cycle rejection + daily puzzle.");
    }
}

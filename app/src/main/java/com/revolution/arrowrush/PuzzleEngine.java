package com.revolution.arrowrush;

import java.util.*;

/** Android-free, deterministic puzzle engine. All published boards are built backwards from a solution. */
public final class PuzzleEngine {
    private PuzzleEngine() {}
    public enum Direction { UP, RIGHT, DOWN, LEFT }
    public enum Tier { EASY, MEDIUM, HARD, EXPERT }

    public static final class Piece {
        public final int id, row, col, color;
        public final Direction direction;
        public final boolean pulse;
        public Piece(int id, int row, int col, Direction direction, int color, boolean pulse) {
            this.id=id; this.row=row; this.col=col; this.direction=direction; this.color=color; this.pulse=pulse;
        }
    }
    public static final class Level {
        public final int number, rows, cols;
        public final Tier tier;
        public final List<Piece> pieces;
        public final boolean daily;
        Level(int number,int rows,int cols,Tier tier,List<Piece> pieces,boolean daily) {
            this.number=number; this.rows=rows; this.cols=cols; this.tier=tier;
            this.pieces=Collections.unmodifiableList(new ArrayList<>(pieces)); this.daily=daily;
        }
    }

    public static Tier tierFor(int level) {
        int offset=(Math.max(1,level)-1)%12;
        if(offset<3) return Tier.EASY;
        if(offset<7) return Tier.MEDIUM;
        if(offset<11) return Tier.HARD;
        return Tier.EXPERT;
    }

    public static Level generate(int number) { return generate(number, false); }
    public static Level generate(int number, boolean daily) {
        Tier tier=daily ? Tier.HARD : tierFor(number);
        int rows = tier==Tier.EASY ? 5 : tier==Tier.MEDIUM ? 6 : 7;
        int cols = rows;
        int want = tier==Tier.EASY ? 10+Math.floorMod(number,4) :
                   tier==Tier.MEDIUM ? 20+Math.floorMod(number,5) :
                   tier==Tier.HARD ? 30+Math.floorMod(number,5) : 40;
        long seed=0x1BE5DCA65L ^ (((long)number)*0x9E3779B97F4A7C15L) ^ (daily?0x4441494C59L:0L);
        for(int attempt=0;attempt<30;attempt++) {
            Random rng=new Random(seed + attempt*173L);
            List<Integer> cells=new ArrayList<>();
            for(int i=0;i<rows*cols;i++) cells.add(i);
            Collections.shuffle(cells,rng);
            cells=new ArrayList<>(cells.subList(0,want));
            // Diagonal sweep from a randomly chosen corner ensures every selected
            // position has at least one clear direction at insertion time.
            final boolean flipRows=rng.nextBoolean(), flipCols=rng.nextBoolean();
            Collections.shuffle(cells,rng); // randomize positions tied on diagonal
            cells.sort(Comparator.comparingInt(cell ->
                (flipRows ? rows-1-cell/cols : cell/cols) +
                (flipCols ? cols-1-cell%cols : cell%cols)));
            List<Piece> placed=new ArrayList<>();
            for(int cell : cells) {
                int row=cell/cols,col=cell%cols;
                List<Direction> directions=new ArrayList<>();
                for(Direction direction:Direction.values())
                    if(clearRay(row,col,direction,placed)) directions.add(direction);
                if(directions.isEmpty()) throw new IllegalStateException("Generator sweep invariant broken");
                Direction direction=directions.get(rng.nextInt(directions.size()));
                placed.add(new Piece(placed.size(),row,col,direction,rng.nextInt(4),false));
            }
            // Seed at least one intended chain for each pulse in the proof path:
            // after all larger IDs exit, a matching smaller arrow is also legal.
            List<Piece> finalPieces=new ArrayList<>(placed);
            for(int id=7;id<finalPieces.size();id+=7) {
                Piece pulsePiece=finalPieces.get(id);
                List<Piece> earlier=new ArrayList<>(finalPieces.subList(0,id));
                List<Piece> clearEarlier=new ArrayList<>();
                for(Piece p:earlier)if(canExit(p,earlier))clearEarlier.add(p);
                if(!clearEarlier.isEmpty()) {
                    Piece target=clearEarlier.get(rng.nextInt(clearEarlier.size()));
                    finalPieces.set(id,new Piece(id,pulsePiece.row,pulsePiece.col,pulsePiece.direction,pulsePiece.color,true));
                    finalPieces.set(target.id,new Piece(target.id,target.row,target.col,target.direction,pulsePiece.color,target.pulse));
                }
            }
            Collections.shuffle(finalPieces,rng);
            Level candidate=new Level(number,rows,cols,tier,finalPieces,daily);
            if(solve(candidate)!=null) return candidate;
        }
        throw new IllegalStateException("Failed to generate verified level " + number);
    }

    public static boolean clearRay(int row,int col,Direction dir,Collection<Piece> pieces) {
        for(Piece p: pieces) {
            if(p.row==row&&p.col==col) continue;
            switch(dir) {
                case UP: if(p.col==col&&p.row<row)return false; break;
                case RIGHT: if(p.row==row&&p.col>col)return false; break;
                case DOWN: if(p.col==col&&p.row>row)return false; break;
                case LEFT: if(p.row==row&&p.col<col)return false; break;
            }
        }
        return true;
    }
    public static boolean canExit(Piece p,Collection<Piece> pieces) {
        return clearRay(p.row,p.col,p.direction,pieces);
    }

    /** DFS with memoization; returns a legitimate win sequence or null for unsolvable boards. */
    public static List<Integer> solve(Level level) { return solvePieces(level.pieces); }
    public static List<Integer> solvePieces(List<Piece> pieces) {
        if(pieces.size()>60) throw new IllegalArgumentException("At most 60 pieces supported");
        int n=pieces.size();
        long[] blockers=new long[n];
        for(int i=0;i<n;i++) {
            Piece p=pieces.get(i);
            for(int j=0;j<n;j++) {
                if(i==j)continue;
                Piece q=pieces.get(j);
                if((p.direction==Direction.UP&&q.col==p.col&&q.row<p.row)||
                   (p.direction==Direction.RIGHT&&q.row==p.row&&q.col>p.col)||
                   (p.direction==Direction.DOWN&&q.col==p.col&&q.row>p.row)||
                   (p.direction==Direction.LEFT&&q.row==p.row&&q.col<p.col)) blockers[i] |= 1L<<j;
            }
        }
        long remaining=(1L<<n)-1L;
        ArrayList<Integer> sequence=new ArrayList<>();
        if(dfs(remaining,blockers,pieces,sequence,new HashSet<>())) return sequence;
        return null;
    }
    private static boolean dfs(long remaining,long[] blockers,List<Piece> pieces,List<Integer> sequence,Set<Long> bad) {
        if(remaining==0L) return true;
        if(!bad.add(remaining)) return false;
        // Low branching cost: viable moves are free in their own arrow direction.
        for(int i=0;i<pieces.size();i++) {
            long bit=1L<<i;
            if((remaining&bit)!=0L&&(remaining&blockers[i])==0L) {
                sequence.add(pieces.get(i).id);
                if(dfs(remaining&~bit,blockers,pieces,sequence,bad)) return true;
                sequence.remove(sequence.size()-1);
            }
        }
        return false;
    }
    public static Piece hint(List<Piece> remaining) {
        List<Integer> solution=solvePieces(remaining);
        if(solution==null||solution.isEmpty()) return null;
        int id=solution.get(0);
        for(Piece p:remaining) if(p.id==id)return p;
        return null;
    }
    public static List<Piece> pulseChain(Piece source,List<Piece> remaining) {
        List<Piece> chain=new ArrayList<>();
        if(!source.pulse) return chain;
        ArrayList<Piece> candidate=new ArrayList<>(remaining);
        candidate.sort(Comparator.comparingInt(p->Math.abs(p.row-source.row)+Math.abs(p.col-source.col)));
        for(Piece p:candidate) {
            if(chain.size()==2)break;
            if(p.color==source.color&&canExit(p,remaining)) {
                chain.add(p);
                remaining.remove(p);
            }
        }
        return chain;
    }
}

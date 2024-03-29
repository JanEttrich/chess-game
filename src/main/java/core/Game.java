package core;

import core.move.Move;
import core.move.MoveMaker;
import core.move.MoveTracker;
import core.pieces.King;
import core.pieces.Rook;
import lombok.Getter;
import lombok.Setter;
import util.FenStringReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Game {

    private static final String START_POS_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";
    @Getter
    protected Board board;
    protected final Player playerWhite;
    protected final Player playerBlack;
    private final Random random = new Random();
    @Getter
    @Setter
    protected Player activePlayer;

    public Game(String posFen, boolean humanOpponent) {
        this.board = new Board();
        this.playerWhite = new Player(true, true);
        this.playerBlack = new Player(false, humanOpponent);
        this.activePlayer = playerWhite;
        if (posFen == null) {
            initStartingPosition();
        } else {
            initPositionFromFen(posFen);
        }
        GameState.resetGameState(playerWhite, playerBlack);
        MoveTracker.resetMoves();
    }

    // TODO: Draw by repetition
    // TODO: Draw by 50 move rule

    public void initStartingPosition() {
        FenStringReader.read(START_POS_FEN, board);
    }

    public void initPositionFromFen(String fen) {
        FenStringReader.read(fen, board);
    }

    public boolean canPlayerMove() {
        return !generate().isEmpty();
    }

    // Returns true if both players only have a king left
    public boolean isInsufficientMaterial() {
        return checkInsufficientMaterialForPlayer(true) && checkInsufficientMaterialForPlayer(false);
    }

    public boolean checkInsufficientMaterialForPlayer(boolean white) {
        int knightCount = 0;
        int bishopCount = 0;
        var piecePositions = board.getAllPiecePositionsOfPlayer(white);
        if (piecePositions.size() > 2) {
            return false;
        }
        for (Square square : piecePositions) {
            if (square.getPiece().getDisplay().equalsIgnoreCase("N")) {
                knightCount += 1;
            } else if (square.getPiece().getDisplay().equalsIgnoreCase("B")) {
                bishopCount += 1;
            } else if (!square.getPiece().getDisplay().equalsIgnoreCase("K")) {
                return false;
            }
        }
        return knightCount + bishopCount <= 1;
    }

    // Returns all legal moves of a player
    public List<Move> generate() {
        List<Move> pseudoLegalMoves = generatePseudoLegalMoves(activePlayer.isWhite());
        return filterMoves(pseudoLegalMoves);
    }

    public Move makeRandomMove() {
        List<Move> legalMoves = generate();
        int randomIndex = random.nextInt(legalMoves.size());

        Move randomMove = legalMoves.get(randomIndex);
        MoveMaker.makeMove(randomMove, activePlayer.isWhite(), board);
        updateCastlingRights(randomMove);
        activePlayer = activePlayer == playerWhite ? playerBlack : playerWhite;
        return randomMove;
    }

    public List<Move> filterMoves(List<Move> pseudoLegalMoves) {
        List<Move> legalMoves = new ArrayList<>();
        for (Move move : pseudoLegalMoves) {
            // do not allow king to castle through check
            if ((Boolean.TRUE.equals(move.getCastleShort()) || Boolean.TRUE.equals(move.getCastleLong())) &&
                    threatenedOrCastlesThroughCheck(move)) {
                continue;
            }
            MoveMaker.makeMove(move, activePlayer.isWhite(), board);
            if (!canKingCanBeCaptured()) {
                legalMoves.add(move);
            }
            MoveMaker.unmakeMove(move, activePlayer.isWhite(), board);
        }
        return legalMoves;
    }

    public boolean canKingCanBeCaptured() {
        // get all possible moves of opponent
        List<Move> pseudoLegalMoves = generatePseudoLegalMoves(!activePlayer.isWhite());
        for (Move move : pseudoLegalMoves) {
            if (move.getEndSquare().isOccupied() && move.getEndSquare().getPiece().getDisplay().equalsIgnoreCase("K")) {
                // if king can be captured directly, last move was illegal, regardless of if the capturing move is illegal
                return true;
            }
        }
        return false;
    }

    private List<Move> generatePseudoLegalMoves(boolean white) {
        // get pseudo-legal moves
        List<Square> positions = board.getAllPiecePositionsOfPlayer(white);
        List<Move> pseudoLegalMoves = new ArrayList<>();
        for (Square sourceSquare : positions) {
            pseudoLegalMoves.addAll(sourceSquare.getPiece().getPseudoLegalMovesForPiece(board.getSquares(),
                    sourceSquare));
        }
        return pseudoLegalMoves;
    }

    // check if king is in check or adjacent square in direction of castle is threatened by opponent
    // target square of king is checked later
    protected boolean threatenedOrCastlesThroughCheck(Move move) {
        if (canKingCanBeCaptured()) {
            return true;
        }
        int direction = Boolean.TRUE.equals(move.getCastleShort()) ? 1 : -1;
        // check if horizontal squares are threatened by the opponents pieces
        Move moveToAdjacentSquare = new Move(move.getStartSquare(),
                board.getSquares()[move.getStartSquare().getRank()][move.getStartSquare().getFile() + direction]);

        MoveMaker.makeMove(moveToAdjacentSquare, activePlayer.isWhite(), board);
        if (canKingCanBeCaptured()) {
            MoveMaker.unmakeMove(moveToAdjacentSquare, activePlayer.isWhite(), board);
            return true;
        }
        MoveMaker.unmakeMove(moveToAdjacentSquare, activePlayer.isWhite(), board);
        return false;
    }

    public boolean updateCastlingRights(Move move) {
        Piece piece = move.getEndSquare().getPiece();
        Square sourceSquare = move.getStartSquare();

        // handle rook/king move for castling rights
        if (piece instanceof King && activePlayer.canCastleOnAtLeastOneSide()) {
            activePlayer.disallowCastle();
            return true;
        } else if (piece instanceof Rook rook) {
            if (sourceSquare.getFile() == 0 && activePlayer.isCastleLongAllowed() && !rook.isMoved()) {
                activePlayer.setCastleLongAllowed(false);
                rook.setMoved(true);
                return true;
            } else if (sourceSquare.getFile() == 7 && activePlayer.isCastleShortAllowed() && !rook.isMoved()) {
                activePlayer.setCastleShortAllowed(false);
                rook.setMoved(true);
                return true;
            }
        }
        return false;
    }

    public void unmakeCastlingRightUpdate(Move move) {
        Piece piece = move.getPiece();
        Square sourceSquare = move.getStartSquare();

        // handle rook/king move for castling rights
        if (piece instanceof King) {
            activePlayer.reAllowedCastle();
        } else if (piece instanceof Rook rook) {
            if (sourceSquare.getFile() == 0) {
                activePlayer.setCastleLongAllowed(true);
                rook.setMoved(false);
            } else if (sourceSquare.getFile() == 7) {
                activePlayer.setCastleShortAllowed(true);
                rook.setMoved(false);
            }
        }
    }
}

package Logic;

import Exceptions.EmptyDeckException;
import Exceptions.GameOverException;
import Exceptions.InvalidChoiceException;
import Exceptions.SelectionNeededException;
import Interfaces.ActionHandler;
import Interfaces.PerformActionHandler;
import Interfaces.PlayActionHandler;
import Models.Cards.Card;
import Models.Cards.Minion;
import Models.InfoPack;
import Models.Passive;

import java.util.ArrayList;
import java.util.Random;

public enum ActionRequest {
    END_TURN{
        @Override
        public void execute() throws GameOverException {
            try{
                game.changeTurn();
                super.execute();
                game.checkAll();
            }catch (GameOverException e){
                game.engGame();
                throw e;
            }
        }
    },
    DRAW_CARD{
        @Override
        public void execute() throws GameOverException {
            try {
                game.drawCard();
            } catch (EmptyDeckException e) {
                //kjshdfkjas
            }
            super.execute();
        }
    },
    PERFORM_ACTION{
        @Override
        public void execute(InfoPack[] parameters) throws SelectionNeededException, InvalidChoiceException, GameOverException {
            try {
                if(!parameters[0].isOnGround() && !(parameters[0].getCharacter() instanceof Passive)){
                    game.checkForMana((Card) parameters[0].getCharacter(), parameters[0].getSide());
                }
                executeBeforeActions(parameters);
                game.performAction(parameters);
                if(!parameters[0].isOnGround() && !(parameters[0].getCharacter() instanceof Passive)){
                    game.playCard((Card) parameters[0].getCharacter(), parameters[0].getSide());
                }
                super.execute(parameters);
                game.checkAll();
            }catch (GameOverException e){
                game.engGame();
                throw e;
            }
        }

        private void executeBeforeActions(InfoPack[] infoPacks) throws InvalidChoiceException {
            for(ActionHandler actionHandler: getBeforeActions()){
                if(actionHandler instanceof PerformActionHandler){
                    ((PerformActionHandler) actionHandler).runAction(infoPacks);
                }
                try {
                    actionHandler.runAction();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    },
    SUMMON_MINION{
        @Override
        public void execute(Card minion, int side) throws GameOverException, InvalidChoiceException {
            game.summon((Minion) minion, side);
            super.execute(minion, side);
        }
    },
    PLAY_CARD{
        @Override
        public void execute(Card card, int side) throws GameOverException, InvalidChoiceException {
            game.playCard(card, side);
            super.execute(card, side);
        }
    };

    public ArrayList<ActionHandler> getBeforeActions() {
        return beforeActions;
    }

    public void execute(Card card, int side) throws GameOverException, InvalidChoiceException {
        for (ActionHandler actionHandler: actions){
            try {
                if(actionHandler instanceof PlayActionHandler){
                    ((PlayActionHandler) actionHandler).runAction(card, side);
                }
                actionHandler.runAction();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void execute() throws GameOverException {
        for (ActionHandler actionHandler: actions){
            try {
                actionHandler.runAction();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    public void execute(InfoPack[] parameters) throws SelectionNeededException, InvalidChoiceException, GameOverException {
        for (ActionHandler actionHandler: actions){
            try {
                if (actionHandler instanceof PerformActionHandler){
                    ((PerformActionHandler) actionHandler).runAction(parameters);
                }
                actionHandler.runAction();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Game game;
    private ArrayList<ActionHandler> actions = new ArrayList<>(), beforeActions = new ArrayList<>();

    public static void setCurrentGame(Game game){
        ActionRequest.game = game;
        for(ActionRequest actionRequest: values()){
            actionRequest.actions.clear();
        }
        game.initialize();
    }

    public void addAction(ActionHandler actionHandler){
        actions.add(actionHandler);
    }

    public void addBeforeAction(ActionHandler actionHandler) {
        beforeActions.add(actionHandler);
    }
}

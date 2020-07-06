package Logic;


import Data.GameConstants;
import Exceptions.EmptyDeckException;
import Exceptions.GameOverException;
import Exceptions.InvalidChoiceException;
import Exceptions.SelectionNeededException;
import Interfaces.ActionHandler;
import Models.Cards.Card;
import Models.Cards.Minion;
import Models.InfoPack;

import java.util.ArrayList;
import java.util.Random;

public class Game {
    private Competitor[] competitor = new Competitor[2];
    private int turn, winner = 0;
    private Actions actions;
    private boolean isWithBot = false;

    public Game(Competitor competitor1, Competitor competitor2, boolean isWithBot){
        this.isWithBot = isWithBot;
        actions = new Actions(this);
        competitor[0] = competitor1;
        competitor[1] = competitor2;
        turn = 0;
    }

    public Competitor[] getCompetitors(){
        return competitor;
    }

    public void performAction(InfoPack[] parameter) throws SelectionNeededException, InvalidChoiceException, GameOverException{
        actions.performAction(parameter);
    }

    public void changeTurn() throws GameOverException {
        turn = (turn+1)%2;
        if(competitor[turn].getFullMana() < GameConstants.getInstance().getInteger("manaMax")){
            competitor[turn].setFullMana(competitor[turn].getFullMana()+1);
        }
        competitor[turn].setLeftMana(competitor[turn].getFullMana());
        for(int i = 0; i < competitor[turn].getDrawNumber(); i++){
            ActionRequest.DRAW_CARD.execute();
        }
    }

    public void playCard(Card card, int side) {
        competitor[side].playCard(card);
        competitor[side].setLeftMana(competitor[side].getLeftMana() - card.getMana());
    }

    public void checkForMana(Card card, int side) throws InvalidChoiceException {
        if(card.getMana() > competitor[side].getLeftMana()) {
            throw new InvalidChoiceException();
        }
    }

    public void setTurn(int turn){
        this.turn = turn%2;
    }

    public int getTurn(){
        return turn;
    }

    public Competitor getCompetitor(int index){
        return competitor[index%2];
    }

    public void engGame(){
        if(competitor[(turn+1)%2].getHero().getHp() <= 0){
            winner = turn;
            competitor[turn].getDeck().setWinsNumber(competitor[turn].getDeck().getWinsNumber() + 1);
        }
        else {
            winner = (turn+1)%2;
            competitor[(turn+1)%2].getDeck().setWinsNumber(competitor[(turn+1)%2].getDeck().getWinsNumber() + 1);
        }
        competitor[0].getDeck().setPlaysNumber(competitor[0].getDeck().getPlaysNumber() + 1);
        competitor[1].getDeck().setPlaysNumber(competitor[1].getDeck().getPlaysNumber() + 1);
        turn = -1;
    }

    public int getWinner() {
        return winner;
    }

    public void drawCard() throws GameOverException, EmptyDeckException {
        try {
            competitor[turn].drawCard();
        } catch (EmptyDeckException e) {
            competitor[turn].damageToHero(GameConstants.getInstance().getInteger("DeckEmptyLifeDecrease"));
            throw new EmptyDeckException();
        }
    }

    public void summon(Minion minion, int side) {
        if(competitor[side].getOnBoardCards().size() < GameConstants.getInstance().getInteger("groundMaxCard")){
            competitor[side].addCardOnBoard(minion);
        }
    }

    public boolean isWithBot() {
        return isWithBot;
    }

    public void checkAll() {
        competitor[0].runQuestRewards();
        competitor[1].runQuestRewards();
        for(int i = 0; i < 2; i++){
            try {
                if(competitor[i].getHeroWeapon().getDurability() <= 0){
                    competitor[i].setHeroWeapon(null);
                }
            } catch (NullPointerException e){}
            for(int j = 0; j < competitor[i].getOnBoardCards().size(); j++){
                Minion minion = competitor[i].getOnBoardCards().get(j);
                if(minion.getHp() <= 0){
                    competitor[i].getOnBoardCards().remove(minion);
                    j--;
                }
                else {
                    minion.setRush(false);
                    minion.setCharge(true);
                }
            }
            competitor[i].getHero().getHeroPower().setCharge(true);
        }
    }

    public void initialize() {
        setForPaladin();
    }

    private void setForPaladin() {
        for(int i = 0; i < 2; i++){
            if(competitor[i].getHero().getName().equals("Paladin")){
                int finalI = i;
                ActionRequest.END_TURN.addAction(new ActionHandler() {
                    @Override
                    public void runAction() throws Exception {
                        if(turn != finalI){
                            Random random = new Random();
                            ArrayList<Minion> minions = competitor[finalI].getOnBoardCards();
                            if(minions.size() > 0){
                                Minion minion = minions.get(random.nextInt(minions.size()));
                                minion.setHp(minion.getHp() + 1);
                                minion.setAttack(minion.getAttack() + 1);
                            }
                        }
                    }
                });
            }
        }
    }
}
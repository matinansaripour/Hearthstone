package Graphics;

import Data.*;
import Exceptions.EmptyDeckException;
import Exceptions.GameOverException;
import Exceptions.InvalidChoiceException;
import Exceptions.SelectionNeededException;
import Interfaces.ActionHandler;
import Log.LogCenter;
import Logic.ActionRequest;
import Logic.Game;
import Models.Cards.Card;
import Models.Cards.Minion;
import Logic.PlayersManager;
import Logic.Competitor;
import Models.Character;
import Models.InfoPack;
import Models.Passive;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.apache.log4j.Logger;

import java.awt.event.WindowStateListener;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.ResourceBundle;

public class BattleGroundController implements Initializable {
    @FXML
    private StackPane root;
    @FXML
    private Pane rootPane;
    @FXML
    private HBox battleGround1;
    @FXML
    private HBox battleGround2;
    private HBox[] battleGround = new HBox[2];
    @FXML
    private Label manaText1;
    @FXML
    private Label manaText2;
    private Label[] manaText = new Label[2];
    @FXML
    private HBox manaBar;
    @FXML
    private Pane hand1;
    @FXML
    private Pane hand2;
    private Pane[] hand = new Pane[2];
    @FXML
    private Label cardsNumberLabel1;
    @FXML
    private Label cardsNumberLabel2;
    private Label[] cardsNumberLabel = new Label[2];
    @FXML
    private StackPane heroPowerPlace1;
    @FXML
    private StackPane heroPowerPlace2;
    private StackPane[] heroPowerPlace = new StackPane[2];
    @FXML
    private StackPane heroWeapon1;
    @FXML
    private StackPane heroWeapon2;
    private StackPane[] heroWeapon = new StackPane[2];
    @FXML
    private ImageView arena;
    @FXML
    private GridPane gameLogGridPane;
    private Parent selected = null;
    private Pane[] hero = new Pane[2];
    private Game game = null;
    private ArrayList<InfoPack> infoPacks = new ArrayList<>();
    Object lock = new Object();

    @FXML
    private void exit(){
        try {
            PlayersManager.getInstance().getCurrentPlayer().saveData();
            LogCenter.getInstance().getLogger().info("exit");
        } catch (Exception ex) {
            System.out.println("exit on login page.");;
        }
        System.exit(0);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        renderPassives();
        battleGround[0] = battleGround1;
        battleGround[1] = battleGround2;
        hand[0] = hand1;
        hand[1] = hand2;
        manaText[0] = manaText1;
        manaText[1] = manaText2;
        cardsNumberLabel[0] = cardsNumberLabel1;
        cardsNumberLabel[1] = cardsNumberLabel2;
        heroPowerPlace[0] = heroPowerPlace1;
        heroPowerPlace[1] = heroPowerPlace2;
        heroWeapon[0] = heroWeapon1;
        heroWeapon[1] = heroWeapon2;
        arena.setImage(AssetManager.getInstance().getImage(GameSettings.getInstance().getBattleGroundArena()));
    }

    public void setGame(Game game){
        this.game = game;
    }

    public synchronized void gameRender() {
        GraphicRender graphicRender = GraphicRender.getInstance();
        for(int i = 0; i < 2; i++){
            rootPane.getChildren().removeAll(hero[i]);
            Competitor competitor = game.getCompetitor(i);
            hero[i] = graphicRender.buildHeroPlace(competitor.getHero());
            hero[i].setLayoutX(GameConstants.getInstance().getInteger("player"+(i+1)+"HeroPlaceX"));
            hero[i].setLayoutY(GameConstants.getInstance().getInteger("player"+(i+1)+"HeroPlaceY"));
            rootPane.getChildren().add(hero[i]);
            hero[i].toBack();
            setForPerformAction(competitor.getHero(), i, true, hero[i]);
            renderHand(competitor.getInHandCards(), hand[i], ((i == 0) ? true : false));
            renderBattleGround(competitor.getOnBoardCards(), battleGround[i], i);
            manaText[i].setText(competitor.getLeftMana()+"/"+competitor.getFullMana());
            if(i == 0)
                renderManaBar(competitor.getLeftMana(), competitor.getFullMana());
            cardsNumberLabel[i].setText(getCardsNumberString(competitor));
            renderHeroPower(competitor, i);
            renderHeroWeapon(competitor, i);
        }
    }

    private void renderHeroWeapon(Competitor competitor, int side) {
        heroWeapon[side].getChildren().clear();
        if(competitor.getHeroWeapon() != null){
            Parent parent =  GraphicRender.getInstance().buildHeroWeapon(competitor.getHeroWeapon());
            setForPerformAction(competitor.getHeroWeapon(),side, true, parent);
            heroWeapon[side].getChildren().add(parent);
        }
    }

    private void renderHeroPower(Competitor competitor, int side) {
        heroPowerPlace[side].getChildren().clear();
        Parent parent =  GraphicRender.getInstance().buildHeroPower(competitor.getHero().getHeroPower());
        setForPerformAction(competitor.getHero().getHeroPower(),side, true, parent);
        heroPowerPlace[side].getChildren().add(parent);
    }

    private String getCardsNumberString(Competitor competitor){
        return competitor.getInDeckCards().size() + "\n/\n" + competitor.getHero().getDeckMax();
    }

    private void renderHand(ArrayList<Card> cards, Pane hand, boolean isForOwn) {
        hand.getChildren().clear();
        int counter = 0;
        for(Card card: cards){
            Pane graphicCard = GraphicRender.getInstance().buildCard(card, false, false, (!isForOwn && game.isWithBot()));
            hand.getChildren().add(graphicCard);
            if(isForOwn || !game.isWithBot()){
                handCardSetAction(graphicCard, card, isForOwn ? 0 : 1);
            }
            if(cards.size() == 1) graphicCard.setLayoutX(0);
            else graphicCard.setLayoutX(counter*((hand.getPrefWidth()-graphicCard.getPrefWidth())/(cards.size()-1)));
            counter++;
        }
    }

    private void handCardSetAction(Parent graphicCard, Card card, int side){
        graphicCard.setOnMouseEntered(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                graphicCard.setLayoutY(
                        graphicCard.getLayoutY()-((side == 0)? 1:-1)*GameConstants.getInstance().getInteger("liftUpCardInHad")
                );
                graphicCard.toFront();
            }
        });
        int cnt = hand[side].getChildren().indexOf(graphicCard);
        graphicCard.setOnMouseExited(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                synchronized (lock){
                    if(hand[side].getChildren().contains(graphicCard)){
                        int tmp = hand[side].getChildren().size() - cnt - 1 ;
                        while (tmp-- > 0){
                            hand[side].getChildren().get(cnt).toFront();
                        }
                        graphicCard.setLayoutY(
                                graphicCard.getLayoutY() + ((side == 0)? 1:-1)*GameConstants.getInstance().getInteger("liftUpCardInHad")
                        );
                    }
                }
            }
        });
        final double[] mousePosition = {0,0};
        final double[] mouseFirstPosition = {0,0};
        graphicCard.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                LogCenter.getInstance().getLogger().info("drag_detected");
                mousePosition[0] = event.getSceneX();
                mousePosition[1] = event.getSceneY();
                mouseFirstPosition[0] = event.getSceneX();
                mouseFirstPosition[1] = event.getSceneY();
            }
        });
        graphicCard.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                graphicCard.setLayoutX(graphicCard.getLayoutX() + event.getSceneX() - mousePosition[0]);
                graphicCard.setLayoutY(graphicCard.getLayoutY() + event.getSceneY() - mousePosition[1]);
                mousePosition[0] = event.getSceneX();
                mousePosition[1] = event.getSceneY();
            }
        });
        graphicCard.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                synchronized (lock){
                    LogCenter.getInstance().getLogger().info("drag_ended");
                    try {
                        int maxYForPlayCard = GameConstants.getInstance().getInteger("maxYForPlayCard");
                        int minYForPlayCard = GameConstants.getInstance().getInteger("minYForPlayCard");
                        if ((side == 0 && graphicCard.getParent().getLayoutY() + graphicCard.getLayoutY() < maxYForPlayCard) ||
                                (side == 1 && graphicCard.getParent().getLayoutY() + graphicCard.getLayoutY() + ((Pane) graphicCard).getHeight() > minYForPlayCard)){
                            performAction(card, side, false,graphicCard);
                            LogCenter.getInstance().getLogger().info("play_"+card.getType());
                            addGameLog("play_"+card.getType());
                        }
                    } catch (Exception e) {
                        LogCenter.getInstance().getLogger().error(e);
                        e.printStackTrace();
                    }
                    graphicCard.setLayoutX(graphicCard.getLayoutX() - event.getSceneX() + mouseFirstPosition[0]);
                    graphicCard.setLayoutY(graphicCard.getLayoutY() - event.getSceneY() + mouseFirstPosition[1]);
                }
            }
        });
    }

    private void cardPlaySound(Card card) {
        MediaManager mediaManager = MediaManager.getInstance();
        if (card instanceof Minion){
            mediaManager.playMedia(GameConstants.getInstance().getString("minionPlacingSoundTrack"), 1);
        }
        else {
            mediaManager.playMedia(GameConstants.getInstance().getString("putCardFromHandSoundTrack"), 1);
        }
    }

    private void renderBattleGround(ArrayList<Minion> cards, HBox battleGround, int side) {
        battleGround.getChildren().clear();
        for(Card card: cards){
            Parent parent = GraphicRender.getInstance().buildBattleGroundMinion((Minion) card);
            setForPerformAction(card, side, true, parent);
            battleGround.getChildren().add(parent);
        }
    }

    private void renderManaBar(int leftMana, int fullMana) {
        manaBar.getChildren().clear();
        for(int i = 0; i < leftMana; i++){
            ImageView imageView = new ImageView(AssetManager.getInstance().getImage("mana"));
            imageView.setFitWidth(manaBar.getPrefHeight());
            imageView.setFitHeight(manaBar.getPrefHeight());
            manaBar.getChildren().add(imageView);
        }
        for(int i = 0; i < fullMana - leftMana; i++){
            ImageView imageView = new ImageView(AssetManager.getInstance().getImage("darkMana"));
            imageView.setFitWidth(manaBar.getHeight());
            imageView.setFitHeight(manaBar.getHeight());
            manaBar.getChildren().add(imageView);
        }
    }

    @FXML
    private StackPane alertBox;
    @FXML
    private Label alertMessage;
    @FXML
    private Button endTurnButton;
    @FXML
    private void endTurn() {
        infoPacks.clear();
        MediaManager.getInstance().playMedia(GameConstants.getInstance().getString("endTurnSoundTrack"), 1);
        changeTurn();
    }

    private void changeTurn() {
        LogCenter.getInstance().getLogger().info("end_turn_player_"+(game.getTurn()+1));
        addGameLog("end_turn_player_"+(game.getTurn()+1));
        try {
            ActionRequest.END_TURN.execute();
        } catch (GameOverException e) {
            endGame();
        }
        if (game.getTurn() == 0 || !game.isWithBot()) endTurnButton.setDisable(false);
        else endTurnButton.setDisable(true);
    }

    private void endGame(){
        LogCenter.getInstance().getLogger().info("game_over");
        addGameLog("game_over");
        alertBox.setVisible(true);
        if(game.getWinner() == 0) alertMessage.setText("You Win.");
        else alertMessage.setText("You Lose.");
    }

    private boolean ch =false;
    public TranslateTransition putCardToHandAnimation(Pane cardPane, boolean isForOwn) {
        int side = isForOwn? 0:1;
        rootPane.getChildren().add(cardPane);
        int duration = 2;
        double fromX, fromY, toX, toY;
        fromX = cardsNumberLabel[side].getLayoutX();
        fromY = cardsNumberLabel[side].getLayoutY();
        toX = hand[side].getLayoutX();
        toY = hand[side].getLayoutY();
        return buildTranslateTransition(cardPane, fromX, fromY, toX, toY, duration);
    }

    @FXML
    private void backToMenu() {
        LogCenter.getInstance().getLogger().info("back_to_menu");
        MediaManager.getInstance().stopMedia(GameConstants.getInstance().getString("battleGroundSoundTrack"));
        MediaManager.getInstance().playMedia(GameConstants.getInstance().getString("menuSoundTrack"), -1);
        root.setVisible(false);
    }

    @FXML
    private StackPane passiveSelectionPane;
    @FXML
    private HBox passiveSelectionPlace;

    public void renderPassives() {
        passiveSelectionPane.setVisible(true);
        ArrayList<Passive> passives = DataManager.getInstance().getAllCharacter(Passive.class);
        int passivesNumber = GameConstants.getInstance().getInteger("passivesOnGamesStar");
        while (passivesNumber-- > 0){
            Random random = new Random();
            Passive passive = passives.get(random.nextInt(passives.size()));
            Pane passiveGraphics = GraphicRender.getInstance().buildPassive(passive);
            passiveGraphics.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    LogCenter.getInstance().getLogger().info("passive_selected");
                    addGameLog("passive_selected");
                    performAction(passive, 0, false, passiveGraphics);
                    passiveSelectionPane.setVisible(false);
                }
            });
            passiveSelectionPlace.getChildren().add(passiveGraphics);
            passives.remove(passive);
        }
    }

    private void addGameLog(String log){
        for (Node node: gameLogGridPane.getChildren()){
            GridPane.setConstraints(node, 0, GridPane.getRowIndex(node)+1);
        }
        Label logLabel = new Label(log);
        logLabel.setTextFill(Color.WHITE);
        GridPane.setConstraints(logLabel, 0, 0);
        gameLogGridPane.getChildren().add(logLabel);
    }
    private synchronized void performAction(Character character, int side, boolean isOnGround, Parent parent){
        Logger logger = LogCenter.getInstance().getLogger();
        infoPacks.add(new InfoPack(character, side, isOnGround, parent));
        InfoPack[] parameters = new InfoPack[infoPacks.size()];
        for(int i = 0; i < infoPacks.size(); i++){
             parameters[i] = infoPacks.get(i);
        }
        try {
            ActionRequest.PERFORM_ACTION.execute(parameters);
            renderActions();
            infoPacks.clear();
        } catch (Exception e) {
            logger.error(e);
            try {
                throw e;
            } catch (SelectionNeededException selectionNeededException) {
                selectionNeededException.printStackTrace();
                //kjslkfdjs
            } catch (InvalidChoiceException invalidChoiceException) {
                invalidChoiceException.printStackTrace();
                infoPacks.clear();
            } catch (GameOverException gameOverException) {
                gameOverException.printStackTrace();
                endGame();
            }
        }
    }
    private ArrayList<Transition> transitions = new ArrayList<>();
    private ArrayList<ActionHandler> afterAction = new ArrayList<>(), beforeAction = new ArrayList<>();
    int cnt =0;
    private void renderActions() {
        if(cnt == 0){
            cnt =1;
            return;
        }
        rootPane.setDisable(true);
        MediaManager mediaManager = MediaManager.getInstance();
        GameConstants gameConstants = GameConstants.getInstance();
        transitions.clear();
        afterAction.clear();
        beforeAction.clear();
        setPlayCardTransition();
     //   setAttackTransition();
      //  setDrawTransition();
        if(transitions.size() > 0) bindTransitions();
        try {
            if (transitions.size() > 0) beforeAction.get(0).runAction();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void bindTransitions() {
        for(int i = 0; i < transitions.size() - 1; i++){
            int finalI = i;
            transitions.get(i).setOnFinished(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    try {
                        afterAction.get(finalI).runAction();
                        beforeAction.get(finalI + 1).runAction();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        transitions.get(transitions.size() - 1).setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    afterAction.get(transitions.size() - 1).runAction();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if(ActionRequest.readSummoned()){
                    MediaManager.getInstance().playMedia(GameConstants.getInstance().getString("minionPlacingSoundTrack"), 1);
                }
                rootPane.setDisable(false);
                gameRender();
            }
        });
    }

    private void setDrawTransition() {
        int drawNumber = ActionRequest.readDrawNumber();
        while (drawNumber > 0 ){
            Card card = game.getCompetitor(game.getTurn()).getInHandCards().get(game.getCompetitor(game.getTurn()).getInHandCards().size() - drawNumber--);
            Pane cardPane = GraphicRender.getInstance().buildCard(card, false, false, (game.getTurn() == 0)? true:false);
            Transition transition = putCardToHandAnimation(cardPane, (game.getTurn() == 0)? true:false);
            transitions.add(transition);
            beforeAction.add(new ActionHandler() {
                @Override
                public void runAction() throws Exception {
                    //MediaManager.getInstance().playMedia(GameConstants.getInstance().getString(), 1);
                    transition.play();
                }
            });
            afterAction.add(new ActionHandler() {
                @Override
                public void runAction() throws Exception {
                    rootPane.getChildren().remove(cardPane);
                }
            });
        }
    }

    private void setAttackTransition() {
        ArrayList<InfoPack> attackList = ActionRequest.readAttackingList();
        if(attackList.size() > 0){
            Transition go = goAttackAnimation(attackList.get(0).getParent(), attackList.get(1).getParent());
            Transition back = backAttackAnimation(attackList.get(0).getParent(), attackList.get(1).getParent());
            transitions.add(go);
            transitions.add(back);
            beforeAction.add(new ActionHandler() {
                @Override
                public void runAction() throws Exception {
                    go.play();
                }
            });
            afterAction.add(new ActionHandler() {
                @Override
                public void runAction() throws Exception {
                    //MediaManager.getInstance().playMedia(GameConstants.getInstance().getString(),1);
                }
            });
            beforeAction.add(new ActionHandler() {
                @Override
                public void runAction() throws Exception {
                    back.play();
                }
            });
            afterAction.add(new ActionHandler() {
                @Override
                public void runAction() throws Exception { }
            });
        }
    }

    private Transition backAttackAnimation(Parent parent, Parent parent1) {
        return null;
    }

    private Transition goAttackAnimation(Parent parent1, Parent parent2) {
        return null;
    }

    private void setPlayCardTransition() {
        InfoPack played = ActionRequest.readPlayed();
        if(played != null){
            Transition transition = playCardTransition(played.getParent());
            transitions.add(transition);
            beforeAction.add(new ActionHandler() {
                @Override
                public void runAction() throws Exception {
                    //MediaManager.getInstance().playMedia(GameConstants.getInstance().getString(), 1);
                    transition.play();
                }
            });
            afterAction.add(new ActionHandler() {
                @Override
                public void runAction() throws Exception {
                    hand[played.getSide()].getChildren().remove(played.getParent());
                }
            });
        }
    }

    private TranslateTransition playCardTransition(Parent parent) {
        double toX = GameConstants.getInstance().getInteger("screenWidth")/2 - parent.getParent().getLayoutX() + ((Pane)parent).getWidth()/2;
        double toY = GameConstants.getInstance().getInteger("screenHeight")/2 - parent.getParent().getLayoutY() + ((Pane)parent).getHeight()/2;
        return buildTranslateTransition(parent, parent.getLayoutX(), parent.getLayoutY(), toX, toY, 1);
    }

    private TranslateTransition buildTranslateTransition(Parent parent, double fromX, double fromY, double toX, double toY, double second){
        TranslateTransition translateTransition = new TranslateTransition(Duration.seconds(second), parent);
        translateTransition.setFromX(fromX);
        translateTransition.setFromY(fromY);
        translateTransition.setToX(toX);
        translateTransition.setToY(toY);
        translateTransition.setCycleCount(1);
        return translateTransition;
    }

    private void setForPerformAction(Character character, int side, boolean isOnGround, Parent parent){
    parent.setOnMouseClicked(new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {
            performAction(character, side, isOnGround, parent);
        }
    });
}
}
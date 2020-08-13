package Network.Server;

import Data.DataManager;
import Data.JacksonMapper;
import Exceptions.GameOverException;
import Exceptions.InvalidChoiceException;
import Exceptions.SelectionNeededException;
import Logic.Game;
import Logic.GameFactory;
import Logic.PlayersManager;
import Models.Cards.Card;
import Models.Deck;
import Models.InfoPack;
import Models.Passive;
import Models.Player;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Server extends Thread{
    private static Server server;
    private static int defaultPort = 8080;
    private ArrayList<ClientHandler> clientHandlers;
    private ArrayList<ClientHandler>  waitingList, deckReaderWaitingList;
    private ServerSocket serverSocket;
    private HashMap<ClientHandler, Game> gameMap;
    private HashMap<Game, String> gameKindMap;

    private Server(int serverPort) throws IOException {
        serverSocket = new ServerSocket(serverPort);
        gameMap = new HashMap<>();
        clientHandlers = new ArrayList<>();
        waitingList = new ArrayList<>();
        gameKindMap = new HashMap<>();
        deckReaderWaitingList = new ArrayList<>();
    }

    public synchronized static Server getInstance() throws IOException {
        return getInstance(-1);
    }

    public synchronized static Server getInstance(int port) throws IOException {
        if(port == -1) port = defaultPort;
        if(server == null){
            server = new Server(port);
        }
        return server;
    }

    @Override
    public void run() {
        Thread thread = new Thread(()->{
            while (!isInterrupted()){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
                PlayersManager.getInstance().save();
            }
        });
        thread.start();
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
           while (!isInterrupted()){
               if(scanner.nextLine().equals("exit")){
                   thread.interrupt();
                   try {
                       serverSocket.close();
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
                   break;
               }
           }
        }).start();
        while(!isInterrupted() && !serverSocket.isClosed()){
            try {
                Socket socket = serverSocket.accept();
                ClientHandler  clientHandler = new ClientHandler(socket.getInputStream(), socket.getOutputStream(), this);
                clientHandlers.add(clientHandler);
                clientHandler.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void checkForDeck(ClientHandler clientHandler) throws Exception {
        if(clientHandler.getPlayer().getCurrentDeckName() == null){
            throw new Exception("Please Select Your Deck To Start.");
        }
    }

    public synchronized void startOfflineGame(ClientHandler clientHandler) throws Exception {
        checkForDeck(clientHandler);
        Deck deck = clientHandler.getPlayer().getDeck(clientHandler.getPlayer().getCurrentDeckName());
        startGame(clientHandler, clientHandler, deck, deck, "offline", false, false);
    }

    public synchronized void startOnlineGame(ClientHandler clientHandler1) throws Exception {
        checkForDeck(clientHandler1);
        boolean check = false;
        for(ClientHandler clientHandler2: waitingList){
            if(isOkForPlay(clientHandler1, clientHandler2)){
                check = true;
                Player player1 = clientHandler1.getPlayer();
                Player player2 = clientHandler2.getPlayer();
                startGame(clientHandler1, clientHandler2, player1.getDeck(player1.getCurrentDeckName()), player2.getDeck(player2.getCurrentDeckName()), "online", false, true);
                waitingList.remove(clientHandler2);
                break;
            }
        }
        if(!check) waitingList.add(clientHandler1);
    }

    public synchronized void startDeckReaderGame(ClientHandler clientHandler1) throws Exception {
        boolean check = false;
        for(ClientHandler clientHandler2: deckReaderWaitingList){
            if(isOkForPlay(clientHandler1, clientHandler2)){
                check = true;
                startGame(clientHandler1, clientHandler2, DataManager.getInstance().getDeckReaderDecks().get(1), DataManager.getInstance().getDeckReaderDecks().get(0), "online", false, true);
                deckReaderWaitingList.remove(clientHandler2);
                break;
            }
        }
        if(!check) deckReaderWaitingList.add(clientHandler1);
    }

    private void startGame(ClientHandler clientHandler1, ClientHandler clientHandler2, Deck deck1, Deck deck2, String kind, boolean isWithBot, boolean sendForBoth) throws Exception {
        Player player1 = clientHandler1.getPlayer();
        Player player2 = clientHandler2.getPlayer();
        Game game = GameFactory.getInstance().build(
                player1.getUsername(),
                player2.getUsername(),
                deck1,
                deck2,
                isWithBot
        );
        gameKindMap.put(game, "online");
        gameMap.put(clientHandler1, game);
        clientHandler1.send(new String[]{"startGame", JacksonMapper.getNetworkMapper().writeValueAsString(GameFactory.getInstance().getPrivateGame(player1.getUsername(), game))});
        if(sendForBoth){
            gameMap.put(clientHandler2, game);
            clientHandler2.send(new String[]{"startGame", JacksonMapper.getNetworkMapper().writeValueAsString(GameFactory.getInstance().getPrivateGame(player2.getUsername(), game))});
        }
    }

    private boolean isOkForPlay(ClientHandler clientHandler1, ClientHandler clientHandler2) {
        //todo
        return true;
    }

    public void exitClient(ClientHandler clientHandler) {
        try {
            endGame(clientHandler, false);
        } catch (Exception e){}
        clientHandlers.remove(clientHandler);
        clientHandler.send(new String[]{"exit"});
    }

    public void performAction(ClientHandler clientHandler, ArrayList<InfoPack> infoPacks) throws GameOverException, SelectionNeededException, InvalidChoiceException {
        Game game = gameMap.get(clientHandler);
        try {
            if(game.getInfoPack(infoPacks.get(0).getCharacter().getId()).getSide() != game.getCompetitorIndex(clientHandler.getPlayer().getUsername())){
                throw new InvalidChoiceException();
            }
        } catch (NullPointerException e){
            Passive passive = DataManager.getInstance().getObject(Passive.class, infoPacks.get(0).getCharacter().getName());
            InfoPack[] passiveInfoPack = {new InfoPack(passive, infoPacks.get(0).getSide(), infoPacks.get(0).isOnGround())};
            if(!game.usePassive(game.getCompetitorIndex(clientHandler.getPlayer().getUsername()))){
                game.getActionRequest().getPerformAction().execute(passiveInfoPack);
                sendGameStateToClients(game);
                return;
            }
        }
        ArrayList<InfoPack> infoPacks1 = new ArrayList<>();
        for(InfoPack infoPack: infoPacks){
            InfoPack infoPack1 = game.getInfoPack(infoPack.getCharacter().getId());
            if(infoPack1 == null) throw new InvalidChoiceException();
            infoPack1.setSummonPlace(infoPack.getSummonPlace());
            infoPacks1.add(infoPack1);
        }
        InfoPack[] infoPacks2 = new InfoPack[infoPacks1.size()];
        for(int i = 0; i < infoPacks2.length; i++){
            infoPacks2[i] = infoPacks1.get(i);
        }
        game.getActionRequest().getPerformAction().execute(infoPacks2);
        sendGameStateToClients(game);
    }

    private void sendGameStateToClients(Game game) {
        Game finalGame = game;
        for(ClientHandler clientHandler: gameMap.keySet()){
            if(gameMap.get(clientHandler) == finalGame){
                if(!gameKindMap.get(finalGame).equals("offline")){
                    try {
                        clientHandler.send(new String[]{"updateGame", JacksonMapper.getNetworkMapper().writeValueAsString(GameFactory.getInstance().getPrivateGame(clientHandler.getPlayer().getUsername(), game))});
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                        clientHandler.send(new String[]{"updateGame", JacksonMapper.getNetworkMapper().writeValueAsString(game)});
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        finalGame.getActionRequest().clearRecords();
    }

    public void endTurn(ClientHandler clientHandler) throws InvalidChoiceException, GameOverException {
        Game game = gameMap.get(clientHandler);
        if(game.getTurn() != game.getCompetitorIndex(clientHandler.getPlayer().getUsername())){
            throw new InvalidChoiceException();
        }
        game.getActionRequest().getEndTurn().execute();
        sendGameStateToClients(game);
    }

    public void cardSelection(ClientHandler clientHandler, ArrayList<Card> cards) throws GameOverException, InvalidChoiceException {
        Game game = gameMap.get(clientHandler);
        if(gameKindMap.get(game).equals("online")) game.getActionRequest().selectCard(cards, game.getCompetitorIndex(clientHandler.getPlayer().getUsername()));
        else {
            try {
                game.getActionRequest().selectCard(cards, 0);
            } catch (InvalidChoiceException e){
                game.getActionRequest().selectCard(cards, 1);
            }
        }
        sendGameStateToClients(game);
    }



    public void endGame(ClientHandler clientHandler, boolean sendForOwn){
        Game game = gameMap.get(clientHandler);
        int index = game.getCompetitorIndex(clientHandler.getPlayer().getUsername());
        game.endGame((index+1)%2);
        if(gameKindMap.get(game).equals("online")){
            ClientHandler clientHandler2 = null;
            for(ClientHandler clientHandler1:gameMap.keySet()){
                if(gameMap.get(clientHandler1) == game && clientHandler1 != clientHandler){
                    clientHandler2 = clientHandler1;
                    break;
                }
            }
            if(game.getWinner() == index){
                clientHandler.getPlayer().setCup(clientHandler.getPlayer().getCup() + 1);
                clientHandler2.getPlayer().setCup(Math.max(clientHandler2.getPlayer().getCup() - 1, 0));
            }
            else {
                clientHandler2.getPlayer().setCup(clientHandler2.getPlayer().getCup() + 1);
                clientHandler.getPlayer().setCup(Math.max(clientHandler.getPlayer().getCup() - 1, 0));
            }
            clientHandler2.endGame(GameFactory.getInstance().getPrivateGame(clientHandler2.getPlayer().getUsername(), game));
            gameMap.remove(clientHandler2);
        }
        if(sendForOwn){
            if(gameKindMap.get(game).equals("online")){
                clientHandler.endGame(GameFactory.getInstance().getPrivateGame(clientHandler.getPlayer().getUsername(), game));
            }
            else{
                clientHandler.endGame(game);
            }
        }
        gameKindMap.remove(game);
        gameMap.remove(clientHandler);
    }
}

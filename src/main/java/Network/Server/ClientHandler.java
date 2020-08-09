package Network.Server;

import Logic.PlayersManager;
import Models.Player;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class ClientHandler extends Thread{
    private Server server;
    private Player player = null;
    private Scanner scanner;
    private PrintStream printStream;
    private Gson gson;
    public ClientHandler(InputStream inputStream, OutputStream outputStream, Server server){
        this.server = server;
        scanner = new Scanner(inputStream);
        printStream = new PrintStream(outputStream);
        gson = new Gson();
    }

    @Override
    public void run() {
        while(!isInterrupted()){
            String string = scanner.nextLine();
            System.out.println("get: "+string);
            ArrayList<String> massagesList = gson.fromJson(string, new TypeToken<ArrayList<String>>(){}.getType());
            String methodName = massagesList.get(0);
            massagesList.remove(0);
            if(methodName.equals("exit")){
                server.exitClient(this);
                return;
            }
            if(player == null && !methodName.equalsIgnoreCase("logIn") && !methodName.equalsIgnoreCase("signIn")){
                continue;
            }
            for(Method method: ClientHandler.class.getDeclaredMethods()){
                if(method.getName().equals(methodName)){
                    try{
                        if(massagesList.size() == 0){
                            method.invoke(this);
                        }
                        else {
                            method.invoke(this, massagesList.toArray());
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        sendException((Exception) e.getCause());
                    }
                    break;
                }
            }
        }
    }

    private void logIn(String username, String password) throws Exception {
        player = PlayersManager.getInstance().logIn(username, password);
        send(new String[]{"logIn"});
    }

    private void signIn(String username, String password) throws Exception {
        player = PlayersManager.getInstance().signIn(username, password);
        send(new String[]{"signIn"});
    }

    public void sendException(Exception exception){
        send(new String[]{"error", exception.getClass().getName(), gson.toJson(exception)});
    }

    public synchronized void send(String[] massages){
        ArrayList<String> massagesList = new ArrayList<>();
        if(player != null) massagesList.add(gson.toJson(player));
        else massagesList.add("null");
        massagesList.addAll(Arrays.asList(massages));
        printStream.println(gson.toJson(massagesList));
        printStream.flush();
        System.out.println("send: "+gson.toJson(massagesList));
    }
}
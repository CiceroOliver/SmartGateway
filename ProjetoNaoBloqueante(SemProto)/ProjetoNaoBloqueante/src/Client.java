import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Scanner;


public class Client {
    private final Scanner scanner;
    private SocketChannel clientChannel;
    private Thread listenerThread;

    public Client() throws IOException {
        clientChannel = SocketChannel.open();

        /*No cliente usa-se connect e não bind.
        O bind é pra dizer qual porta local o cliente vai usar.
        Se isso não for feito, o SO decide qual porta usar.
        O cliente precisa de uma porta local para permitir que o servidor,
        após receber a conexão na porta 4000,
        passe a atender o cliente numa porta diferente
        enquanto aceita novas conexões na porta 4000.
        */
        clientChannel.connect(new InetSocketAddress("127.0.0.1", 4000));
        scanner = new Scanner(System.in);
    }

    public void start() throws IOException {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientChannel.socket().getInputStream()));
            PrintWriter out = new PrintWriter(clientChannel.socket().getOutputStream(), true)
        ) {
            // Thread para ouvir mensagens do servidor
            listenerThread = new Thread(() -> {
                String serverMsg;
                try {
                    while (!Thread.currentThread().isInterrupted() && (serverMsg = in.readLine()) != null) {
                        System.out.println("Servidor: " + serverMsg);
                    }
                } catch (IOException e) {
                    System.out.println("Conexão com o servidor encerrada.");
                }
            });
            listenerThread.start();

            // Loop principal para enviar mensagens ao servidor
            String msg;
            do {
                //System.out.print("Digite uma mensagem (ou sair para finalizar): ");
                msg = scanner.nextLine();
                out.println(msg);
            } while (!"sair".equalsIgnoreCase(msg));

            // Solicita interrupção da thread de escuta
            listenerThread.interrupt();
        } catch (IOException e) {
            System.err.println("Erro na comunicação com o servidor: " + e.getMessage());
        } finally {
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.interrupt();
            }
            clientChannel.close();
            System.out.println("Cliente desconectado.");
        }
    }

    public static void main(String[] args) {
        try {
            Client client = new Client();
            client.start();
        } catch (IOException e) {
            System.err.println("Erro ao inicializar cliente: " + e.getMessage());
        }
    }
}

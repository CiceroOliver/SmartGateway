import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

public class Gateway {
    public static final int CLIENT_PORT = 4000; // Porta para o Client
    public static final int SENSOR_PORT = 5000; // Porta para os Sensores
    public static final String HOSTNAME = "127.0.0.1";
    public static final String MULTICAST_GROUP = "230.0.0.0";
    public static final int MULTICAST_PORT = 6000; // Porta para a comunicação multicast

    private ServerSocketChannel sensorServerChannel;
    private ServerSocketChannel clientServerChannel;
    private List<SocketChannel> sensors = Collections.synchronizedList(new ArrayList<>());
    private List<SocketChannel> clients = Collections.synchronizedList(new ArrayList<>());

    // ThreadPool para lidar com múltiplas conexões de sensores
    private ExecutorService sensorThreadPool = Executors.newFixedThreadPool(10); // Até 10 sensores simultâneos

    public Gateway() throws IOException {
        // Configura o servidor para sensores
        sensorServerChannel = ServerSocketChannel.open();
        sensorServerChannel.configureBlocking(true);
        sensorServerChannel.bind(new InetSocketAddress(HOSTNAME, SENSOR_PORT));
        System.out.println("Servidor Sensor TCP iniciado no endereço " + HOSTNAME + " na porta " + SENSOR_PORT);

        // Configura o servidor para clientes
        clientServerChannel = ServerSocketChannel.open();
        clientServerChannel.configureBlocking(true);
        clientServerChannel.bind(new InetSocketAddress(HOSTNAME, CLIENT_PORT));
        System.out.println("Servidor Client TCP iniciado no endereço " + HOSTNAME + " na porta " + CLIENT_PORT);
    }

    public void start() throws IOException {
        // Inicia a thread de multicast para enviar mensagens periódicas aos sensores
        new Thread(this::sendMulticast).start();

        // Aceita conexões dos Sensores
        new Thread(this::acceptSensors).start();

        // Aceita conexões dos Clients
        new Thread(this::acceptClients).start();
    }

    private void sendMulticast() {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);

            // Mensagem que os sensores irão receber para saber como se conectar ao Gateway
            String message = HOSTNAME + ":" + SENSOR_PORT;

            // Envia a mensagem multicast a cada 5 segundos
            while (true) {
                DatagramPacket packet = new DatagramPacket(
                        message.getBytes(),
                        message.length(),
                        group,
                        MULTICAST_PORT
                );
                socket.send(packet);
                System.out.println("Mensagem de multicast enviada: " + message);
                Thread.sleep(5000); // Aguarda 5 segundos antes de enviar novamente
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Erro ao enviar multicast: " + e.getMessage());
        }
    }

    private void acceptSensors() {
        try {
            while (true) {
                SocketChannel sensorChannel = sensorServerChannel.accept(); //Bloqueia e espera proximo sensor
                System.out.println("Sensor TCP " + sensorChannel.getRemoteAddress() + " conectado.");
                sensors.add(sensorChannel);

                // Envia para o ThreadPool para tratamento paralelo
                sensorThreadPool.submit(() -> handleSensor(sensorChannel));
            }
        } catch (IOException e) {
            System.out.println("Erro ao aceitar conexão de Sensor: " + e.getMessage());
        }
    }

    private void handleSensor(SocketChannel sensorChannel) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(sensorChannel.socket().getInputStream()));
            String msg;
            while ((msg = in.readLine()) != null) { //O readLine é bloqueante
                System.out.println("Mensagem recebida do Sensor TCP " + sensorChannel.getRemoteAddress() + ": " + msg);
                forwardToClients("Mensagem do Sensor: " + msg);
            }
        } catch (IOException e) {
            System.out.println("Erro ao comunicar com o Sensor: " + e.getMessage());
        } finally {
            sensors.remove(sensorChannel);
            try {
                sensorChannel.close();
            } catch (IOException e) {
                System.out.println("Erro ao fechar conexão com Sensor: " + e.getMessage());
            }
        }
    }

    private void acceptClients() {
        try {
            while (true) {
                SocketChannel clientChannel = clientServerChannel.accept();
                System.out.println("Client TCP " + clientChannel.getRemoteAddress() + " conectado.");
                clients.add(clientChannel);

                // Trata clientes em uma nova thread
                new Thread(() -> handleClient(clientChannel)).start();
            }
        } catch (IOException e) {
            System.out.println("Erro ao aceitar conexão de Client: " + e.getMessage());
        }
    }

    private void handleClient(SocketChannel clientChannel) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientChannel.socket().getInputStream()));
            //PrintWriter out = new PrintWriter(clientChannel.socket().getOutputStream(), true);

            String msg;
            while ((msg = in.readLine()) != null) {  //O readLine é bloqueante
                System.out.println("Mensagem recebida do Client TCP " + clientChannel.getRemoteAddress() + ": " + msg);
                forwardToSensors("Mensagem do Client: " + msg);
            }
        } catch (IOException e) {
            System.out.println("Erro ao comunicar com o Client: " + e.getMessage());
        } finally {
            clients.remove(clientChannel);
            try {
                clientChannel.close();
            } catch (IOException e) {
                System.out.println("Erro ao fechar conexão com Client: " + e.getMessage());
            }
        }
    }

    private void forwardToSensors(String message) {
        synchronized (sensors) {
            for (SocketChannel sensorChannel : sensors) {
                try {
                    PrintWriter out = new PrintWriter(sensorChannel.socket().getOutputStream(), true);
                    out.println(message);
                    System.out.println("Mensagem do Gateway repassada para o Sensor: " + message);
                } catch (IOException e) {
                    System.out.println("Erro ao repassar mensagem para o Sensor: " + e.getMessage());
                }
            }
        }
    }

    private void forwardToClients(String message) {
        synchronized (clients) {
            for (SocketChannel clientChannel : clients) {
                try {
                    PrintWriter out = new PrintWriter(clientChannel.socket().getOutputStream(), true);
                    out.println(message);
                    System.out.println("Mensagem do Gateway repassada para o Client: " + message);
                } catch (IOException e) {
                    System.out.println("Erro ao repassar mensagem para o Client: " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            Gateway server = new Gateway();
            server.start();
        } catch (IOException e) {
            System.err.println("Erro ao inicializar servidor: " + e.getMessage());
        }
    }
}

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.Random;

public class Sensor2 {
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private static final int MULTICAST_PORT = 6000;
    private static final int STATUS_INTERVAL = 10000; // 10 segundos

    private String gatewayHost;
    private int gatewayPort;

    private SocketChannel socketChannel;
    private PrintWriter out;
    private BufferedReader in;

    public Sensor2() {
        this.gatewayHost = null;
        this.gatewayPort = -1;
    }

    @SuppressWarnings("deprecation")
    public void discoverGateway() {
        try (MulticastSocket multicastSocket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            multicastSocket.joinGroup(group);
    
            System.out.println("Sensor2 aguardando mensagens multicast...");
    
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    
            multicastSocket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Mensagem multicast recebida: " + received);
    
            // Agora a mensagem é esperada no formato "127.0.0.1:4000"
            String[] addressParts = received.split(":");
    
            if (addressParts.length == 2) {
                gatewayHost = addressParts[0]; // O host (IP)
                gatewayPort = Integer.parseInt(addressParts[1]); // A porta
    
                System.out.println("Gateway descoberto: " + gatewayHost + ":" + gatewayPort);
            } else {
                System.err.println("Mensagem multicast recebida em formato inválido.");
            }
    
            multicastSocket.leaveGroup(group);
        } catch (IOException e) {
            System.err.println("Erro ao descobrir o Gateway: " + e.getMessage());
        }
    }

    // Thread responsável pelo envio de mensagens
    public void startSending() {
        Random random = new Random();
        String[] statusOptions = {"Ligado", "Desligado"};

        while (true) {
            try {
                String status = statusOptions[random.nextInt(2)];
                String payload = "Payload-" + random.nextInt(1000);

                String message = "ID: 2, Status: " + status + ", Payload: " + payload;
                out.println(message);

                System.out.println("Enviado para o Gateway: " + message);

                Thread.sleep(STATUS_INTERVAL); // Aguarda o intervalo entre mensagens
            } catch (InterruptedException e) {
                System.err.println("Erro no envio de mensagem: " + e.getMessage());
            }
        }
    }

    // Thread responsável pela recepção de mensagens
    public void startReceiving() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                System.out.println("Mensagem recebida do Gateway: " + msg);
            }
        } catch (IOException e) {
            System.err.println("Erro ao receber mensagem do Gateway: " + e.getMessage());
        }
    }

    public void startCommunication() {
        if (gatewayHost == null || gatewayPort == -1) {
            System.out.println("Gateway não descoberto. Não é possível iniciar a comunicação TCP.");
            return;
        }

        try {
            socketChannel = SocketChannel.open(new InetSocketAddress(gatewayHost, gatewayPort));
            out = new PrintWriter(socketChannel.socket().getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socketChannel.socket().getInputStream()));

            System.out.println("Conectado ao Gateway em " + gatewayHost + ":" + gatewayPort);

            // Inicia as threads para envio e recepção de mensagens
            new Thread(this::startSending).start();
            new Thread(this::startReceiving).start();
        } catch (IOException e) {
            System.err.println("Erro na comunicação com o Gateway: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Sensor2 sensor = new Sensor2();
        sensor.discoverGateway();
        sensor.startCommunication();
    }
}

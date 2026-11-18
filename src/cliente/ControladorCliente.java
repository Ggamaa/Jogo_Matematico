package cliente;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Executor;

import comunicacao.InterfaceServidorJogo;
import modelo.EstadoPartida;
import modelo.Jogador;

/**
 * Concentra a comunicacao do cliente com o servidor: localiza o registry,
 * obtem o stub, exporta o callback e faz as chamadas remotas.
 *
 * Os metodos sao BLOQUEANTES (fazem I/O de rede) e devem ser chamados fora
 * da thread de eventos do Swing.
 */
public class ControladorCliente {

    private static final int TIMEOUT_CONEXAO_MS = 3000;

    /** true se o usuario ja definiu -Djava.rmi.server.hostname ao iniciar o cliente. */
    private static final boolean HOSTNAME_DEFINIDO_PELO_USUARIO =
            System.getProperty("java.rmi.server.hostname") != null;

    private InterfaceServidorJogo servidor;
    private ClienteCallbackImpl callback;
    private String hostConectado;
    private volatile String identificador;
    private String nome;

    /** Conecta ao servidor e entra na partida (usa a EDT do Swing para os eventos). */
    public synchronized void conectar(String host, int porta, String nome, OuvinteJogo ouvinte) throws Exception {
        conectar(host, porta, nome, ouvinte, null);
    }

    /**
     * @param entrega executor que entrega os eventos ao ouvinte (null = Swing EDT)
     */
    public synchronized void conectar(String host, int porta, String nome, OuvinteJogo ouvinte, Executor entrega)
            throws Exception {
        if (callback != null && !host.equals(hostConectado)) {
            desexportarCallback();
        }
        if (callback == null) {
            configurarEnderecoCallback(host, porta);
        }

        Registry registry = LocateRegistry.getRegistry(host, porta);
        servidor = (InterfaceServidorJogo) registry.lookup(InterfaceServidorJogo.NOME_SERVICO);

        if (callback == null) {
            callback = entrega == null
                    ? new ClienteCallbackImpl(ouvinte)
                    : new ClienteCallbackImpl(ouvinte, entrega, 0);
            hostConectado = host;
        }
        this.nome = nome;
        identificador = servidor.entrarNaPartida(new Jogador(null, nome), callback);
    }

    /** Entra em uma nova partida no mesmo servidor, reaproveitando o callback. */
    public synchronized void jogarNovamente() throws RemoteException {
        identificador = null;
        identificador = servidor.entrarNaPartida(new Jogador(null, nome), callback);
    }

    public void enviarResposta(int resposta) throws RemoteException {
        String id = identificador;
        if (id == null) {
            throw new IllegalStateException("Ainda não entrou na partida.");
        }
        servidor.enviarResposta(id, resposta);
    }

    public EstadoPartida consultarEstado() throws RemoteException {
        String id = identificador;
        return id == null ? null : servidor.consultarEstado(id);
    }

    /** Sai da partida (se estiver em uma) e libera o objeto de callback. */
    public synchronized void sair() {
        String id = identificador;
        identificador = null;
        if (servidor != null && id != null) {
            try {
                servidor.sairDaPartida(id);
            } catch (Exception ignorado) {
                // servidor indisponivel: nada a fazer
            }
        }
        desexportarCallback();
    }

    public String getIdentificador() {
        return identificador;
    }

    public String getNome() {
        return nome;
    }

    private void desexportarCallback() {
        if (callback != null) {
            try {
                UnicastRemoteObject.unexportObject(callback, true);
            } catch (Exception ignorado) {
                // ja estava desexportado
            }
            callback = null;
        }
    }

    /**
     * Descobre qual IP local e usado para alcancar o servidor e o grava em
     * java.rmi.server.hostname. E esse endereco que vai no stub do callback,
     * permitindo que o servidor chame o cliente de volta mesmo quando a
     * maquina tem varias placas de rede. Respeita um valor definido com -D.
     */
    private static void configurarEnderecoCallback(String host, int porta) throws Exception {
        try (Socket teste = new Socket()) {
            teste.connect(new InetSocketAddress(host, porta), TIMEOUT_CONEXAO_MS);
            if (!HOSTNAME_DEFINIDO_PELO_USUARIO) {
                System.setProperty("java.rmi.server.hostname", teste.getLocalAddress().getHostAddress());
            }
        }
    }

    /** Converte exceções técnicas em mensagens para o jogador. */
    public static String mensagemAmigavel(Throwable erro, String host, int porta) {
        Throwable t = erro;
        while (t != null) {
            if (t instanceof ConnectException || t instanceof java.net.SocketTimeoutException) {
                return "Não foi possível conectar ao servidor em " + host + ":" + porta
                        + ".\nVerifique se o servidor está rodando, o IP/porta e o firewall.";
            }
            if (t instanceof UnknownHostException) {
                return "Endereço do servidor desconhecido: " + host;
            }
            if (t instanceof NotBoundException) {
                return "O serviço \"" + InterfaceServidorJogo.NOME_SERVICO + "\" não está registrado nesse servidor.";
            }
            if (!(t instanceof RemoteException) && t.getMessage() != null) {
                return t.getMessage();
            }
            if (t.getCause() == null || t.getCause() == t) {
                break;
            }
            t = t.getCause();
        }
        return erro.getMessage() != null ? erro.getMessage() : erro.toString();
    }
}

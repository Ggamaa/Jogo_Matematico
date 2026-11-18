package cliente;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.Executor;

import javax.swing.SwingUtilities;

import comunicacao.InterfaceClienteJogo;
import modelo.EstadoPartida;
import modelo.Pergunta;
import modelo.ResultadoPartida;
import modelo.ResultadoResposta;

/**
 * Objeto remoto exportado pelo CLIENTE. O servidor recebe o stub deste objeto
 * em entrarNaPartida() e o usa para enviar eventos ao jogador (callback).
 *
 * Os metodos sao executados em threads do RMI; por isso cada evento e
 * repassado ao {@link OuvinteJogo} por meio de um {@link Executor} — por
 * padrao, a thread de eventos do Swing (SwingUtilities.invokeLater). Assim o
 * metodo remoto retorna rapido e a interface grafica so e tocada pela EDT.
 */
public class ClienteCallbackImpl extends UnicastRemoteObject implements InterfaceClienteJogo {

    private static final long serialVersionUID = 1L;

    private final transient OuvinteJogo ouvinte;
    private final transient Executor entrega;

    public ClienteCallbackImpl(OuvinteJogo ouvinte) throws RemoteException {
        this(ouvinte, SwingUtilities::invokeLater, Integer.getInteger("cliente.portaCallback", 0));
    }

    /**
     * @param porta porta local do callback (0 = porta livre escolhida pelo sistema)
     */
    public ClienteCallbackImpl(OuvinteJogo ouvinte, Executor entrega, int porta) throws RemoteException {
        super(porta);
        this.ouvinte = ouvinte;
        this.entrega = entrega;
    }

    @Override
    public void informarAguardandoJogador() throws RemoteException {
        entrega.execute(ouvinte::informarAguardandoJogador);
    }

    @Override
    public void iniciarPartida(EstadoPartida estado) throws RemoteException {
        entrega.execute(() -> ouvinte.iniciarPartida(estado));
    }

    @Override
    public void receberNovaPergunta(Pergunta pergunta, int numeroPergunta) throws RemoteException {
        entrega.execute(() -> ouvinte.receberNovaPergunta(pergunta, numeroPergunta));
    }

    @Override
    public void informarResultadoResposta(ResultadoResposta resultado) throws RemoteException {
        entrega.execute(() -> ouvinte.informarResultadoResposta(resultado));
    }

    @Override
    public void atualizarEstadoPartida(EstadoPartida estado) throws RemoteException {
        entrega.execute(() -> ouvinte.atualizarEstadoPartida(estado));
    }

    @Override
    public void finalizarPartida(ResultadoPartida resultado) throws RemoteException {
        entrega.execute(() -> ouvinte.finalizarPartida(resultado));
    }

    @Override
    public void informarDesconexaoAdversario() throws RemoteException {
        entrega.execute(ouvinte::informarDesconexaoAdversario);
    }
}

package comunicacao;

import java.rmi.Remote;
import java.rmi.RemoteException;
import modelo.EstadoPartida;
import modelo.Jogador;

public interface InterfaceServidorJogo extends Remote {

    /** Nome com que o servidor e registrado no RMI Registry. */
    String NOME_SERVICO = "JogoMatematico";

    /** Porta padrao do RMI Registry. */
    int PORTA_PADRAO = 1099;

    String entrarNaPartida(
        Jogador jogador,
        InterfaceClienteJogo cliente
    ) throws RemoteException;

    void enviarResposta(
        String identificadorJogador,
        int resposta
    ) throws RemoteException;

    EstadoPartida consultarEstado(
        String identificadorJogador
    ) throws RemoteException;

    void sairDaPartida(
        String identificadorJogador
    ) throws RemoteException;
}
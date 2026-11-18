package comunicacao;

import java.rmi.Remote;
import java.rmi.RemoteException;
import modelo.EstadoPartida;
import modelo.Pergunta;
import modelo.ResultadoPartida;
import modelo.ResultadoResposta;

public interface InterfaceClienteJogo extends Remote {

	void informarAguardandoJogador() throws RemoteException;

	void iniciarPartida(EstadoPartida estado) throws RemoteException;

	void receberNovaPergunta(Pergunta pergunta, int numeroPergunta) throws RemoteException;

	void informarResultadoResposta(ResultadoResposta resultado) throws RemoteException;

	void atualizarEstadoPartida(EstadoPartida estado) throws RemoteException;

	void finalizarPartida(ResultadoPartida resultado) throws RemoteException;

	void informarDesconexaoAdversario() throws RemoteException;
}
package cliente;

import modelo.EstadoPartida;
import modelo.Pergunta;
import modelo.ResultadoPartida;
import modelo.ResultadoResposta;

/**
 * Versao local (sem RemoteException) dos eventos recebidos do servidor.
 * A tela do jogo implementa esta interface; o objeto de callback RMI
 * ({@link ClienteCallbackImpl}) apenas repassa os eventos para ela.
 */
public interface OuvinteJogo {

    void informarAguardandoJogador();

    void iniciarPartida(EstadoPartida estado);

    void receberNovaPergunta(Pergunta pergunta, int numeroPergunta);

    void informarResultadoResposta(ResultadoResposta resultado);

    void atualizarEstadoPartida(EstadoPartida estado);

    void finalizarPartida(ResultadoPartida resultado);

    void informarDesconexaoAdversario();
}

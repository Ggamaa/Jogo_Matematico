package servidor;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import comunicacao.InterfaceClienteJogo;
import comunicacao.InterfaceServidorJogo;
import modelo.EstadoPartida;
import modelo.Jogador;
import modelo.Pergunta;
import modelo.ResultadoPartida;
import modelo.ResultadoResposta;
import modelo.SituacaoPartida;
import modelo.SituacaoResposta;

/**
 * Objeto remoto do servidor: mantem o estado da partida e aplica as regras.
 *
 * <h3>Concorrencia</h3>
 * O RMI atende cada chamada remota em uma thread propria, entao dois jogadores
 * podem chamar {@link #enviarResposta} ao mesmo tempo. Todos os metodos que
 * leem ou alteram o estado sao {@code synchronized}: o primeiro a obter a
 * trava e o primeiro a responder, o que garante um unico vencedor por rodada.
 *
 * <h3>Callbacks</h3>
 * As chamadas ao cliente (callbacks) NAO sao feitas dentro da trava. Elas sao
 * enfileiradas em um executor de thread unica ({@code notificador}), o que:
 * <ul>
 *   <li>mantem a ordem dos eventos enviados a cada cliente;</li>
 *   <li>evita que um cliente lento ou desconectado trave o servidor;</li>
 *   <li>permite tratar a {@link RemoteException} de um callback como queda do jogador.</li>
 * </ul>
 */
public class ServidorJogoImpl extends UnicastRemoteObject implements InterfaceServidorJogo {

    private static final long serialVersionUID = 1L;

    /** Acao executada sobre o stub de um cliente (callback). */
    private interface Callback {
        void executar(InterfaceClienteJogo cliente) throws RemoteException;
    }

    private final Map<String, Jogador> jogadores = new LinkedHashMap<>();
    private final Map<String, InterfaceClienteJogo> clientes = new HashMap<>();
    private final Set<String> erraramNaRodada = new HashSet<>();

    private final GeradorPerguntas gerador;
    private final transient ExecutorService notificador = Executors.newSingleThreadExecutor(threads("notificador-callbacks"));
    private final transient ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(threads("agendador-rodadas"));

    private SituacaoPartida situacao = SituacaoPartida.AGUARDANDO_JOGADORES;
    private Pergunta perguntaAtual;
    private int numeroPergunta;
    private boolean rodadaAberta;

    /** Incrementado a cada partida: invalida tarefas agendadas de partidas anteriores. */
    private int idPartida;
    /** Contador de partidas jogadas (apenas para o log). */
    private int partidasJogadas;
    private ScheduledFuture<?> timerRodada;
    private ScheduledFuture<?> timerPartida;

    /**
     * @param porta porta em que o objeto remoto e exportado (pode ser a mesma do registry)
     */
    public ServidorJogoImpl(int porta) throws RemoteException {
        this(porta, new GeradorPerguntas());
    }

    public ServidorJogoImpl(int porta, GeradorPerguntas gerador) throws RemoteException {
        super(porta);
        this.gerador = gerador;
    }

    // ------------------------------------------------------------------
    // Metodos remotos (InterfaceServidorJogo)
    // ------------------------------------------------------------------

    @Override
    public synchronized String entrarNaPartida(Jogador jogador, InterfaceClienteJogo cliente) throws RemoteException {
        if (cliente == null) {
            throw new IllegalArgumentException("Objeto de callback do cliente não informado.");
        }
        String nome = (jogador == null || jogador.getNome() == null) ? "" : jogador.getNome().trim();
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("Informe um nome de jogador.");
        }
        if (nome.length() > 20) {
            nome = nome.substring(0, 20);
        }

        if (situacao == SituacaoPartida.FINALIZADA) {
            reiniciar();
        }
        if (situacao == SituacaoPartida.EM_ANDAMENTO || jogadores.size() >= Configuracao.JOGADORES_POR_PARTIDA) {
            throw new IllegalStateException("Já existe uma partida em andamento. Tente novamente mais tarde.");
        }
        for (Jogador existente : jogadores.values()) {
            if (existente.getNome().equalsIgnoreCase(nome)) {
                throw new IllegalArgumentException("Já existe um jogador com o nome \"" + nome + "\".");
            }
        }

        String identificador = UUID.randomUUID().toString();
        jogadores.put(identificador, new Jogador(identificador, nome));
        clientes.put(identificador, cliente);
        log("Jogador conectado: " + nome + " (" + jogadores.size() + "/" + Configuracao.JOGADORES_POR_PARTIDA + ")");

        if (jogadores.size() < Configuracao.JOGADORES_POR_PARTIDA) {
            notificar(identificador, c -> c.informarAguardandoJogador());
        } else {
            iniciarPartida();
        }
        return identificador;
    }

    @Override
    public synchronized void enviarResposta(String identificadorJogador, int resposta) throws RemoteException {
        Jogador jogador = buscarJogador(identificadorJogador);

        if (situacao != SituacaoPartida.EM_ANDAMENTO || perguntaAtual == null) {
            responder(identificadorJogador, SituacaoResposta.PARTIDA_NAO_INICIADA,
                    "A partida ainda não começou.", jogador);
            return;
        }
        if (!rodadaAberta) {
            responder(identificadorJogador, SituacaoResposta.RODADA_ENCERRADA,
                    "Tarde demais! Esta rodada já foi encerrada.", jogador);
            return;
        }
        if (erraramNaRodada.contains(identificadorJogador)) {
            responder(identificadorJogador, SituacaoResposta.RODADA_ENCERRADA,
                    "Você já respondeu esta pergunta. Aguarde a próxima.", jogador);
            return;
        }

        int correta = perguntaAtual.getRespostaCorreta();
        if (perguntaAtual.verificarResposta(resposta)) {
            // Primeiro acerto da rodada: ponto para o jogador e rodada encerrada.
            jogador.adicionarPonto();
            fecharRodada();
            log("Rodada " + numeroPergunta + ": " + jogador.getNome() + " acertou (" + resposta + ")");
            responder(identificadorJogador, SituacaoResposta.CORRETA,
                    "Correto! +1 ponto. (" + perguntaAtual.getExpressao() + " = " + correta + ")", jogador);
            for (String outro : outrosJogadores(identificadorJogador)) {
                responder(outro, SituacaoResposta.RODADA_ENCERRADA,
                        jogador.getNome() + " acertou primeiro! A resposta era " + correta + ".", jogadores.get(outro));
            }
            transmitirEstado();
            agendarProximaRodada();
        } else {
            erraramNaRodada.add(identificadorJogador);
            log("Rodada " + numeroPergunta + ": " + jogador.getNome() + " errou (" + resposta + ")");
            responder(identificadorJogador, SituacaoResposta.INCORRETA,
                    "Resposta errada! Aguarde a próxima pergunta.", jogador);

            if (erraramNaRodada.size() >= jogadores.size()) {
                // Todos erraram: ninguem pontua e a expressao e trocada.
                fecharRodada();
                for (String id : jogadores.keySet()) {
                    responder(id, SituacaoResposta.RODADA_ENCERRADA,
                            "Ninguém acertou. A resposta era " + correta + ".", jogadores.get(id));
                }
                transmitirEstado();
                agendarProximaRodada();
            }
        }
    }

    @Override
    public synchronized EstadoPartida consultarEstado(String identificadorJogador) throws RemoteException {
        buscarJogador(identificadorJogador);
        return fotografarEstado();
    }

    @Override
    public synchronized void sairDaPartida(String identificadorJogador) throws RemoteException {
        removerJogador(identificadorJogador, "saiu da partida");
    }

    // ------------------------------------------------------------------
    // Ciclo da partida
    // ------------------------------------------------------------------

    private void iniciarPartida() {
        idPartida++;
        situacao = SituacaoPartida.EM_ANDAMENTO;
        numeroPergunta = 0;
        perguntaAtual = null;
        rodadaAberta = false;
        for (Jogador j : jogadores.values()) {
            j.zerarPontuacao();
        }
        partidasJogadas++;
        log("Partida #" + partidasJogadas + " iniciada: " + nomes());

        final EstadoPartida estado = fotografarEstado();
        notificarTodos(c -> c.iniciarPartida(estado));

        final int partida = idPartida;
        timerPartida = agendador.schedule(() -> encerrarPorTempo(partida),
                Configuracao.SEGUNDOS_PARTIDA, TimeUnit.SECONDS);
        agendarProximaRodada();
    }

    private void agendarProximaRodada() {
        final int partida = idPartida;
        agendador.schedule(() -> iniciarRodada(partida),
                Configuracao.PAUSA_ENTRE_RODADAS_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void iniciarRodada(int partida) {
        if (partida != idPartida || situacao != SituacaoPartida.EM_ANDAMENTO) {
            return; // tarefa de uma partida que ja acabou
        }
        if (numeroPergunta >= Configuracao.TOTAL_RODADAS) {
            finalizarPartida("todas as perguntas foram respondidas");
            return;
        }
        numeroPergunta++;
        perguntaAtual = gerador.gerar();
        rodadaAberta = true;
        erraramNaRodada.clear();
        // No log os simbolos x e / evitam caracteres estranhos no console do Windows.
        log("Rodada " + numeroPergunta + ": " + perguntaAtual.getExpressao().replace('×', 'x').replace('÷', '/')
                + " = " + perguntaAtual.getRespostaCorreta());

        final Pergunta pergunta = perguntaAtual;
        final int numero = numeroPergunta;
        notificarTodos(c -> c.receberNovaPergunta(pergunta, numero));

        timerRodada = agendador.schedule(() -> encerrarRodadaPorTempo(partida, numero),
                Configuracao.SEGUNDOS_POR_RODADA, TimeUnit.SECONDS);
    }

    private synchronized void encerrarRodadaPorTempo(int partida, int numero) {
        if (partida != idPartida || numero != numeroPergunta || !rodadaAberta) {
            return;
        }
        fecharRodada();
        int correta = perguntaAtual.getRespostaCorreta();
        log("Rodada " + numero + ": tempo esgotado");
        for (String id : jogadores.keySet()) {
            responder(id, SituacaoResposta.RODADA_ENCERRADA,
                    "Tempo esgotado! A resposta era " + correta + ".", jogadores.get(id));
        }
        transmitirEstado();
        agendarProximaRodada();
    }

    private synchronized void encerrarPorTempo(int partida) {
        if (partida != idPartida || situacao != SituacaoPartida.EM_ANDAMENTO) {
            return;
        }
        finalizarPartida("tempo da partida esgotado");
    }

    private void fecharRodada() {
        rodadaAberta = false;
        cancelar(timerRodada);
        timerRodada = null;
    }

    private void finalizarPartida(String motivo) {
        situacao = SituacaoPartida.FINALIZADA;
        fecharRodada();
        cancelar(timerPartida);
        timerPartida = null;

        List<Jogador> placar = copiarJogadores();
        if (placar.isEmpty()) {
            return;
        }
        Jogador vencedor = null;
        boolean empate = false;
        for (Jogador j : placar) {
            if (vencedor == null || j.getPontuacao() > vencedor.getPontuacao()) {
                vencedor = j;
                empate = false;
            } else if (j.getPontuacao() == vencedor.getPontuacao()) {
                empate = true;
            }
        }
        if (empate) {
            vencedor = null;
        }
        log("Partida #" + partidasJogadas + " finalizada (" + motivo + "). "
                + (empate ? "Empate." : "Vencedor: " + vencedor.getNome()));

        final ResultadoPartida resultado = new ResultadoPartida(vencedor, placar, empate);
        notificarTodos(c -> c.finalizarPartida(resultado));
    }

    private void reiniciar() {
        jogadores.clear();
        clientes.clear();
        erraramNaRodada.clear();
        situacao = SituacaoPartida.AGUARDANDO_JOGADORES;
        perguntaAtual = null;
        numeroPergunta = 0;
        rodadaAberta = false;
        idPartida++;
    }

    /** Remove o jogador; se a partida estava em andamento, o adversario vence por W.O. */
    private synchronized void removerJogador(String identificador, String motivo) {
        Jogador removido = jogadores.remove(identificador);
        clientes.remove(identificador);
        erraramNaRodada.remove(identificador);
        if (removido == null) {
            return;
        }
        log("Jogador " + removido.getNome() + " " + motivo + ".");

        if (situacao == SituacaoPartida.EM_ANDAMENTO) {
            notificarTodos(c -> c.informarDesconexaoAdversario());
            finalizarPartida("desconexao de " + removido.getNome());
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Jogador buscarJogador(String identificador) {
        Jogador jogador = identificador == null ? null : jogadores.get(identificador);
        if (jogador == null) {
            throw new IllegalArgumentException("Jogador não encontrado nesta partida.");
        }
        return jogador;
    }

    private void responder(String id, SituacaoResposta situacaoResposta, String mensagem, Jogador jogador) {
        final ResultadoResposta resultado = new ResultadoResposta(situacaoResposta, mensagem, jogador.getPontuacao());
        notificar(id, c -> c.informarResultadoResposta(resultado));
    }

    private void transmitirEstado() {
        final EstadoPartida estado = fotografarEstado();
        notificarTodos(c -> c.atualizarEstadoPartida(estado));
    }

    /** Copia imutavel do estado atual, segura para ser serializada fora da trava. */
    private EstadoPartida fotografarEstado() {
        return new EstadoPartida(situacao, perguntaAtual, numeroPergunta, copiarJogadores());
    }

    private List<Jogador> copiarJogadores() {
        List<Jogador> copias = new ArrayList<>();
        for (Jogador original : jogadores.values()) {
            Jogador copia = new Jogador(original.getIdentificador(), original.getNome());
            for (int i = 0; i < original.getPontuacao(); i++) {
                copia.adicionarPonto();
            }
            copias.add(copia);
        }
        return copias;
    }

    private List<String> outrosJogadores(String identificador) {
        List<String> outros = new ArrayList<>(jogadores.keySet());
        outros.remove(identificador);
        return outros;
    }

    private void notificarTodos(Callback callback) {
        for (String id : new ArrayList<>(clientes.keySet())) {
            notificar(id, callback);
        }
    }

    /** Enfileira um callback para o cliente; falha de comunicacao remove o jogador. */
    private void notificar(String identificador, Callback callback) {
        final InterfaceClienteJogo cliente = clientes.get(identificador);
        if (cliente == null) {
            return;
        }
        notificador.submit(() -> {
            try {
                callback.executar(cliente);
            } catch (RemoteException e) {
                log("Falha ao contatar cliente: " + e.getMessage());
                removerJogador(identificador, "perdeu a conexao");
            } catch (RuntimeException e) {
                log("Erro no callback: " + e);
            }
        });
    }

    private String nomes() {
        List<String> nomes = new ArrayList<>();
        for (Jogador j : jogadores.values()) {
            nomes.add(j.getNome());
        }
        return String.join(" x ", nomes);
    }

    private static void cancelar(ScheduledFuture<?> tarefa) {
        if (tarefa != null) {
            tarefa.cancel(false);
        }
    }

    private static ThreadFactory threads(String nome) {
        return r -> {
            Thread t = new Thread(r, nome);
            t.setDaemon(true);
            return t;
        };
    }

    static void log(String mensagem) {
        System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + mensagem);
    }

    /** Encerra os executores internos (usado ao desligar o servidor). */
    public void encerrar() {
        agendador.shutdownNow();
        notificador.shutdownNow();
    }
}

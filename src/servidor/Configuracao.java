package servidor;

/**
 * Parametros do jogo. Todos podem ser alterados na linha de comando
 * com -Dnome=valor (ex.: -Djogo.rodadas=15).
 */
public final class Configuracao {

    /** Porta do RMI Registry (e tambem do objeto remoto do servidor). */
    public static final int PORTA_PADRAO = comunicacao.InterfaceServidorJogo.PORTA_PADRAO;

    /** Nome com que o objeto remoto e registrado no RMI Registry. */
    public static final String NOME_SERVICO = comunicacao.InterfaceServidorJogo.NOME_SERVICO;

    /** Quantidade maxima de perguntas (rodadas) por partida. */
    public static final int TOTAL_RODADAS = Integer.getInteger("jogo.rodadas", 10);

    /** Tempo limite de cada rodada, em segundos. */
    public static final int SEGUNDOS_POR_RODADA = Integer.getInteger("jogo.segundosRodada", 20);

    /** Duracao maxima da partida, em segundos (o tempo estabelecido do jogo). */
    public static final int SEGUNDOS_PARTIDA = Integer.getInteger("jogo.segundosPartida", 180);

    /** Pausa entre o fim de uma rodada e a proxima pergunta, em milissegundos. */
    public static final int PAUSA_ENTRE_RODADAS_MS = Integer.getInteger("jogo.pausaMs", 1500);

    /** Numero de jogadores por partida. */
    public static final int JOGADORES_POR_PARTIDA = 2;

    private Configuracao() {
    }
}

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cliente.ControladorCliente;
import cliente.OuvinteJogo;
import modelo.EstadoPartida;
import modelo.Pergunta;
import modelo.ResultadoPartida;
import modelo.ResultadoResposta;

/**
 * Teste de integracao sem interface grafica: dois "robos" jogam uma partida
 * contra um servidor real (outro processo) via RMI.
 *
 * Uso:
 *   java -cp bin;teste-bin TesteIntegracao              -> sobe um servidor de teste neste
 *                                                         processo (porta 1199, partidas curtas)
 *   java -cp bin;teste-bin TesteIntegracao host porta   -> usa um servidor ja em execucao
 *                                                         (inicie-o com -Djogo.rodadas=5 -Djogo.pausaMs=300)
 */
public class TesteIntegracao {

    /** Robo que registra os eventos e responde automaticamente. */
    static class Robo implements OuvinteJogo {
        final String nome;
        final boolean acerta;
        final long atrasoMs;
        final ControladorCliente controlador = new ControladorCliente();
        final List<String> eventos = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch fim = new CountDownLatch(1);
        volatile ResultadoPartida resultado;
        volatile boolean wo;

        Robo(String nome, boolean acerta, long atrasoMs) {
            this.nome = nome;
            this.acerta = acerta;
            this.atrasoMs = atrasoMs;
        }

        public void informarAguardandoJogador() { eventos.add("AGUARDANDO"); }
        public void iniciarPartida(EstadoPartida e) { eventos.add("INICIO " + e.getJogadores().size()); }
        public void atualizarEstadoPartida(EstadoPartida e) { eventos.add("ESTADO"); }
        public void informarDesconexaoAdversario() { wo = true; eventos.add("WO"); }

        public void receberNovaPergunta(Pergunta p, int n) {
            eventos.add("PERGUNTA " + n + " " + p.getExpressao() + " gabarito-recebido=" + p.getRespostaCorreta());
            new Thread(() -> {
                try {
                    Thread.sleep(atrasoMs);
                    int certa = avaliar(p.getExpressao());
                    controlador.enviarResposta(acerta ? certa : certa + 1);
                } catch (Exception ex) {
                    eventos.add("ERRO " + ex);
                }
            }).start();
        }

        public void informarResultadoResposta(ResultadoResposta r) {
            eventos.add("RESULTADO " + r.getSituacao() + " " + r.getPontuacaoAtual() + " " + r.getMensagem());
        }

        public void finalizarPartida(ResultadoPartida r) {
            resultado = r;
            eventos.add("FIM");
            fim.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        String host;
        int porta;
        if (args.length >= 2) {
            host = args[0];
            porta = Integer.parseInt(args[1]);
        } else {
            // Servidor de teste no mesmo processo (a comunicacao continua sendo RMI via TCP).
            // As propriedades precisam ser definidas antes de a classe Configuracao ser carregada.
            System.setProperty("jogo.rodadas", "5");
            System.setProperty("jogo.pausaMs", "300");
            System.setProperty("jogo.segundosRodada", "4");
            host = "127.0.0.1";
            porta = 1199;
            servidor.ServidorJogo.main(new String[] {host, String.valueOf(porta)});
        }
        int falhas = 0;

        // 1) Partida normal: Ana sempre acerta (rapida), Bruno sempre erra.
        Robo ana = new Robo("Ana", true, 50);
        Robo bruno = new Robo("Bruno", false, 10);
        ana.controlador.conectar(host, porta, ana.nome, ana, Runnable::run);
        bruno.controlador.conectar(host, porta, bruno.nome, bruno, Runnable::run);
        ana.fim.await(60, TimeUnit.SECONDS);
        bruno.fim.await(5, TimeUnit.SECONDS);
        falhas += verificar("partida termina para os dois", ana.resultado != null && bruno.resultado != null);
        falhas += verificar("Ana vence", ana.resultado != null && !ana.resultado.isEmpate()
                && "Ana".equals(ana.resultado.getVencedor().getNome()));
        falhas += verificar("gabarito NAO trafega para o cliente",
                ana.eventos.stream().filter(e -> e.startsWith("PERGUNTA")).allMatch(e -> e.endsWith("=0")));
        falhas += verificar("Bruno recebe INCORRETA",
                bruno.eventos.stream().anyMatch(e -> e.startsWith("RESULTADO INCORRETA")));
        System.out.println("Placar final: " + placar(ana.resultado));

        // 2) Corrida: os dois acertam ao mesmo tempo -> apenas 1 ponto por rodada.
        Robo c1 = new Robo("Caio", true, 0);
        Robo c2 = new Robo("Duda", true, 0);
        c1.controlador.conectar(host, porta, c1.nome, c1, Runnable::run);
        c2.controlador.conectar(host, porta, c2.nome, c2, Runnable::run);
        c1.fim.await(60, TimeUnit.SECONDS);
        c2.fim.await(5, TimeUnit.SECONDS);
        int rodadas = (int) c1.eventos.stream().filter(e -> e.startsWith("PERGUNTA")).count();
        int soma = c1.resultado.getJogadores().stream().mapToInt(j -> j.getPontuacao()).sum();
        falhas += verificar("corrida: soma dos pontos (" + soma + ") == rodadas (" + rodadas + ")", soma == rodadas);
        falhas += verificar("corrida: perdedor recebe RODADA_ENCERRADA",
                c1.eventos.stream().anyMatch(e -> e.startsWith("RESULTADO RODADA_ENCERRADA"))
                || c2.eventos.stream().anyMatch(e -> e.startsWith("RESULTADO RODADA_ENCERRADA")));
        System.out.println("Placar final: " + placar(c1.resultado));

        // 3) Desconexao: Eva sai no meio da partida -> Fabio vence por W.O.
        Robo eva = new Robo("Eva", true, 2000);
        Robo fabio = new Robo("Fabio", false, 5000);
        eva.controlador.conectar(host, porta, eva.nome, eva, Runnable::run);
        fabio.controlador.conectar(host, porta, fabio.nome, fabio, Runnable::run);
        Thread.sleep(1000);
        eva.controlador.sair();
        fabio.fim.await(10, TimeUnit.SECONDS);
        falhas += verificar("W.O.: Fabio avisado", fabio.wo);
        falhas += verificar("W.O.: Fabio vence", fabio.resultado != null
                && "Fabio".equals(fabio.resultado.getVencedor().getNome()));

        // 4) Nome vazio e terceira pessoa com partida cheia
        Robo g = new Robo("Gil", false, 99999), h = new Robo("Hel", false, 99999), i = new Robo("Ivo", false, 99999);
        g.controlador.conectar(host, porta, g.nome, g, Runnable::run);
        h.controlador.conectar(host, porta, h.nome, h, Runnable::run);
        String erro = null;
        try {
            i.controlador.conectar(host, porta, i.nome, i, Runnable::run);
        } catch (Exception e) {
            erro = ControladorCliente.mensagemAmigavel(e, host, porta);
        }
        falhas += verificar("terceiro jogador recusado: " + erro, erro != null && erro.contains("andamento"));
        g.controlador.sair();
        h.controlador.sair();
        i.controlador.sair();

        System.out.println();
        System.out.println(falhas == 0 ? "TODOS OS TESTES PASSARAM" : falhas + " TESTE(S) FALHARAM");
        System.exit(falhas == 0 ? 0 : 1);
    }

    static int verificar(String descricao, boolean ok) {
        System.out.println((ok ? "[OK]    " : "[FALHA] ") + descricao);
        return ok ? 0 : 1;
    }

    static String placar(ResultadoPartida r) {
        if (r == null) return "-";
        StringBuilder sb = new StringBuilder();
        r.getJogadores().forEach(j -> sb.append(j.getNome()).append('=').append(j.getPontuacao()).append(' '));
        return sb.toString().trim();
    }

    /** Avalia a expressao exibida (respeitando a precedencia de x e ÷). */
    static int avaliar(String expr) {
        String[] t = expr.replace('×', '*').replace('÷', '/').split(" ");
        List<String> termos = new ArrayList<>();
        int i = 0;
        int atual = Integer.parseInt(t[0]);
        while (i + 2 < t.length + 1 && i + 1 < t.length) {
            String op = t[i + 1];
            int b = Integer.parseInt(t[i + 2]);
            if (op.equals("*")) atual *= b;
            else if (op.equals("/")) atual /= b;
            else { termos.add(String.valueOf(atual)); termos.add(op); atual = b; }
            i += 2;
        }
        termos.add(String.valueOf(atual));
        int r = Integer.parseInt(termos.get(0));
        for (int k = 1; k < termos.size(); k += 2) {
            int v = Integer.parseInt(termos.get(k + 1));
            r = termos.get(k).equals("+") ? r + v : r - v;
        }
        return r;
    }
}

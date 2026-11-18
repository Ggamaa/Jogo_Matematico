package cliente;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import comunicacao.InterfaceServidorJogo;
import modelo.EstadoPartida;
import modelo.Jogador;
import modelo.Pergunta;
import modelo.ResultadoPartida;
import modelo.ResultadoResposta;
import modelo.SituacaoResposta;

/**
 * Janela do jogador (Swing).
 *
 * Regras de threads:
 * <ul>
 *   <li>Eventos do servidor chegam pelo callback e sao entregues aqui na EDT.</li>
 *   <li>Chamadas remotas ao servidor rodam no executor {@code rede}, nunca na EDT,
 *       para a janela nao congelar enquanto espera a rede.</li>
 * </ul>
 */
public class TelaJogo extends JFrame implements OuvinteJogo {

    private static final long serialVersionUID = 1L;

    private static final Color VERDE = new Color(0x1B8A3A);
    private static final Color VERMELHO = new Color(0xC62828);
    private static final Color LARANJA = new Color(0xE07B00);
    private static final Color AZUL = new Color(0x1F4E9C);

    private final transient ControladorCliente controlador = new ControladorCliente();
    private final transient ExecutorService rede = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chamadas-rmi");
        t.setDaemon(true);
        return t;
    });

    private final JLabel lblVoce = new JLabel("Você: -", SwingConstants.LEFT);
    private final JLabel lblAdversario = new JLabel("Adversário: -", SwingConstants.RIGHT);
    private final JLabel lblRodada = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel lblTempo = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel lblExpressao = new JLabel("Conectando...", SwingConstants.CENTER);
    private final JLabel lblFeedback = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel lblStatus = new JLabel(" ");
    private final JTextField campoResposta = new JTextField(8);
    private final JButton btnResponder = new JButton("Responder");
    private final JTextArea historico = new JTextArea(8, 40);

    /** Cronometro local da pergunta atual (mostra quanto tempo o jogador esta levando). */
    private final Timer cronometro = new Timer(100, e -> atualizarCronometro());
    /** Verificacao periodica de que o servidor continua acessivel. */
    private final Timer verificador = new Timer(5000, e -> verificarConexao());

    private long inicioPergunta;
    private String host = "localhost";
    private int porta = InterfaceServidorJogo.PORTA_PADRAO;
    private String nome = "";
    private boolean partidaAtiva;

    public TelaJogo() {
        this(null, InterfaceServidorJogo.PORTA_PADRAO, null);
    }

    /** Cria a tela com os campos de conexao ja preenchidos (valores podem ser null). */
    public TelaJogo(String hostInicial, int portaInicial, String nomeInicial) {
        super("Jogo da Matemática");
        if (hostInicial != null) {
            host = hostInicial;
        }
        porta = portaInicial;
        if (nomeInicial != null) {
            nome = nomeInicial;
        }
        montarTela();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                encerrar();
            }
        });
    }

    /** Exibe a janela e pede os dados de conexao. */
    public void iniciar() {
        setVisible(true);
        pedirDadosConexao();
    }

    // ------------------------------------------------------------------
    // Montagem da interface
    // ------------------------------------------------------------------

    private void montarTela() {
        JPanel raiz = new JPanel(new BorderLayout(10, 10));
        raiz.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel placar = new JPanel(new GridLayout(1, 2, 10, 0));
        lblVoce.setFont(lblVoce.getFont().deriveFont(Font.BOLD, 16f));
        lblVoce.setForeground(AZUL);
        lblAdversario.setFont(lblAdversario.getFont().deriveFont(Font.BOLD, 16f));
        placar.add(lblVoce);
        placar.add(lblAdversario);

        JPanel centro = new JPanel(new GridLayout(4, 1, 0, 4));
        lblRodada.setFont(lblRodada.getFont().deriveFont(Font.PLAIN, 14f));
        lblExpressao.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        lblFeedback.setFont(lblFeedback.getFont().deriveFont(Font.BOLD, 15f));
        lblTempo.setFont(lblTempo.getFont().deriveFont(Font.PLAIN, 13f));
        centro.add(lblRodada);
        centro.add(lblExpressao);
        centro.add(lblTempo);
        centro.add(lblFeedback);

        JPanel entrada = new JPanel();
        campoResposta.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
        campoResposta.setHorizontalAlignment(SwingConstants.CENTER);
        btnResponder.setFont(btnResponder.getFont().deriveFont(Font.BOLD, 15f));
        campoResposta.addActionListener(e -> responder()); // Enter envia
        btnResponder.addActionListener(e -> responder());
        entrada.add(new JLabel("Sua resposta:"));
        entrada.add(campoResposta);
        entrada.add(btnResponder);
        habilitarResposta(false);

        JPanel meio = new JPanel(new BorderLayout());
        meio.add(centro, BorderLayout.CENTER);
        meio.add(entrada, BorderLayout.SOUTH);

        historico.setEditable(false);
        historico.setLineWrap(true);
        historico.setWrapStyleWord(true);
        JScrollPane rolagem = new JScrollPane(historico);
        rolagem.setBorder(BorderFactory.createTitledBorder("Histórico"));

        JPanel sul = new JPanel(new BorderLayout());
        sul.add(rolagem, BorderLayout.CENTER);
        lblStatus.setForeground(Color.GRAY);
        sul.add(lblStatus, BorderLayout.SOUTH);

        raiz.add(placar, BorderLayout.NORTH);
        raiz.add(meio, BorderLayout.CENTER);
        raiz.add(sul, BorderLayout.SOUTH);
        setContentPane(raiz);
        setMinimumSize(new Dimension(560, 520));
        pack();
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    // Conexao
    // ------------------------------------------------------------------

    private void pedirDadosConexao() {
        JTextField campoHost = new JTextField(host, 15);
        JTextField campoPorta = new JTextField(String.valueOf(porta), 6);
        JTextField campoNome = new JTextField(nome, 15);
        JPanel painel = new JPanel(new GridLayout(3, 2, 6, 6));
        painel.add(new JLabel("IP do servidor:"));
        painel.add(campoHost);
        painel.add(new JLabel("Porta:"));
        painel.add(campoPorta);
        painel.add(new JLabel("Seu nome:"));
        painel.add(campoNome);

        while (true) {
            int opcao = JOptionPane.showConfirmDialog(this, painel, "Conectar ao servidor",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcao != JOptionPane.OK_OPTION) {
                encerrar();
                return;
            }
            String h = campoHost.getText().trim();
            String n = campoNome.getText().trim();
            int p;
            try {
                p = Integer.parseInt(campoPorta.getText().trim());
            } catch (NumberFormatException e) {
                mostrarErro("Porta inválida.");
                continue;
            }
            if (h.isEmpty() || n.isEmpty()) {
                mostrarErro("Preencha o IP do servidor e o seu nome.");
                continue;
            }
            host = h;
            porta = p;
            nome = n;
            break;
        }
        conectar();
    }

    private void conectar() {
        lblExpressao.setText("Conectando...");
        lblStatus.setText("Conectando a " + host + ":" + porta + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                controlador.conectar(host, porta, nome, TelaJogo.this);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    setTitle("Jogo da Matemática - " + nome);
                    lblStatus.setText("Conectado a " + host + ":" + porta + " via Java RMI");
                    verificador.start();
                } catch (Exception e) {
                    Throwable causa = e.getCause() != null ? e.getCause() : e;
                    lblStatus.setText("Desconectado");
                    mostrarErro(ControladorCliente.mensagemAmigavel(causa, host, porta));
                    pedirDadosConexao();
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------
    // Acoes do jogador
    // ------------------------------------------------------------------

    private void responder() {
        if (!btnResponder.isEnabled()) {
            return;
        }
        String texto = campoResposta.getText().trim();
        final int valor;
        try {
            valor = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            mostrarFeedback("Digite um número inteiro.", LARANJA);
            campoResposta.selectAll();
            return;
        }
        habilitarResposta(false);
        rede.submit(() -> {
            try {
                controlador.enviarResposta(valor);
            } catch (RemoteException e) {
                SwingUtilities.invokeLater(() -> conexaoPerdida(e));
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> mostrarFeedback(e.getMessage(), VERMELHO));
            }
        });
    }

    private void jogarNovamente() {
        limparPartida();
        lblExpressao.setText("Entrando...");
        rede.submit(() -> {
            try {
                controlador.jogarNovamente();
                SwingUtilities.invokeLater(verificador::start);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    mostrarErro(ControladorCliente.mensagemAmigavel(e, host, porta));
                    pedirDadosConexao();
                });
            }
        });
    }

    private void verificarConexao() {
        if (controlador.getIdentificador() == null) {
            return;
        }
        rede.submit(() -> {
            try {
                controlador.consultarEstado();
            } catch (RemoteException e) {
                SwingUtilities.invokeLater(() -> conexaoPerdida(e));
            } catch (RuntimeException e) {
                // jogador nao existe mais no servidor (ex.: servidor reiniciado)
                SwingUtilities.invokeLater(() -> conexaoPerdida(e));
            }
        });
    }

    private void conexaoPerdida(Exception e) {
        if (!verificador.isRunning() && !partidaAtiva) {
            return; // ja tratado
        }
        verificador.stop();
        cronometro.stop();
        partidaAtiva = false;
        habilitarResposta(false);
        lblStatus.setText("Desconectado");
        lblExpressao.setText("Sem conexão");
        registrar("Conexão com o servidor perdida: " + ControladorCliente.mensagemAmigavel(e, host, porta));
        mostrarErro("A conexão com o servidor foi perdida.\n" + ControladorCliente.mensagemAmigavel(e, host, porta));
    }

    private void encerrar() {
        verificador.stop();
        cronometro.stop();
        // sair() faz uma chamada remota: executa fora da EDT e fecha em seguida
        Thread saida = new Thread(() -> {
            controlador.sair();
            System.exit(0);
        }, "saida");
        saida.start();
        dispose();
    }

    // ------------------------------------------------------------------
    // Eventos vindos do servidor (OuvinteJogo) - executados na EDT
    // ------------------------------------------------------------------

    @Override
    public void informarAguardandoJogador() {
        lblExpressao.setText("Aguardando adversário...");
        lblRodada.setText(" ");
        lblVoce.setText("Você (" + nome + "): 0");
        lblAdversario.setText("Adversário: aguardando");
        registrar("Conectado. Aguardando o segundo jogador...");
    }

    @Override
    public void iniciarPartida(EstadoPartida estado) {
        partidaAtiva = true;
        atualizarPlacar(estado.getJogadores());
        lblExpressao.setText("Prepare-se!");
        lblRodada.setText("A partida vai começar");
        mostrarFeedback(" ", Color.BLACK);
        registrar("Partida iniciada: " + nomesJogadores(estado.getJogadores()));
    }

    @Override
    public void receberNovaPergunta(Pergunta pergunta, int numeroPergunta) {
        lblRodada.setText("Pergunta " + numeroPergunta);
        lblExpressao.setText(pergunta.getExpressao() + " = ?");
        mostrarFeedback(" ", Color.BLACK);
        campoResposta.setText("");
        habilitarResposta(true);
        campoResposta.requestFocusInWindow();
        inicioPergunta = System.currentTimeMillis();
        cronometro.restart();
    }

    @Override
    public void informarResultadoResposta(ResultadoResposta resultado) {
        cronometro.stop();
        Color cor;
        SituacaoResposta s = resultado.getSituacao();
        if (s == SituacaoResposta.CORRETA) {
            cor = VERDE;
            registrar(resultado.getMensagem() + " Tempo: " + segundosDesdeInicio() + "s");
        } else if (s == SituacaoResposta.INCORRETA) {
            cor = VERMELHO;
            registrar(resultado.getMensagem());
        } else {
            cor = LARANJA;
            registrar(resultado.getMensagem());
        }
        mostrarFeedback(resultado.getMensagem(), cor);
        habilitarResposta(false); // aguarda a proxima pergunta
    }

    @Override
    public void atualizarEstadoPartida(EstadoPartida estado) {
        atualizarPlacar(estado.getJogadores());
    }

    @Override
    public void finalizarPartida(ResultadoPartida resultado) {
        partidaAtiva = false;
        verificador.stop();
        cronometro.stop();
        habilitarResposta(false);
        atualizarPlacar(resultado.getJogadores());

        String titulo;
        if (resultado.isEmpate()) {
            titulo = "Empate!";
        } else if (resultado.getVencedor() != null
                && resultado.getVencedor().getIdentificador().equals(controlador.getIdentificador())) {
            titulo = "Você venceu!";
        } else {
            titulo = (resultado.getVencedor() == null ? "?" : resultado.getVencedor().getNome()) + " venceu!";
        }
        lblExpressao.setText(titulo);
        lblRodada.setText("Fim de jogo");
        StringBuilder placarFinal = new StringBuilder();
        for (Jogador j : resultado.getJogadores()) {
            placarFinal.append(j.getNome()).append(": ").append(j.getPontuacao()).append(" ponto(s)\n");
        }
        registrar("Fim de jogo - " + titulo + " " + placarFinal.toString().replace('\n', ' ').trim());

        // Mostra o dialogo depois que a tela terminar de se atualizar.
        SwingUtilities.invokeLater(() -> {
            Object[] opcoes = {"Jogar novamente", "Sair"};
            int escolha = JOptionPane.showOptionDialog(this, placarFinal.toString(), titulo,
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opcoes, opcoes[0]);
            if (escolha == 0) {
                jogarNovamente();
            } else {
                encerrar();
            }
        });
    }

    @Override
    public void informarDesconexaoAdversario() {
        registrar("Seu adversário saiu ou perdeu a conexão. Vitória por W.O.");
        mostrarFeedback("O adversário desconectou.", LARANJA);
    }

    // ------------------------------------------------------------------
    // Auxiliares de interface
    // ------------------------------------------------------------------

    private void atualizarPlacar(List<Jogador> jogadores) {
        String meuId = controlador.getIdentificador();
        Jogador eu = null;
        Jogador outro = null;
        for (Jogador j : jogadores) {
            boolean souEu = meuId != null ? j.getIdentificador().equals(meuId) : j.getNome().equalsIgnoreCase(nome);
            if (souEu && eu == null) {
                eu = j;
            } else {
                outro = j;
            }
        }
        lblVoce.setText("Você (" + nome + "): " + (eu == null ? 0 : eu.getPontuacao()));
        lblAdversario.setText(outro == null ? "Adversário: -" : outro.getNome() + ": " + outro.getPontuacao());
    }

    private void limparPartida() {
        historico.setText("");
        mostrarFeedback(" ", Color.BLACK);
        lblRodada.setText(" ");
        lblTempo.setText(" ");
        lblAdversario.setText("Adversário: -");
    }

    private void habilitarResposta(boolean habilitar) {
        campoResposta.setEnabled(habilitar);
        btnResponder.setEnabled(habilitar);
    }

    private void atualizarCronometro() {
        lblTempo.setText("Tempo: " + segundosDesdeInicio() + "s");
    }

    private String segundosDesdeInicio() {
        long ms = System.currentTimeMillis() - inicioPergunta;
        return String.format("%.1f", ms / 1000.0);
    }

    private void mostrarFeedback(String texto, Color cor) {
        lblFeedback.setForeground(cor);
        lblFeedback.setText(texto);
    }

    private void registrar(String mensagem) {
        historico.append("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + mensagem + "\n");
        historico.setCaretPosition(historico.getDocument().getLength());
    }

    private void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Jogo da Matemática", JOptionPane.ERROR_MESSAGE);
    }

    private static String nomesJogadores(List<Jogador> jogadores) {
        StringBuilder sb = new StringBuilder();
        for (Jogador j : jogadores) {
            if (sb.length() > 0) {
                sb.append(" x ");
            }
            sb.append(j.getNome());
        }
        return sb.toString();
    }
}

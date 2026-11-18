package cliente;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import comunicacao.InterfaceServidorJogo;

/**
 * Ponto de entrada do cliente (jogador).
 *
 * Uso: java cliente.ClienteJogo [ip-do-servidor] [porta] [nome]
 * Os argumentos sao opcionais: apenas preenchem a tela de conexao, onde o
 * jogador confirma ou altera o IP do servidor, a porta e o seu nome.
 */
public class ClienteJogo {

    public static void main(String[] args) {
        final String host = args.length > 0 ? args[0] : null;
        final int porta = args.length > 1 ? Integer.parseInt(args[1]) : InterfaceServidorJogo.PORTA_PADRAO;
        final String nome = args.length > 2 ? args[2] : null;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignorado) {
                // usa o visual padrao do Java
            }
            new TelaJogo(host, porta, nome).iniciar();
        });
    }
}

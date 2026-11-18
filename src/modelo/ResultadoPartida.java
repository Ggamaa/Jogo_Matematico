package modelo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ResultadoPartida implements Serializable {

    private static final long serialVersionUID = 1L;

    private Jogador vencedor;
    private List<Jogador> jogadores;
    private boolean empate;

    public ResultadoPartida(
        Jogador vencedor,
        List<Jogador> jogadores,
        boolean empate
    ) {
        this.vencedor = vencedor;
        this.jogadores = new ArrayList<>(jogadores);
        this.empate = empate;
    }

    public Jogador getVencedor() {
        return vencedor;
    }

    public List<Jogador> getJogadores() {
        return new ArrayList<>(jogadores);
    }

    public boolean isEmpate() {
        return empate;
    }
}
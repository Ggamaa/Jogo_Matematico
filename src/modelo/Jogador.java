package modelo;

import java.io.Serializable;

public class Jogador implements Serializable {

    private static final long serialVersionUID = 1L;

    private String identificador;
    private String nome;
    private int pontuacao;

    public Jogador(String identificador, String nome) {
        this.identificador = identificador;
        this.nome = nome;
        this.pontuacao = 0;
    }

    public String getIdentificador() {
        return identificador;
    }

    public String getNome() {
        return nome;
    }

    public int getPontuacao() {
        return pontuacao;
    }

    public void adicionarPonto() {
        pontuacao++;
    }

    public void zerarPontuacao() {
        pontuacao = 0;
    }
}
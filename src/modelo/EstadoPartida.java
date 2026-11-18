package modelo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class EstadoPartida implements Serializable {

	private static final long serialVersionUID = 1L;

	private SituacaoPartida situacao;
	private Pergunta perguntaAtual;
	private int numeroPergunta;
	private List<Jogador> jogadores;

	public EstadoPartida(SituacaoPartida situacao, Pergunta perguntaAtual, int numeroPergunta,List<Jogador> jogadores) {
		this.situacao = situacao;
		this.perguntaAtual = perguntaAtual;
		this.numeroPergunta = numeroPergunta;
		this.jogadores = new ArrayList<>(jogadores);
	}

	public SituacaoPartida getSituacao() {
		return situacao;
	}

	public Pergunta getPerguntaAtual() {
		return perguntaAtual;
	}

	public int getNumeroPergunta() {
		return numeroPergunta;
	}

	public List<Jogador> getJogadores() {
		return new ArrayList<>(jogadores);
	}
}
package modelo;

import java.io.Serializable;

public class ResultadoResposta implements Serializable {

    private static final long serialVersionUID = 1L;

    private SituacaoResposta situacao;
    private String mensagem;
    private int pontuacaoAtual;

    public ResultadoResposta(
        SituacaoResposta situacao,
        String mensagem,
        int pontuacaoAtual
    ) {
        this.situacao = situacao;
        this.mensagem = mensagem;
        this.pontuacaoAtual = pontuacaoAtual;
    }

    public SituacaoResposta getSituacao() {
        return situacao;
    }

    public String getMensagem() {
        return mensagem;
    }

    public int getPontuacaoAtual() {
        return pontuacaoAtual;
    }
}
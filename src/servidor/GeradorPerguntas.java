package servidor;

import java.util.Random;

import modelo.Pergunta;

/**
 * Sorteia expressoes matematicas de nivel basico com resultado inteiro.
 *
 * Tipos gerados: soma, subtracao (resultado nao negativo), multiplicacao
 * (tabuada ate 12), divisao exata e, ocasionalmente, uma expressao com
 * duas operacoes respeitando a precedencia (ex.: 3 + 4 x 2).
 */
public class GeradorPerguntas {

    private final Random random;

    public GeradorPerguntas() {
        this(new Random());
    }

    /** Construtor com Random injetavel (util para testes reproduziveis). */
    public GeradorPerguntas(Random random) {
        this.random = random;
    }

    public Pergunta gerar() {
        int tipo = random.nextInt(10);
        if (tipo < 3) {
            return soma();
        } else if (tipo < 5) {
            return subtracao();
        } else if (tipo < 7) {
            return multiplicacao();
        } else if (tipo < 9) {
            return divisao();
        }
        return composta();
    }

    private Pergunta soma() {
        int a = entre(1, 50);
        int b = entre(1, 50);
        return new Pergunta(a + " + " + b, a + b);
    }

    private Pergunta subtracao() {
        int a = entre(5, 60);
        int b = entre(1, a);
        return new Pergunta(a + " - " + b, a - b);
    }

    private Pergunta multiplicacao() {
        int a = entre(2, 12);
        int b = entre(2, 12);
        return new Pergunta(a + " × " + b, a * b);
    }

    private Pergunta divisao() {
        int divisor = entre(2, 12);
        int quociente = entre(1, 12);
        int dividendo = divisor * quociente;
        return new Pergunta(dividendo + " ÷ " + divisor, quociente);
    }

    /** a + b x c  ou  a x b - c (sempre com resultado nao negativo). */
    private Pergunta composta() {
        if (random.nextBoolean()) {
            int a = entre(1, 20);
            int b = entre(2, 9);
            int c = entre(2, 9);
            return new Pergunta(a + " + " + b + " × " + c, a + b * c);
        }
        int a = entre(2, 9);
        int b = entre(2, 9);
        int c = entre(1, a * b);
        return new Pergunta(a + " × " + b + " - " + c, a * b - c);
    }

    private int entre(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}

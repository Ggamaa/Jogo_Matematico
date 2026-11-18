/**
 * Modulo do Jogo da Matematica (Java RMI).
 *
 * - comunicacao: interfaces remotas (contrato RMI entre cliente e servidor)
 * - modelo: objetos serializaveis trafegados pela rede
 * - servidor: implementacao do objeto remoto e regras do jogo
 * - cliente: interface grafica (Swing) e objeto de callback
 */
module JogoMatematico {
	requires java.rmi;
	requires java.desktop;

	exports comunicacao;
	exports modelo;
	exports servidor;
	exports cliente;
}

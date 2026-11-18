package servidor;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ponto de entrada do servidor.
 *
 * Uso: java servidor.ServidorJogo [ip-do-servidor] [porta]
 *
 * O IP informado e gravado em java.rmi.server.hostname, que e o endereco que
 * vai dentro do stub enviado aos clientes. Em maquinas com varias placas de
 * rede (Wi-Fi, VirtualBox, WSL...) informe o IP da rede local para que os
 * clientes de outras maquinas consigam conectar.
 */
public class ServidorJogo {

    public static void main(String[] args) throws Exception {
        String ip = args.length > 0 ? args[0] : null;
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : Configuracao.PORTA_PADRAO;

        // Precisa ser definido ANTES de qualquer uso do RMI.
        if (ip != null) {
            System.setProperty("java.rmi.server.hostname", ip);
        }

        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(porta);
            ServidorJogoImpl.log("RMI Registry criado na porta " + porta);
        } catch (ExportException e) {
            registry = LocateRegistry.getRegistry(porta);
            ServidorJogoImpl.log("Usando RMI Registry ja existente na porta " + porta);
        }

        // O objeto remoto e exportado na mesma porta do registry (uma unica porta no firewall).
        final ServidorJogoImpl servidor = new ServidorJogoImpl(porta);
        registry.rebind(Configuracao.NOME_SERVICO, servidor);

        final Registry reg = registry;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                reg.unbind(Configuracao.NOME_SERVICO);
                UnicastRemoteObject.unexportObject(servidor, true);
                servidor.encerrar();
            } catch (Exception ignorado) {
                // servidor ja esta sendo encerrado
            }
        }));

        System.out.println();
        System.out.println("==============================================");
        System.out.println("  JOGO DA MATEMATICA - servidor RMI no ar");
        System.out.println("==============================================");
        System.out.println("  Servico : " + Configuracao.NOME_SERVICO);
        System.out.println("  Porta   : " + porta);
        System.out.println("  Endereco anunciado: "
                + System.getProperty("java.rmi.server.hostname", InetAddress.getLocalHost().getHostAddress()));
        System.out.println("  IPs desta maquina : " + ipsLocais());
        System.out.println("  Rodadas: " + Configuracao.TOTAL_RODADAS
                + " | " + Configuracao.SEGUNDOS_POR_RODADA + "s por rodada"
                + " | partida de ate " + Configuracao.SEGUNDOS_PARTIDA + "s");
        System.out.println("  Aguardando jogadores... (Ctrl+C para encerrar)");
        System.out.println("==============================================");
        // As threads do RMI mantem o processo vivo.
    }

    private static List<String> ipsLocais() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress endereco : Collections.list(ni.getInetAddresses())) {
                    if (endereco instanceof Inet4Address) {
                        ips.add(endereco.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            ips.add("(nao foi possivel listar)");
        }
        return ips;
    }
}

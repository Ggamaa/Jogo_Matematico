# Jogo da Matemática — Java RMI

Jogo multiplayer (2 jogadores) em que os participantes competem para resolver
expressões matemáticas básicas. Arquitetura cliente-servidor com **Java RMI**
(stub, objeto remoto, RMI Registry e callback).

Integrantes: Davi Amorim Araujo · Gabriel Antonio Torres Diogo · Gustavo Do Vale Gama

## Requisitos
- JDK 11 ou superior (`java` e `javac` no PATH)

## Como executar (Windows)
Abra o terminal na pasta do repositório (onde está este README). No PowerShell, use `.\` antes do script:
```
cd C:\Users\<usuario>\OneDrive\Documentos\GitHub\Jogo_Matematico
.\testar.bat                 # teste automático (servidor + 2 robôs)
.\compilar.bat               # compila src\ em bin\
.\servidor.bat               # back-end: servidor local (porta 1099), deixe aberto
.\cliente.bat                # front-end: rode duas vezes, uma janela por jogador
```
No Prompt de Comando (cmd) ou com dois cliques no Explorer, o `.\` não é necessário.

### No Eclipse
File → Import → General → Existing Projects into Workspace → selecione a pasta `Jogo_Matematico`.
Rode `servidor.ServidorJogo` uma vez e `cliente.ClienteJogo` duas vezes (Run As → Java Application).

### Em rede local (duas ou três máquinas)
1. Na máquina do servidor: `servidor.bat 192.168.0.10` (use o IP dessa máquina na rede).
2. Libere a porta **1099/TCP** no firewall do servidor e permita o Java nas redes privadas
   (o cliente também recebe chamadas de volta — callback — numa porta dinâmica).
3. Em cada máquina de jogador: `cliente.bat 192.168.0.10` e informe o nome.

Linux/macOS: `compilar.sh`, `servidor.sh`, `cliente.sh` (mesmos argumentos).

## Configuração (opcional, no servidor)
| Propriedade | Padrão | Significado |
|---|---|---|
| `-Djogo.rodadas` | 10 | perguntas por partida |
| `-Djogo.segundosRodada` | 20 | tempo limite de cada pergunta |
| `-Djogo.segundosPartida` | 180 | duração máxima da partida |
| `-Djogo.pausaMs` | 1500 | pausa entre perguntas (ms) |

Ex.: `java -Djogo.rodadas=15 -cp bin servidor.ServidorJogo`

## Teste automático
`testar.bat` sobe um servidor de teste (porta 1199) e dois jogadores-robô e verifica:
vitória, resposta errada, corrida entre respostas simultâneas, saída de jogador (W.O.),
partida cheia e que o gabarito não é enviado ao cliente.

## Estrutura
```
src/
  comunicacao/  InterfaceServidorJogo, InterfaceClienteJogo   (contratos RMI)
  modelo/       Jogador, Pergunta, EstadoPartida, ResultadoResposta, ResultadoPartida, enums
  servidor/     ServidorJogo (main), ServidorJogoImpl (objeto remoto), GeradorPerguntas, Configuracao
  cliente/      ClienteJogo (main), TelaJogo (Swing), ClienteCallbackImpl (callback), ControladorCliente
teste/          TesteIntegracao
```
Detalhes completos no Manual Técnico.

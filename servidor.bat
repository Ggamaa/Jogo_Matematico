@echo off
REM Inicia o servidor. Uso: servidor.bat [ip-da-maquina-na-rede] [porta]
REM Exemplo para jogar em rede: servidor.bat 192.168.0.10
cd /d "%~dp0"
if not exist bin\servidor\ServidorJogo.class call compilar.bat
java -cp bin servidor.ServidorJogo %*
pause

@echo off
REM Abre a janela de um jogador. Uso: cliente.bat [ip-do-servidor] [porta] [nome]
cd /d "%~dp0"
if not exist bin\cliente\ClienteJogo.class call compilar.bat
start "" javaw -cp bin cliente.ClienteJogo %*

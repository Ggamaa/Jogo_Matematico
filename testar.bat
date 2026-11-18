@echo off
REM Executa o teste de integracao automatico (2 jogadores-robo via RMI).
cd /d "%~dp0"
call compilar.bat
if not exist teste-bin mkdir teste-bin
javac -encoding UTF-8 -cp bin -d teste-bin teste\TesteIntegracao.java
java -cp "bin;teste-bin" TesteIntegracao
pause

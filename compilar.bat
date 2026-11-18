@echo off
REM Compila o Jogo da Matematica (fontes em src\ -> classes em bin\)
cd /d "%~dp0"
if not exist bin mkdir bin
javac -encoding UTF-8 -d bin src\module-info.java src\comunicacao\*.java src\modelo\*.java src\servidor\*.java src\cliente\*.java
if errorlevel 1 (
  echo.
  echo ERRO na compilacao. Verifique se o JDK 11+ esta instalado e no PATH.
  pause
  exit /b 1
)
echo Compilado com sucesso em bin\

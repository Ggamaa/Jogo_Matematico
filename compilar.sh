#!/bin/sh
# Compila o Jogo da Matematica (Linux/macOS)
cd "$(dirname "$0")" && mkdir -p bin && javac -encoding UTF-8 -d bin $(find src -name '*.java') && echo "Compilado em bin/"

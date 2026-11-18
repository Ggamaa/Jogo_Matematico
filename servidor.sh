#!/bin/sh
# Uso: ./servidor.sh [ip-da-maquina-na-rede] [porta]
cd "$(dirname "$0")" && java -cp bin servidor.ServidorJogo "$@"

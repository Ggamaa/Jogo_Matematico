#!/bin/sh
# Uso: ./cliente.sh [ip-do-servidor] [porta] [nome]
cd "$(dirname "$0")" && java -cp bin cliente.ClienteJogo "$@"

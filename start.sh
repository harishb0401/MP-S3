#!/usr/bin/env bash
set -e

echo "==============================================================================="
echo "   STARTING MAKKAL NAGAR MUNICIPAL GRIEVANCE PORTAL & REST API"
echo "==============================================================================="

# Recompile Java classes
echo "[1/3] Compiling Java backend classes..."
mkdir -p backend/bin
javac -d backend/bin $(find backend/src -name "*.java")

# Run Automated Verification Suite
echo "[2/3] Running Automated System Verification Tests..."
java -cp backend/bin test.SystemTest

# Launch Live REST API & Web Application
PORT="${PORT:-8080}"
echo "[3/3] Starting Full-Stack Server on port ${PORT}..."
echo "Web Portal UI: http://localhost:${PORT}/"
echo "REST API:      http://localhost:${PORT}/api/"
exec java -cp backend/bin Main

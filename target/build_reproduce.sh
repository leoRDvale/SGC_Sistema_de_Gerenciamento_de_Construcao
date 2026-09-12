#!/usr/bin/env bash
# Script autogerado pelo SGC para reprodução exata da Baseline: v1.0.0-release
javac -d classes $(find src -name "*.java")
jar cfe app.jar com.exemplo.App -C classes .

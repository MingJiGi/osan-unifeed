@echo off
rem unifeed 서버 접속 — 더블클릭하면 접속, "connect-windows.cmd setup" 이면 ssh config 등록만
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0connect-windows.ps1" %*

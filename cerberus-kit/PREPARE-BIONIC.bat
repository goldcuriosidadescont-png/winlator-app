@echo off
setlocal
where py >nul 2>nul && (py -3 "%~dp0PREPARE-BIONIC.py" %* & exit /b %errorlevel%)
python "%~dp0PREPARE-BIONIC.py" %*
exit /b %errorlevel%

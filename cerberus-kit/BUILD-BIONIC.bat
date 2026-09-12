@echo off
setlocal
where py >nul 2>nul && (py -3 "%~dp0BUILD-BIONIC.py" %* & exit /b %errorlevel%)
python "%~dp0BUILD-BIONIC.py" %*
exit /b %errorlevel%

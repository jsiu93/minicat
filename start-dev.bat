@echo off
chcp 65001 >nul
echo 🚀 Starting Minicat Development Environment...

REM Create data directory
if not exist "data" mkdir data
if not exist "logs" mkdir logs

REM Start backend
echo.
echo 📦 Starting Backend (Spring Boot)...
start "Minicat Backend" cmd /k "cd backend && mvn spring-boot:run"

REM Wait for backend to start
timeout /t 5 /nobreak >nul

REM Start frontend
echo.
echo 🎨 Starting Frontend (Vue + Vite)...
start "Minicat Frontend" cmd /k "cd frontend && npm run dev"

echo.
echo ✅ Development environment started!
echo.
echo 📝 Access points:
echo    Frontend: http://localhost:5173
echo    Backend:  http://localhost:8080/api
echo    Swagger:  http://localhost:8080/api/swagger-ui.html
echo.
echo 🛑 Close the backend and frontend windows to stop services
echo.
pause

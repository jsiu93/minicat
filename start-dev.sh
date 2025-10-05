#!/bin/bash

# Minicat Development Startup Script

echo "🚀 Starting Minicat Development Environment..."

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Create data directory if not exists
mkdir -p data

# Function to check if port is in use
check_port() {
    if lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null ; then
        echo -e "${YELLOW}Warning: Port $1 is already in use${NC}"
        return 1
    fi
    return 0
}

# Check ports
echo "Checking ports..."
check_port 8080 || echo "  Backend port 8080 occupied"
check_port 5173 || echo "  Frontend port 5173 occupied"

# Start backend
echo -e "\n${BLUE}📦 Starting Backend (Spring Boot)...${NC}"
cd backend
mvn spring-boot:run > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID"
cd ..

# Wait a moment for backend to start
sleep 3

# Start frontend
echo -e "\n${GREEN}🎨 Starting Frontend (Vue + Vite)...${NC}"
cd frontend
npm run dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "Frontend PID: $FRONTEND_PID"
cd ..

echo -e "\n${GREEN}✅ Development environment started!${NC}"
echo -e "\n📝 Access points:"
echo -e "   Frontend: ${BLUE}http://localhost:5173${NC}"
echo -e "   Backend:  ${BLUE}http://localhost:8080/api${NC}"
echo -e "   Swagger:  ${BLUE}http://localhost:8080/api/swagger-ui.html${NC}"
echo -e "\n📋 Process IDs:"
echo -e "   Backend:  $BACKEND_PID"
echo -e "   Frontend: $FRONTEND_PID"
echo -e "\n🛑 To stop services:"
echo -e "   kill $BACKEND_PID $FRONTEND_PID"
echo -e "\n📄 Logs location:"
echo -e "   Backend:  logs/backend.log"
echo -e "   Frontend: logs/frontend.log"

# Save PIDs to file for easy stopping
echo "$BACKEND_PID" > .backend.pid
echo "$FRONTEND_PID" > .frontend.pid

echo -e "\n${YELLOW}Press Ctrl+C to view logs or run './stop-dev.sh' to stop services${NC}"

# Wait for user interrupt
wait

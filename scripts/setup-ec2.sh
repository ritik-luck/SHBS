#!/bin/bash
# deploy.sh
# Zero-Cost Deployment Script for AWS EC2 (Amazon Linux 2 / 2023)
# WARNING: This script installs dependencies and runs the docker-compose stack.

set -e

echo "=========================================="
echo " Starting Zero-Cost AWS Deployment Script"
echo "=========================================="

# 1. Update OS and Install Git
echo "=> Updating OS and installing Git..."
sudo dnf update -y
sudo dnf install git -y

# 2. Install Docker
echo "=> Installing Docker..."
sudo dnf install docker -y
sudo systemctl start docker
sudo systemctl enable docker

# Allow current user to run docker commands (may require logout/login, so we use sudo for now)
sudo usermod -aG docker $USER

# 3. Install Docker Compose plugin
echo "=> Installing Docker Compose..."
DOCKER_CONFIG=${DOCKER_CONFIG:-/usr/local/lib/docker}
sudo mkdir -p $DOCKER_CONFIG/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/download/v2.24.5/docker-compose-linux-x86_64 -o $DOCKER_CONFIG/cli-plugins/docker-compose
sudo chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose

echo "=> Environment Setup Complete!"
echo "Docker version: $(docker --version)"
echo "Docker Compose version: $(docker compose version)"

echo ""
echo "=========================================="
echo " Deployment Instructions:"
echo "=========================================="
echo "1. Clone your repository:"
echo "   git clone <your-repo-url> shbs"
echo "   cd shbs"
echo ""
echo "2. Edit backend/frontend/.env.production to match your EC2 public IP or domain:"
echo "   REACT_APP_API_URL=http://<YOUR_EC2_IP>:8080"
echo "   REACT_APP_WS_URL=ws://<YOUR_EC2_IP>:8080/ws"
echo ""
echo "3. Run the application:"
echo "   sudo docker compose up -d --build"
echo ""
echo "4. Access the frontend at: http://<YOUR_EC2_IP>:3000"
echo "=========================================="

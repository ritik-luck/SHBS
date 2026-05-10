# ☁️ ResilX — AWS Deployment Guide

> Deploy your Self-Healing Backend System to AWS after the 1-month build phase.
>
> **Timeline**: Week 5 (Apr 25 – May 1) — 1 extra week after project completion.

---

## Architecture on AWS

```
                         ┌──────────────┐
                         │  Route 53    │
                         │  (DNS)       │
                         └──────┬───────┘
                                │
                         ┌──────▼───────┐
                         │  CloudFront  │
                         │  (CDN)       │
                         └──────┬───────┘
                                │
                 ┌──────────────┼──────────────┐
                 │              │              │
          ┌──────▼──────┐      │       ┌──────▼──────┐
          │   S3 Bucket │      │       │   ALB       │
          │  (React     │      │       │ (Load       │
          │   Frontend) │      │       │  Balancer)  │
          └─────────────┘      │       └──────┬──────┘
                               │              │
                               │       ┌──────▼──────┐
                               │       │   EC2 / ECS │
                               │       │  (Spring    │
                               │       │   Boot App) │
                               │       └──────┬──────┘
                               │              │
                               │       ┌──────▼──────┐
                               │       │   RDS       │
                               │       │  (MySQL)    │
                               │       └─────────────┘
```

---

## AWS Services You'll Use

| Service | Purpose | Free Tier? |
|---|---|---|
| **EC2** (or ECS Fargate) | Run Spring Boot backend | ✅ 750 hrs/month (t2.micro) |
| **RDS MySQL** | Managed database | ✅ 750 hrs/month (db.t3.micro) |
| **S3** | Host React frontend (static) | ✅ 5GB storage |
| **CloudFront** | CDN for frontend | ✅ 1TB/month transfer |
| **ALB** | Load balancer for backend | ❌ (~$16/month) |
| **Route 53** | Custom domain (optional) | ❌ (~$0.50/month) |
| **ACM** | Free SSL certificate | ✅ Free with CloudFront/ALB |

> **💰 Estimated cost**: $0–20/month using Free Tier. Stay within t2.micro + db.t3.micro.

---

## Day-by-Day Deployment Plan

### Day 1 (Apr 25): Dockerize the Application

**What to learn**: Docker basics (images, containers, Dockerfile, docker-compose)

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY target/backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# docker-compose.yml (for local testing)
services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/resilx
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=password
    depends_on:
      - db
  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: resilx
    ports:
      - "3306:3306"
```

**Tasks**:
- [ ] Create `Dockerfile` for Spring Boot app
- [ ] Create `docker-compose.yml` for local testing
- [ ] Build JAR: `./mvnw clean package -DskipTests`
- [ ] Test locally: `docker-compose up`
- [ ] Verify all endpoints work in Docker

---

### Day 2 (Apr 26): Set Up AWS Account + RDS

**What to learn**: AWS Console, VPC basics, Security Groups

**Tasks**:
- [ ] Create AWS account (if not already)
- [ ] Set up IAM user with limited permissions (never use root)
- [ ] Create RDS MySQL instance:
  - Engine: MySQL 8.0
  - Instance: db.t3.micro (Free Tier)
  - Storage: 20GB
  - Public access: Yes (for initial setup, disable later)
  - Create database: `resilx`
- [ ] Note the RDS endpoint: `resilx-db.xxxxx.us-east-1.rds.amazonaws.com`
- [ ] Update `application.properties` to use environment variables:

```properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASS}
```

---

### Day 3 (Apr 27): Deploy Backend to EC2

**What to learn**: EC2 instances, SSH, Security Groups, user-data scripts

**Option A: Simple EC2 (Recommended for first deploy)**

**Tasks**:
- [ ] Launch EC2 instance:
  - AMI: Amazon Linux 2023
  - Type: t2.micro (Free Tier)
  - Key pair: Create new, download `.pem`
  - Security Group: Allow ports 22 (SSH), 8080 (app), 443 (HTTPS)
- [ ] SSH into instance:
  ```bash
  ssh -i your-key.pem ec2-user@<public-ip>
  ```
- [ ] Install Java + Docker:
  ```bash
  sudo yum update -y
  sudo yum install -y java-17-amazon-corretto docker
  sudo systemctl start docker
  sudo usermod -aG docker ec2-user
  ```
- [ ] Copy JAR to EC2 and run:
  ```bash
  scp -i your-key.pem backend/target/backend-0.0.1-SNAPSHOT.jar ec2-user@<ip>:~/
  
  # On EC2:
  export DATABASE_URL=jdbc:mysql://<rds-endpoint>:3306/resilx
  export DATABASE_USER=admin
  export DATABASE_PASS=yourpassword
  nohup java -jar backend-0.0.1-SNAPSHOT.jar &
  ```
- [ ] Verify: `curl http://<ec2-public-ip>:8080/system/health`

**Option B: ECS Fargate (More production-grade)**
- Push Docker image to ECR (Elastic Container Registry)
- Create ECS Fargate service with task definition
- More complex but shows container orchestration knowledge

---

### Day 4 (Apr 28): Deploy Frontend to S3 + CloudFront

**What to learn**: S3 static hosting, CloudFront CDN, CORS

**Tasks**:
- [ ] Update `api.js` → change `localhost:8080` to your EC2 public IP or domain
- [ ] Build React app:
  ```bash
  cd backend/frontend
  npm run build
  ```
- [ ] Create S3 bucket:
  - Name: `resilx-frontend` (must be globally unique)
  - Region: same as EC2
  - Uncheck "Block all public access"
  - Enable static website hosting (index.html)
- [ ] Upload `build/` folder contents to S3
- [ ] Set bucket policy for public read:
  ```json
  {
    "Version": "2012-10-17",
    "Statement": [{
      "Sid": "PublicRead",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::resilx-frontend/*"
    }]
  }
  ```
- [ ] Create CloudFront distribution pointing to S3
- [ ] Update backend CORS to allow CloudFront domain

---

### Day 5 (Apr 29): HTTPS + Domain + WebSocket Fix

**What to learn**: SSL/TLS, ACM, reverse proxy with Nginx

**Tasks**:
- [ ] Request free SSL certificate from ACM (AWS Certificate Manager)
- [ ] Install Nginx on EC2 as reverse proxy:
  ```nginx
  server {
      listen 80;
      server_name api.resilx.com;  # or your domain
      
      location / {
          proxy_pass http://localhost:8080;
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
      }
      
      # WebSocket support
      location /ws {
          proxy_pass http://localhost:8080/ws;
          proxy_http_version 1.1;
          proxy_set_header Upgrade $http_upgrade;
          proxy_set_header Connection "upgrade";
      }
  }
  ```
- [ ] (Optional) Register domain on Route 53 or use free subdomain
- [ ] Update frontend WebSocket URL to use `wss://` (secure WebSocket)

---

### Day 6 (Apr 30): Production Hardening

**What to learn**: Environment profiles, systemd, logging in production

**Tasks**:
- [ ] Create `application-prod.properties`:
  ```properties
  spring.jpa.hibernate.ddl-auto=validate  # never use update/create in prod
  logging.level.root=WARN
  logging.level.com.selfhealing=INFO
  server.error.include-stacktrace=never
  ```
- [ ] Create systemd service for auto-restart:
  ```ini
  # /etc/systemd/system/resilx.service
  [Unit]
  Description=ResilX Backend
  After=network.target

  [Service]
  User=ec2-user
  ExecStart=/usr/bin/java -jar /home/ec2-user/backend.jar --spring.profiles.active=prod
  Restart=always
  RestartSec=10

  [Install]
  WantedBy=multi-user.target
  ```
- [ ] Set up CloudWatch for log monitoring (or simple log rotation)
- [ ] Test: reboot EC2 → verify app auto-starts

---

### Day 7 (May 1): Final Testing + Documentation

**Tasks**:
- [ ] End-to-end test on AWS:
  - Open dashboard → simulate failures → watch circuit breaker → observe recovery
  - Test WebSocket real-time updates work
  - Test from mobile browser (responsive check)
- [ ] Update README with:
  - Live demo URL
  - AWS architecture diagram
  - Deployment instructions
- [ ] Take screenshots of live dashboard for documentation
- [ ] (Bonus) Record a 2-minute demo video

---

## 📚 What to Learn for Deployment

| Topic | Resource | Time |
|---|---|---|
| Docker basics | Docker official "Get Started" guide | 2 hours |
| AWS Free Tier setup | AWS docs "Getting Started" | 1 hour |
| EC2 + SSH | AWS EC2 user guide | 1 hour |
| RDS MySQL setup | AWS RDS getting started guide | 1 hour |
| S3 static hosting | AWS S3 static website docs | 30 min |
| Nginx reverse proxy | DigitalOcean's Nginx guide | 1 hour |
| Spring Boot profiles | Baeldung → Spring Profiles | 30 min |

---

## 🏅 Why AWS Deployment Makes Your Project Elite

| What You Can Say | Why It Matters |
|---|---|
| "Dockerized Spring Boot app deployed to EC2" | Shows containerization knowledge |
| "MySQL on RDS with security groups" | Shows managed database + networking |
| "React frontend on S3 + CloudFront CDN" | Shows static hosting + CDN concepts |
| "Nginx reverse proxy with WebSocket support" | Shows production architecture |
| "HTTPS with ACM certificate" | Shows security awareness |
| "Systemd auto-restart on crash" | Shows ops/SRE mindset — system heals even itself! |

> **The irony is beautiful**: Your self-healing app is deployed with self-healing infrastructure
> (auto-restart on crash). The app heals its services, and the infra heals the app. 🔥

---

## Updated Full Timeline

```
 MONTH 1 (Build)                              WEEK 5 (Deploy)
 ┌────────┬────────┬────────┬────────┐       ┌────────────────┐
 │ Week 1 │ Week 2 │ Week 3 │ Week 4 │       │    AWS         │
 │Backend │Healing │Frontend│Testing │──────▶│  Deployment    │
 │ Core   │Engine  │Dashboard│& Docs │       │  + Go Live!    │
 └────────┴────────┴────────┴────────┘       └────────────────┘
  Mar 25          →           Apr 24           Apr 25 → May 1

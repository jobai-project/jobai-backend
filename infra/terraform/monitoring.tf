# Prometheus + Grafana 관측 스택 전용 인스턴스.
# 백엔드 EC2(t3.micro, 1GB)는 이미 스왑을 쓸 만큼 여유가 없어 같은 호스트에 올리지 않는다.

resource "aws_security_group" "monitoring" {
  name        = "jobai-monitoring-sg"
  description = "Prometheus and Grafana for JobAI observability"
  vpc_id      = aws_vpc.jobai.id

  ingress {
    description = "Grafana UI from admin IP"
    from_port   = 3000
    to_port     = 3000
    protocol    = "tcp"
    cidr_blocks = ["${var.my_ip}/32"]
  }

  ingress {
    description = "Prometheus UI from admin IP"
    from_port   = 9090
    to_port     = 9090
    protocol    = "tcp"
    cidr_blocks = ["${var.my_ip}/32"]
  }

  ingress {
    description = "SSH from admin IP"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["${var.my_ip}/32"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "jobai-monitoring-sg"
  }
}

resource "aws_iam_role" "monitoring_ec2" {
  name = "jobai-monitoring-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = {
    Name = "jobai-monitoring-ec2-role"
  }
}

resource "aws_iam_role_policy_attachment" "monitoring_ssm_managed_instance" {
  role       = aws_iam_role.monitoring_ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "monitoring_ec2" {
  name = "jobai-monitoring-ec2-instance-profile"
  role = aws_iam_role.monitoring_ec2.name
}

resource "aws_instance" "monitoring" {
  ami                    = var.ami_id
  instance_type          = "t3.micro"
  subnet_id              = aws_subnet.jobai_public.id
  vpc_security_group_ids = [aws_security_group.monitoring.id]
  key_name               = var.key_name
  iam_instance_profile   = aws_iam_instance_profile.monitoring_ec2.name

  user_data = <<-EOF
    #!/bin/bash
    apt-get update -y
    apt-get install -y docker.io awscli snapd
    if [ ! -f /swapfile ]; then
      fallocate -l 2G /swapfile
      chmod 600 /swapfile
      mkswap /swapfile
      swapon /swapfile
      echo '/swapfile none swap sw 0 0' >> /etc/fstab
    fi
    systemctl start docker
    systemctl enable docker
    usermod -aG docker ubuntu
    snap install amazon-ssm-agent --classic || true
    systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent.service || true
    systemctl start snap.amazon-ssm-agent.amazon-ssm-agent.service || true
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose

    mkdir -p /opt/monitoring/prometheus
    mkdir -p /opt/monitoring/grafana/provisioning/datasources
    mkdir -p /opt/monitoring/grafana/provisioning/dashboards
    mkdir -p /opt/monitoring/grafana/dashboards

    cat > /opt/monitoring/prometheus/prometheus.yml <<'PROMCFG'
    global:
      scrape_interval: 15s

    scrape_configs:
      - job_name: jobai-backend
        metrics_path: /actuator/prometheus
        static_configs:
          - targets:
              - ${aws_instance.jobai.private_ip}:9090
    PROMCFG

    cat > /opt/monitoring/grafana/provisioning/datasources/datasource.yml <<'DSCFG'
    apiVersion: 1

    datasources:
      - name: Prometheus
        uid: prometheus
        type: prometheus
        access: proxy
        url: http://prometheus:9090
        isDefault: true
        editable: true
    DSCFG

    cat > /opt/monitoring/grafana/provisioning/dashboards/dashboard.yml <<'DBCFG'
    apiVersion: 1

    providers:
      - name: jobai
        orgId: 1
        folder: ''
        type: file
        disableDeletion: false
        updateIntervalSeconds: 30
        allowUiUpdates: true
        options:
          path: /var/lib/grafana/dashboards
    DBCFG

    cat > /opt/monitoring/grafana/dashboards/jobai-backend-overview.json <<'DASHJSON'
    ${replace(file("${path.module}/../grafana/dashboards/jobai-backend-overview.json"), "jobai-backend-local", "jobai-backend")}
    DASHJSON

    cat > /opt/monitoring/docker-compose.yml <<'COMPOSE'
    services:
      prometheus:
        image: prom/prometheus:v2.55.1
        container_name: jobai-prometheus
        restart: unless-stopped
        ports:
          - "9090:9090"
        volumes:
          - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
          - prometheus_data:/prometheus

      grafana:
        image: grafana/grafana:11.3.0
        container_name: jobai-grafana
        restart: unless-stopped
        ports:
          - "3000:3000"
        environment:
          GF_SECURITY_ADMIN_USER: admin
          GF_SECURITY_ADMIN_PASSWORD: admin
        volumes:
          - grafana_data:/var/lib/grafana
          - ./grafana/provisioning:/etc/grafana/provisioning:ro
          - ./grafana/dashboards:/var/lib/grafana/dashboards:ro
        depends_on:
          - prometheus

    volumes:
      prometheus_data:
      grafana_data:
    COMPOSE

    cd /opt/monitoring
    /usr/local/bin/docker-compose up -d
  EOF

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    encrypted   = true
    volume_type = "gp3"
    volume_size = 20
  }

  tags = {
    Name = "jobai-monitoring-ec2"
  }
}

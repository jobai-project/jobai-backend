terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# VPC
resource "aws_vpc" "jobai" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "jobai-vpc"
  }
}

# 서브넷
resource "aws_subnet" "jobai_public" {
  vpc_id                  = aws_vpc.jobai.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "ap-northeast-2a"
  map_public_ip_on_launch = true

  tags = {
    Name = "jobai-public-subnet"
  }
}

# 인터넷 게이트웨이
resource "aws_internet_gateway" "jobai" {
  vpc_id = aws_vpc.jobai.id

  tags = {
    Name = "jobai-igw"
  }
}

# 라우팅 테이블
resource "aws_route_table" "jobai_public" {
  vpc_id = aws_vpc.jobai.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.jobai.id
  }

  tags = {
    Name = "jobai-public-rt"
  }
}

resource "aws_route_table_association" "jobai_public" {
  subnet_id      = aws_subnet.jobai_public.id
  route_table_id = aws_route_table.jobai_public.id
}

# 보안그룹
resource "aws_security_group" "jobai" {
  name        = "jobai-sg"
  description = "jobai security group"
  vpc_id      = aws_vpc.jobai.id
ingress {
  from_port   = 22
  to_port     = 22
  protocol    = "tcp"
  cidr_blocks = ["${var.my_ip}/32"]  # SSH는 내 IP만
}

ingress {
  from_port   = 8080
  to_port     = 8080
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]  # Spring Boot 전체 오픈
}

ingress {
  from_port   = 8001
  to_port     = 8001
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]  # FastAPI 전체 오픈
}

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "jobai-sg"
  }
}

# EC2
resource "aws_instance" "jobai" {
  ami                    = var.ami_id
  instance_type          = "t2.micro"
  subnet_id              = aws_subnet.jobai_public.id
  vpc_security_group_ids = [aws_security_group.jobai.id]
  key_name               = var.key_name

  user_data = <<-EOF
    #!/bin/bash
    apt-get update -y
    apt-get install -y docker.io
    systemctl start docker
    systemctl enable docker
    usermod -aG docker ubuntu
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
  EOF

  tags = {
    Name = "jobai-ec2"
  }
}
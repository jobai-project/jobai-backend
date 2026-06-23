variable "aws_region" {
  default = "ap-northeast-2"
}

variable "ami_id" {
  default = "ami-042e76978adeb8c48" # Ubuntu 22.04 서울 리전
}

variable "key_name" {
  description = "EC2 SSH 키페어 이름"
}

variable "my_ip" {
  description = "내 로컬 IP"
}

variable "db_allocated_storage" {
  description = "RDS 스토리지 크기 (GB)"
  default     = 20
}

variable "db_instance_class" {
  description = "RDS 인스턴스 클래스"
  default     = "db.t3.micro"
}

variable "db_engine" {
  description = "RDS 엔진"
  default     = "postgres"
}

variable "db_engine_version" {
  description = "RDS 엔진 버전"
  default     = "16.14"
}

variable "db_name" {
  description = "RDS 데이터베이스 이름"
  default     = "jobai"
}

variable "db_username" {
  description = "RDS 마스터 사용자 이름"
  default     = "jobai"
}

variable "db_password" {
  description = "RDS 마스터 비밀번호"
  type        = string
  sensitive   = true
}

variable "db_port" {
  description = "RDS 포트"
  default     = 5432
}
variable "db_backup_retention_period" {
  description = "RDS backup retention period in days. Use 0 for free-tier-limited initial deployment, 7 for production."
  type        = number
  default     = 0
}
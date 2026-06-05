variable "aws_region" {
  default = "ap-northeast-2"
}

variable "ami_id" {
  default = "ami-042e76978adeb8c48"  # Ubuntu 22.04 서울 리전
}

variable "key_name" {
  description = "EC2 SSH 키페어 이름"
}
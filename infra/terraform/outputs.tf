output "ec2_public_ip" {
  value = aws_instance.jobai.public_ip
}

output "ec2_public_dns" {
  value = aws_instance.jobai.public_dns
}

output "rds_endpoint" {
  value = aws_db_instance.jobai.endpoint
}

output "rds_address" {
  value = aws_db_instance.jobai.address
}
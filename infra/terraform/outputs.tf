output "ec2_public_ip" {
  value = aws_instance.jobai.public_ip
}

output "ec2_public_dns" {
  value = aws_instance.jobai.public_dns
}
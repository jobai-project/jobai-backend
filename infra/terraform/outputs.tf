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

output "backend_ecr_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "ai_server_ecr_repository_url" {
  value = aws_ecr_repository.ai_server.repository_url
}

output "ecr_registry_url" {
  value = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}

output "github_actions_deploy_role_arn" {
  value = aws_iam_role.github_actions_deploy.arn
}

output "resume_s3_bucket_name" {
  value = aws_s3_bucket.resume_files.bucket
}

output "ai_model_s3_bucket_name" {
  value = aws_s3_bucket.ai_models.bucket
}

output "ai_model_jd_s3_prefix" {
  value = "models/jd/"
}

output "ai_model_ncs_s3_prefix" {
  value = "models/ncs/"
}

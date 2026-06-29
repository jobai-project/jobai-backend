resource "aws_s3_bucket" "resume_files" {
  bucket = "jobai-resumes-077660206269-ap-northeast-2"
}

resource "aws_s3_bucket_public_access_block" "resume_files" {
  bucket = aws_s3_bucket.resume_files.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "resume_files" {
  bucket = aws_s3_bucket.resume_files.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
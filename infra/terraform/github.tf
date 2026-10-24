# GitHub Actions уваходзіць у AWS кароткімі токенамі GitHub, без ключоў AWS у GitHub.
# Зборка (галіна main) можа толькі пакласьці вобраз у ECR; выкладка (асяродзьдзе production,
# пасьля ўхвалы) — толькі даслаць каманду на сэрвэр bmuz.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_policy_document" "github_trust" {
  for_each = {
    build  = "repo:${local.repo}:ref:refs/heads/main"
    deploy = "repo:${local.repo}:environment:production"
  }

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [each.value]
    }
  }
}

resource "aws_iam_role" "github" {
  for_each = data.aws_iam_policy_document.github_trust

  name               = "bmuz-github-${each.key}"
  assume_role_policy = each.value.json
}

data "aws_iam_policy_document" "github_build" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = ["arn:aws:ecr:${local.region}:${local.account}:repository/bmuz"]
  }
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:${local.region}::document/AWS-RunShellScript"]
  }

  # Толькі сэрвэр з тэгам Name=bmuz.
  statement {
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:${local.region}:${local.account}:instance/*"]

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Name"
      values   = ["bmuz"]
    }
  }

  statement {
    actions   = ["ssm:GetCommandInvocation", "ec2:DescribeInstances"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_build" {
  name   = "bmuz-github-build"
  role   = aws_iam_role.github["build"].id
  policy = data.aws_iam_policy_document.github_build.json
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "bmuz-github-deploy"
  role   = aws_iam_role.github["deploy"].id
  policy = data.aws_iam_policy_document.github_deploy.json
}

output "github_roles" {
  value = { for k, r in aws_iam_role.github : k => r.arn }
}

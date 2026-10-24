# Інфраструктура AWS

Як BMUZ працуе на AWS і якімі камандамі гэта створана. Рэгіён — `eu-north-1`
(Стакгольм), профіль AWS CLI — `bmuz`. Усе рэсурсы маюць тэг `project=bmuz`.

Гэта ручная ўстаноўка; пазьней яна стане кодам AWS CDK у гэтай тэчцы.

## Што працуе

| Рэсурс                              | Імя або ID                            |
| ----------------------------------- | ------------------------------------- |
| Дамэн і яго DNS (Route 53)          | `bmuz.eu`, `Z01058652SZMDHTGQ7WL5`    |
| Сэртыфікат HTTPS (ACM, `us-east-1`) | `bmuz.eu`, `www.bmuz.eu`              |
| CloudFront                          | `E2MKAS13NWHFNZ`                      |
| Функцыя CloudFront                  | `bmuz-www-redirect`                   |
| Сховішча вобразаў (ECR)             | `bmuz`                                |
| База PostgreSQL 18.6 (RDS)          | `bmuz`, `db.t4g.micro`, 20 GB         |
| Пароль базы (Secrets Manager)       | `rds!db-66425024-…`                   |
| Сэрвэр (EC2)                        | тэг `Name=bmuz`, `t3.micro`           |
| Роля сэрвэра (IAM)                  | `bmuz-server`                         |
| Вокладкі (S3)                       | `bmuz-covers-899012776854`            |
| Доступ CloudFront да вокладак (OAC) | `bmuz-covers`, `E15OL0EQ71QGKQ`       |
| Адпраўка лістоў (SES)               | `bmuz.eu`, адпраўнік `no-reply@`      |
| Правілы доступу (security groups)   | `bmuz-app`, `bmuz-db`                 |

Адрас сайта: https://bmuz.eu — `www.bmuz.eu` перакідвае туды, а
`d33pljzy4x3xbb.cloudfront.net` працуе як раней.

- Сэрвэр прымае HTTP толькі ад CloudFront (сьпіс адрасоў
  `com.amazonaws.global.cloudfront.origin-facing`), база — толькі ад сэрвэра.
  Порт SSH закрыты; на сэрвэр заходзяць праз Systems Manager.
- Пароль базы стварыў і захоўвае RDS. Сэрвэр чытае яго пры кожным запуску
  сэрвіса і на дыск не піша.
- Усё, акрамя `/assets/*` і `/covers/*`, CloudFront перадае сэрвэру з усімі
  мэтадамі (`POST`, `PUT`, `DELETE` — для ўваходу і зьменаў), з кукі і
  загалоўкамі, і нічога з гэтага не кэшуе.
- Вокладкі ляжаць у прыватным S3: чытае іх толькі CloudFront па `/covers/*`,
  а сэрвэр можа толькі пакласьці або выдаліць файл у `covers/`. Ранейшыя
  вэрсіі файлаў S3 захоўвае 30 дзён, потым выдаляе.
- Лісты сайт адпраўляе праз SES як `no-reply@bmuz.eu`, падпісаныя DKIM; зваротны
  адрас для адмоваў — `mail.bmuz.eu`, правіла DMARC — `p=quarantine`. Роля
  сэрвэра можа адпраўляць толькі з гэтага адраса. SES дастаўляе на любы
  адрас: AWS зьняў тэставы рэжым 2026-10-24.
- Сэртыфікат AWS падаўжае сам, пакуль у DNS ёсьць запісы праверкі
  (`_…bmuz.eu`, `_…www.bmuz.eu`).

## Файлы

| Файл                     | Што                                                  |
| ------------------------ | ---------------------------------------------------- |
| `user-data.sh`           | першы запуск сэрвэра: Docker, swap, сэрвіс `bmuz`    |
| `cloudfront.json`        | канфігурацыя CloudFront, як яна ёсьць                |
| `www-redirect.js`        | функцыя CloudFront: `www.bmuz.eu` → `bmuz.eu`        |
| `iam-server-trust.json`  | хто можа ўзяць ролю `bmuz-server` (толькі EC2)       |
| `iam-server-access.json` | што можа роля: адзін вобраз, адзін пароль, вокладкі  |
| `s3-covers-policy.json`  | хто можа чытаць вокладкі (толькі CloudFront)         |
| `dns-ses.json`           | запісы DNS для SES: DKIM, `mail.bmuz.eu`, DMARC      |
| `dns-mail.json`          | `h.puhachou@bmuz.eu` → Gmail праз Forward Email      |
| `run_on_server.py`       | выканаць каманды на сэрвэры праз Systems Manager     |

## Як створана

```bash
export AWS_PROFILE=bmuz

# Сховішча вобразаў, захоўвае 5 апошніх
aws ecr create-repository --repository-name bmuz --image-scanning-configuration scanOnPush=true

# Вобраз: тэг — кароткі хэш коміту
aws ecr get-login-password | docker login --username AWS --password-stdin 899012776854.dkr.ecr.eu-north-1.amazonaws.com
docker build -t 899012776854.dkr.ecr.eu-north-1.amazonaws.com/bmuz:$(git rev-parse --short HEAD) .
docker push 899012776854.dkr.ecr.eu-north-1.amazonaws.com/bmuz:$(git rev-parse --short HEAD)

# База: прыватная, зашыфраваная, пароль у Secrets Manager
aws rds create-db-instance --db-instance-identifier bmuz --engine postgres --engine-version 18.6 \
  --db-instance-class db.t4g.micro --allocated-storage 20 --storage-type gp3 --storage-encrypted \
  --db-name bmuz --master-username bmuz --manage-master-user-password \
  --vpc-security-group-ids <bmuz-db> --no-publicly-accessible --no-multi-az \
  --backup-retention-period 7 --deletion-protection

# Пароль мяняецца раз на год, а не раз на тыдзень (гл. «Заўвагі»)
aws secretsmanager rotate-secret --secret-id <пароль базы> \
  --rotation-rules AutomaticallyAfterDays=365 --no-rotate-immediately

# Сэрвэр
aws ec2 run-instances --image-id <Amazon Linux 2023> --instance-type t3.micro \
  --security-group-ids <bmuz-app> --iam-instance-profile Name=bmuz-server \
  --associate-public-ip-address --credit-specification CpuCredits=standard \
  --metadata-options HttpTokens=required,HttpEndpoint=enabled \
  --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=8,VolumeType=gp3,Encrypted=true}' \
  --user-data file://infra/user-data.sh \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=bmuz},{Key=project,Value=bmuz}]'

# Сэртыфікат для дамэна: CloudFront бярэ сэртыфікаты толькі з us-east-1
aws acm request-certificate --region us-east-1 --domain-name bmuz.eu \
  --subject-alternative-names www.bmuz.eu --validation-method DNS
# два запісы CNAME з выніку — у зону bmuz.eu, потым:
aws acm wait certificate-validated --region us-east-1 --certificate-arn <сэртыфікат>

# Функцыя перакіду www
aws cloudfront create-function --name bmuz-www-redirect \
  --function-config Comment="www.bmuz.eu to bmuz.eu",Runtime=cloudfront-js-2.0 \
  --function-code fileb://infra/www-redirect.js
aws cloudfront publish-function --name bmuz-www-redirect --if-match <ETag>

# CloudFront з дамэнам, сэртыфікатам і функцыяй
aws cloudfront create-distribution --distribution-config file://infra/cloudfront.json

# Вокладкі: прыватнае сховішча, ранейшыя вэрсіі — 30 дзён
aws s3api create-bucket --bucket bmuz-covers-899012776854 \
  --create-bucket-configuration LocationConstraint=eu-north-1
aws s3api put-public-access-block --bucket bmuz-covers-899012776854 --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-versioning --bucket bmuz-covers-899012776854 --versioning-configuration Status=Enabled
aws s3api put-bucket-tagging --bucket bmuz-covers-899012776854 --tagging 'TagSet=[{Key=project,Value=bmuz}]'
aws s3api put-bucket-lifecycle-configuration --bucket bmuz-covers-899012776854 --lifecycle-configuration \
  '{"Rules":[{"ID":"old-versions-30-days","Status":"Enabled","Filter":{},
    "NoncurrentVersionExpiration":{"NoncurrentDays":30},"Expiration":{"ExpiredObjectDeleteMarker":true},
    "AbortIncompleteMultipartUpload":{"DaysAfterInitiation":7}}]}'
aws cloudfront create-origin-access-control --origin-access-control-config \
  '{"Name":"bmuz-covers","SigningProtocol":"sigv4","SigningBehavior":"always","OriginAccessControlOriginType":"s3"}'
aws s3api put-bucket-policy --bucket bmuz-covers-899012776854 --policy file://infra/s3-covers-policy.json
aws iam put-role-policy --role-name bmuz-server --policy-name bmuz-server-access \
  --policy-document file://infra/iam-server-access.json
# CloudFront: паходжаньне bmuz-covers і /covers/* — у cloudfront.json
aws cloudfront update-distribution --id E2MKAS13NWHFNZ --if-match <ETag> \
  --distribution-config file://infra/cloudfront.json

# Лісты: дамэн у SES з DKIM і зваротным адрасам mail.bmuz.eu
aws sesv2 create-email-identity --email-identity bmuz.eu --tags Key=project,Value=bmuz
aws sesv2 put-email-identity-mail-from-attributes --email-identity bmuz.eu \
  --mail-from-domain mail.bmuz.eu --behavior-on-mx-failure USE_DEFAULT_VALUE
# тры токены DKIM з выніку першай каманды — у dns-ses.json, потым:
aws route53 change-resource-record-sets --hosted-zone-id Z01058652SZMDHTGQ7WL5 --change-batch file://infra/dns-ses.json

# Запісы A і AAAA для bmuz.eu і www.bmuz.eu — псэўданімы на CloudFront
# (HostedZoneId CloudFront — заўсёды Z2FDTNDATAQYW2)
aws route53 change-resource-record-sets --hosted-zone-id Z01058652SZMDHTGQ7WL5 --change-batch file://<запісы>
```

`CpuCredits=standard` — каб сэрвэр не плаціў за працу вышэй за базавую хуткасьць
працэсара; за гэта запуск сайта займае каля 2½ хвіліны.

Каталёг у пустую базу:

```bash
python3 infra/run_on_server.py 'systemctl stop bmuz' \
  '/opt/bmuz/start.sh --spring.main.web-application-type=none --bmuz.import.json=data/catalogue.json' \
  'systemctl start bmuz'
```

## Заўвагі

- **Некаторыя каманды AWS CLI не працуюць** у пакеце `awscli` 2.31 з Ubuntu
  26.04: Python 3.14 правярае тэксты даведкі больш строга, і знак `%` у
  адным з іх спыняе каманду (`badly formed help string`). Так падаюць
  `aws ssm send-command` і `aws rds modify-db-instance`; астатнія, ужытыя
  тут, працуюць. Той самы API выклікаецца праз бібліятэку `botocore` з гэтага
  пакета — так робіць `run_on_server.py`:

  ```bash
  python3 -I -c "from awscli.botocore import session
  rds = session.Session(profile='bmuz').create_client('rds', region_name='eu-north-1')
  rds.modify_db_instance(DBInstanceIdentifier='bmuz', DeletionProtection=False, ApplyImmediately=True)"
  ```
- **Сайт карыстаецца адміністратарам базы.** Ягоны пароль RDS мяняе сам, а сэрвіс
  чытае пароль толькі пры запуску, таму змена спыніла б сайт. Пакуль змена — раз
  на год; правільна — асобны карыстальнік базы для сайта з уласным паролем.

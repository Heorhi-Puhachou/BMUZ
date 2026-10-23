# Інфраструктура AWS

Як BMZ працуе на AWS і якімі камандамі гэта створана. Рэгіён — `eu-north-1`
(Стакгольм), профіль AWS CLI — `bmz`. Усе рэсурсы маюць тэг `project=bmz`.

Гэта ручная ўстаноўка; пазьней яна стане кодам AWS CDK у гэтай тэчцы.

## Што працуе

| Рэсурс                              | Імя або ID                           |
| ----------------------------------- | ------------------------------------ |
| Сховішча вобразаў (ECR)             | `bmz`                                |
| База PostgreSQL 18.6 (RDS)          | `bmz`, `db.t4g.micro`, 20 GB         |
| Пароль базы (Secrets Manager)       | `rds!db-05036461-…`                  |
| Сэрвэр (EC2)                        | `i-0b2b1af6c62f8f95c`, `t3.micro`    |
| Роля сэрвэра (IAM)                  | `bmz-server`                         |
| Правілы доступу (security groups)   | `bmz-app`, `bmz-db`                  |
| CloudFront                          | `E2MKAS13NWHFNZ`                     |

Адрас сайта: https://d33pljzy4x3xbb.cloudfront.net

- Сэрвэр прымае HTTP толькі ад CloudFront (сьпіс адрасоў
  `com.amazonaws.global.cloudfront.origin-facing`), база — толькі ад сэрвэра.
  Порт SSH закрыты; на сэрвэр заходзяць праз Systems Manager.
- Пароль базы стварыў і захоўвае RDS. Сэрвэр чытае яго пры кожным запуску
  сэрвіса і на дыск не піша.

## Файлы

| Файл                     | Што                                                  |
| ------------------------ | ---------------------------------------------------- |
| `user-data.sh`           | першы запуск сэрвэра: Docker, swap, сэрвіс `bmz`     |
| `cloudfront.json`        | канфігурацыя CloudFront, як яна ёсьць                |
| `iam-server-trust.json`  | хто можа ўзяць ролю `bmz-server` (толькі EC2)        |
| `iam-server-access.json` | што можа роля: адзін вобраз, адзін пароль            |
| `run_on_server.py`       | выканаць каманды на сэрвэры праз Systems Manager     |

## Як створана

```bash
export AWS_PROFILE=bmz

# Сховішча вобразаў, захоўвае 5 апошніх
aws ecr create-repository --repository-name bmz --image-scanning-configuration scanOnPush=true

# Вобраз: тэг — кароткі хэш коміту
aws ecr get-login-password | docker login --username AWS --password-stdin 899012776854.dkr.ecr.eu-north-1.amazonaws.com
docker build -t 899012776854.dkr.ecr.eu-north-1.amazonaws.com/bmz:$(git rev-parse --short HEAD) .
docker push 899012776854.dkr.ecr.eu-north-1.amazonaws.com/bmz:$(git rev-parse --short HEAD)

# База: прыватная, зашыфраваная, пароль у Secrets Manager
aws rds create-db-instance --db-instance-identifier bmz --engine postgres --engine-version 18.6 \
  --db-instance-class db.t4g.micro --allocated-storage 20 --storage-type gp3 --storage-encrypted \
  --db-name bmz --master-username bmz --manage-master-user-password \
  --vpc-security-group-ids <bmz-db> --no-publicly-accessible --no-multi-az \
  --backup-retention-period 7 --deletion-protection

# Пароль мяняецца раз на год, а не раз на тыдзень (гл. «Заўвагі»)
aws secretsmanager rotate-secret --secret-id <пароль базы> \
  --rotation-rules AutomaticallyAfterDays=365 --no-rotate-immediately

# Сэрвэр
aws ec2 run-instances --image-id <Amazon Linux 2023> --instance-type t3.micro \
  --security-group-ids <bmz-app> --iam-instance-profile Name=bmz-server \
  --associate-public-ip-address --credit-specification CpuCredits=standard \
  --metadata-options HttpTokens=required,HttpEndpoint=enabled \
  --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=8,VolumeType=gp3,Encrypted=true}' \
  --user-data file://infra/user-data.sh

# CloudFront
aws cloudfront create-distribution --distribution-config file://infra/cloudfront.json
```

`CpuCredits=standard` — каб сэрвэр не плаціў за працу вышэй за базавую хуткасьць
працэсара; за гэта запуск сайта займае каля 2½ хвіліны.

Каталёг у пустую базу:

```bash
python3 infra/run_on_server.py 'systemctl stop bmz' \
  '/opt/bmz/start.sh --spring.main.web-application-type=none --bmz.import.json=data/catalogue.json' \
  'systemctl start bmz'
```

## Заўвагі

- **`aws ssm send-command` не працуе** ў пакеце `awscli` 2.31 з Ubuntu 26.04:
  Python 3.14 правярае тэксты даведкі больш строга, і знак `%` у адным з іх
  спыняе каманду (`ValueError: unsupported format character`). Таму
  `run_on_server.py` выклікае той самы API праз бібліятэку `botocore` з гэтага
  пакета.
- **Сайт карыстаецца адміністратарам базы.** Ягоны пароль RDS мяняе сам, а сэрвіс
  чытае пароль толькі пры запуску, таму змена спыніла б сайт. Пакуль змена — раз
  на год; правільна — асобны карыстальнік базы для сайта з уласным паролем.

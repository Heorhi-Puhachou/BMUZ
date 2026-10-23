"""Выконвае каманды на сэрвэры BMUZ праз Systems Manager і друкуе вынік.
Сэрвэр шукаецца па тэгу Name=bmuz сярод запушчаных.

Абыход: `aws ssm send-command` падае ў awscli 2.31 з Ubuntu пад Python 3.14 (argparse
не прымае `%` у адным з тэкстаў даведкі), таму той самы API выклікаецца праз botocore.
Ужываньне: python3 run_on_server.py 'каманда адна' 'каманда другая' ...
"""
import sys
import time

from awscli.botocore import session as botocore_session

session = botocore_session.Session(profile="bmuz")
ec2 = session.create_client("ec2", region_name="eu-north-1")
ssm = session.create_client("ssm", region_name="eu-north-1")

reservations = ec2.describe_instances(Filters=[
    {"Name": "tag:Name", "Values": ["bmuz"]},
    {"Name": "instance-state-name", "Values": ["running"]},
])["Reservations"]
instances = [i["InstanceId"] for r in reservations for i in r["Instances"]]
if len(instances) != 1:
    sys.exit(f"expected one running server named bmuz, found {instances}")
INSTANCE = instances[0]

command_id = ssm.send_command(
    InstanceIds=[INSTANCE],
    DocumentName="AWS-RunShellScript",
    Parameters={"commands": sys.argv[1:]},
)["Command"]["CommandId"]

while True:
    time.sleep(2)
    try:
        result = ssm.get_command_invocation(CommandId=command_id, InstanceId=INSTANCE)
    except ssm.exceptions.InvocationDoesNotExist:
        continue
    if result["Status"] not in ("Pending", "InProgress", "Delayed"):
        break

print(result["Status"])
print(result["StandardOutputContent"], end="")
if result["StandardErrorContent"]:
    print("--- stderr ---")
    print(result["StandardErrorContent"], end="")

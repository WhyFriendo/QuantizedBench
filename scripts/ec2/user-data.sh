#!/usr/bin/env bash
set -euo pipefail

dnf install -y git rsync tmux jq htop python3
systemctl enable --now docker
usermod -aG docker ec2-user

install -d -o ec2-user -g ec2-user /opt/quantizedbench
install -d -o ec2-user -g ec2-user /opt/quantizedbench/results
install -d -o ec2-user -g ec2-user /opt/quantizedbench/logs

nvidia-smi > /opt/quantizedbench/logs/nvidia-smi.txt 2>&1
docker version > /opt/quantizedbench/logs/docker-version.txt 2>&1
chown -R ec2-user:ec2-user /opt/quantizedbench
touch /opt/quantizedbench/READY

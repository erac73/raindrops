---
name: ssh-remote
description: Remote server management via SSH - connection, commands, file transfer, debugging
license: MIT
compatibility: opencode
metadata:
  target: raspberry-pi-5
  host: 100.109.105.19
---

## What I do

Guide SSH operations for remote Raspberry Pi management.

## Connection

```bash
# Basic SSH
ssh serpico@100.109.105.19

# With password
ssh serpico@100.109.105.19  # password: Nu6EuwrR%ij7

# Execute single command
ssh serpico@100.109.105.19 "command here"
```

## File Transfer (SCP)

```bash
# Upload file
scp localfile.txt serpico@100.109.105.19:/home/serpico/

# Upload directory
scp -r ./localdir serpico@100.109.105.19:/home/serpico/

# Download file
scp serpico@100.109.105.19:/home/serpico/file.txt ./

# Upload to temp
scp file.txt serpico@100.109.105.19:/tmp/
```

## Remote Execution Patterns

### Execute Python Script
```bash
# 1. Create local script
# 2. SCP to Pi
scp script.py serpico@100.109.105.19:/tmp/
# 3. Execute remotely
ssh serpico@100.109.105.19 "python3 /tmp/script.py"
```

### Execute Bash Script
```bash
# Create and execute in one step
ssh serpico@100.109.105.19 'bash -s' < local_script.sh
```

### Pipe Content to Remote File
```bash
cat localfile.txt | ssh serpico@100.109.105.19 "cat > /home/serpico/remotefile.txt"
```

## Useful Remote Commands

```bash
# System info
ssh serpico@100.109.105.19 "uname -a && df -h && free -h"

# Docker status
ssh serpico@100.109.105.19 "docker ps --format 'table {{.Names}}\t{{.Status}}' | grep raindrops"

# Check ports
ssh serpico@100.109.105.19 "netstat -tlnp | grep -E '908[0-3]|8089'"

# View logs
ssh serpico@100.109.105.19 "docker logs raindrops-witness --tail 50"

# Check processes
ssh serpico@100.109.105.19 "ps aux | grep java"

# Disk usage
ssh serpico@100.109.105.19 "du -sh /home/serpico/raindrops-fase1/ /mnt/m2-storage/ /mnt/storage/"
```

## Troubleshooting SSH

| Problem | Solution |
|---------|----------|
| Connection refused | Check if Pi is on: `ping 100.109.105.19` |
| Permission denied | Verify password, check SSH key setup |
| Connection timeout | Check VPN/Tailscale connectivity |
| Slow response | Pi might be overloaded: check `top` |
| Broken pipe | Reconnect: `ssh serpico@100.109.105.19` |

## PowerShell SSH Notes

PowerShell uses `;` not `&&` for command chaining:
```powershell
# Wrong (PowerShell)
ssh host "cmd1 && cmd2"

# Right (PowerShell)
ssh host "cmd1; if ($?) { cmd2 }"
```

## When to use me

Use this skill when connecting to the Pi, transferring files, or executing remote commands. Always verify SSH connectivity before remote operations.

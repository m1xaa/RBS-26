# Oblak

## Before Running

Firecracker-based project preparation needs a TAP device and host NAT configured first. Run this once in WSL before starting the application:

```bash
cd ~/project/RBS-26/oblak
sudo bash src/main/resources/firecracker/scripts/setup-firecracker-network.sh tap0
```

This creates the `tap0` interface, enables forwarding, and configures NAT so the microVM can access the internet and install dependencies from `requirements.txt`.

## Start The Application

```bash
cd ~/project/RBS-26/oblak
./gradlew bootRun
```

# Oblak CDK CLI

Komandnolinijski klijent za upload fajlova na Oblak platformu.

## Instalacija

Iz `cdk-cli` foldera:

```
pip install -e .
```

## Komande

```
cdk config --server http://localhost:8080   # podesi server
cdk register -u jovana                       # napravi nalog
cdk login -u jovana                          # prijavi se
cdk whoami                                   # ko sam ja
cdk upload C:\putanja\do\fajla.zip           # upload fajla
cdk logout                                   # odjava
```

## Tipično korišćenje

```
cdk config --server http://localhost:8080
cdk register -u user
cdk upload C:\Users\User\Desktop\test.zip
```
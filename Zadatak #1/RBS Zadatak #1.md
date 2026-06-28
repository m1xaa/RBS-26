**Enkripcija tajni (pa i lozinki)**

## **Enkripcija korisničkih lozinki koje moraju biti čitljive**

U savremenim sistemima lozinke za autentikaciju se gotovo uvek čuvaju u obliku heša, jer za njih obično ne postoji potreba da budu ponovo pročitane. Međutim, u sistemima za upravljanje tajnama, password manager rešenjima ili aplikacijama koje moraju povremeno da otkriju originalnu lozinku korisniku ili drugoj komponenti sistema, heširanje nije dovoljno. U takvim slučajevima neophodna je reverzibilna zaštita, odnosno enkripcija. Zbog toga je cilj ovog mehanizma da zaštiti poverljivost korisničkih lozinki, ali tako da ovlašćeni sistem ipak može da ih dekriptuje kada je to potrebno. OWASP pritom naglašava da reverzibilna enkripcija nije odgovarajući izbor za klasično skladištenje autentikacionih lozinki, ali jeste opravdana kada aplikacija zaista mora da ih ponovo pročita.

### **Algoritam za generisanje ključa iz glavne lozinke**

Najvažniji korak u ovakvom sistemu jeste da se glavni ključ ne dobija direktno iz master lozinke, jer lozinke imaju relativno nisku entropiju i nisu pogodne da se koriste kao sirovi kriptografski ključevi. NIST eksplicitno navodi da lozinke nisu pogodne da se direktno koriste kao ključevi i da treba koristiti password-based key derivation function, odnosno PBKDF.

Kao kandidati za ovu namenu najčešće se razmatraju PBKDF2, scrypt i Argon2id. PBKDF2 je dugo prisutan, široko podržan i i dalje je NIST-odobren mehanizam. Međutim, njegova glavna slabost je što je pre svega CPU-hard, dok savremeni napadači koriste GPU i specijalizovani hardver. Scrypt uvodi memorijsku zahtevnost i time otežava napade na specijalizovanom hardveru, ali danas se Argon2id uglavnom smatra boljim izborom. OWASP danas preporučuje upravo Argon2id kao prvi izbor, a RFC 9106 opisuje Argon2id kao varijantu koja kombinuje zaštitu od side-channel napada i dobru otpornost na brute-force i time-memory tradeoff napade.

Zbog toga je za ovaj zadatak najprikladniji izbor **Argon2id** kao algoritam za derivaciju ključa iz master lozinke. PBKDF2 treba zadržati samo kao kompatibilni fallback u okruženjima gde je potrebna stroga usklađenost sa starijim standardima. OWASP za Argon2id navodi minimalnu preporuku od 19 MiB memorije, 2 iteracije i paralelizam 1, dok RFC 9106 daje jače preporučene profile, uključujući profil od 64 MiB memorije, 3 prolaza i 4 lane-a za memorijski ograničena okruženja.

### **Simetrični algoritam za enkripciju i dekripciju**

Kod simetrične enkripcije nije dovoljno izabrati samo algoritam, već i režim rada. Savremena praksa zahteva authenticated encryption, odnosno zaštitu i poverljivosti i integriteta. OWASP preporučuje da se koriste autentifikovani režimi kao što su GCM ili CCM, dok NIST SP 800-38D formalno standardizuje GCM kao authenticated encryption režim za blok šifre kao što je AES.

Kandidati za ovaj zadatak su pre svega AES-GCM i XChaCha20-Poly1305. AES-GCM ima najveću interoperabilnost, široku podršku u bibliotekama i hardversko ubrzanje na mnogim platformama. Google Tink navodi da je AES-GCM obično najbrži AEAD režim, ali istovremeno upozorava da ima stroge granice i da pogrešna upotreba, naročito oko nonca, može imati katastrofalne posledice. Sa druge strane, XChaCha20-Poly1305 ima znatno veći nonce i praktično je otporniji na greške pri radu sa nasumičnim nonce vrednostima; libsodium ga preporučuje kada interoperabilnost sa drugim bibliotekama nije glavni zahtev.

Za ovaj mehanizam bih odabrao **AES-256-GCM** kao primarni algoritam za enkripciju korisničkih lozinki, jer predstavlja najzreliji kompromis između bezbednosti, standardizacije, performansi i podrške u provajderima. Kao alternativu bih naveo **XChaCha20-Poly1305** u sistemima koji se oslanjaju na libsodium i gde je prioritet jednostavnije bezbedno rukovanje nonce vrednostima. OWASP preporučuje AES sa ključem od najmanje 128 bita, idealno 256 bita, uz autentifikovani režim poput GCM.

### **Preporučena konfiguracija**

Za derivaciju ključa iz master lozinke preporučena konfiguracija je sledeća: koristi se Argon2id sa nasumičnim salt-om od najmanje 128 bita, izlaznim ključem od 256 bita, paralelizmom 4 i sa parametrima koji su dovoljno skupi da uspore offline napade, ali da i dalje budu prihvatljivi za legitimnog korisnika. Kao praktičan profil za većinu serverskih i desktop sistema može da se uzme RFC 9106 “second recommended option”: **m \= 64 MiB, t \= 3, p \= 4, salt \= 128 bita, tag/output \= 256 bita**. Ako sistem raspolaže sa više memorije i želi viši nivo zaštite, može se koristiti i jači profil sa 2 GiB memorije i jednim prolazom.

Ako je potreban PBKDF2 fallback, tada ga treba koristiti sa HMAC-SHA-256 ili jačom hash funkcijom, nasumičnim salt-om od najmanje 128 bita i veoma visokim brojem iteracija. NIST SP 800-132 navodi da salt treba da ima nasumični deo od najmanje 128 bita i da broj iteracija treba izabrati što je moguće veći uz prihvatljive performanse, uz stariju minimalnu preporuku od 1.000 iteracija. Ta vrednost danas više nije dovoljna za moderne sisteme, pa bi u praksi broj iteracija morao biti višestruko veći i periodično reevaluiran.

Za enkripciju same lozinke preporučuje se AES-256-GCM sa jedinstvenim IV/nonce za svaku operaciju enkripcije, generisanim kriptografski bezbednim generatorom slučajnih brojeva. GCM je AEAD režim, što znači da obezbeđuje i poverljivost i integritet. NIST je 2024\. objavio da u reviziji SP 800-38D planira uklanjanje podrške za autentikacione tagove kraće od 96 bita, pa je razumno koristiti tag dužine najmanje 96 bita, a u praksi standardnih 128 bita.

### **Predlog arhitekture mehanizma**

Bezbedna implementacija ne treba da koristi master lozinku direktno za enkripciju svih korisničkih lozinki. Mnogo bolje rešenje je envelope encryption pristup. Iz master lozinke se pomoću Argon2id izvodi **KEK** (Key Encryption Key). Za svaku korisničku lozinku ili zapis generiše se poseban nasumični **DEK** (Data Encryption Key). Sam DEK se koristi za AES-256-GCM enkripciju lozinke, a zatim se taj DEK dodatno enkriptuje KEK-om. OWASP upravo preporučuje razdvajanje DEK i KEK i odvajanje ključeva od podataka kada god je to moguće.

Prednost ovakvog pristupa je što kompromitacija jednog zapisa ne ugrožava nužno sve ostale zapise, a rotacija master lozinke može da se svede na ponovno enkriptovanje DEK vrednosti, umesto ponovne enkripcije svih korisničkih lozinki. Uz svaki zapis treba čuvati metapodatke: identifikator algoritma, verziju šeme, Argon2id parametre, salt, IV/nonce, ciphertext, autentikacioni tag i identifikator ključa. Ovo omogućava buduću migraciju na novu konfiguraciju bez gubitka kompatibilnosti. Preporuke OWASP-a o rotaciji, odvajanju ključeva i verzionisanju ključeva to podržavaju.

### **Odabir pouzdanih provajdera**

Pri izboru provajdera važno je koristiti zrele, auditovane i široko prihvaćene biblioteke, a ne implementirati sopstvenu kriptografiju. OWASP eksplicitno upozorava da se ne koriste sopstveni algoritmi i da se oslanja na kvalitetne biblioteke.

Za opštu upotrebu veoma dobar izbor su **libsodium** i **Google Tink**. Libsodium je moderna biblioteka fokusirana na bezbedne podrazumevane vrednosti i jednostavan API, dok Tink nudi visok nivo apstrakcije i smanjuje verovatnoću pogrešne upotrebe kriptografskih primitiva. Tink dokumentacija detaljno pokriva AEAD mehanizme, uključujući AES-GCM i XChaCha20-Poly1305.

U Java okruženju realističan izbor su **Bouncy Castle** ili JCA/JCE provajderi koje obezbeđuje platforma. Ipak, Bouncy Castle zahteva pažljivo upravljanje verzijama, jer su tokom 2025\. ispravljene pojedine CVE ranjivosti i objavljene zakrpe u novijim LTS verzijama. Bouncy Castle Java LTS 2.73.8, na primer, navodi ispravke za CVE-2025-9340 i CVE-2025-9341.

**OpenSSL** je i dalje relevantan i vrlo rasprostranjen provajder, naročito u C/C++ i sistemskim okruženjima, ali njegov izbor takođe mora biti praćen redovnim ažuriranjem zbog istorije ozbiljnih ranjivosti i novijih bezbednosnih ispravki.

### **Aktuelne ranjivosti i bezbednosni status implementacija**

Pri proveri da li poslednje verzije implementacija imaju ozbiljnijih ranjivosti, važno je razlikovati ranjivosti u samim algoritmima od ranjivosti u bibliotekama koje ih implementiraju. Za Argon2id kao konstrukciju nema široko prihvaćenih savremenih preporuka za povlačenje; naprotiv, RFC 9106 i OWASP ga i dalje preporučuju kao prvi izbor za password-based derivaciju ključa.

Kod **OpenSSL-a** su i u 2025\. i 2026\. zabeležene ranjivosti, ali one nisu bile usmerene na “sam AES-GCM kao algoritam”, već na određene delove biblioteke, poput CMS i PKCS\#12 parsiranja. OpenSSL je 27\. januara 2026\. objavio, između ostalog, **CVE-2025-15467**, visoko ocenjen stack buffer overflow u CMS (Auth)EnvelopedData parsiranju, kao i **CVE-2025-11187** vezan za PBMAC1 parametre u PKCS\#12 verifikaciji. Zbog toga je preporuka da se koristi samo ažurirana grana i da se izbegavaju zastarele verzije; OpenSSL sam navodi da su verzije pre 1.1.1 van podrške, a da i novije grane zahtevaju pravovremeno patchovanje.

Kod **Bouncy Castle-a** su tokom 2025\. takođe objavljene i zakrpljene pojedine ranjivosti, što ne znači da biblioteku treba odbaciti, već da je neophodno striktno koristiti poslednje podržane verzije i pratiti bezbednosna obaveštenja proizvođača.

Za **libsodium** nisam našao signal o nekoj aktuelnoj velikoj javno dokumentovanoj kritičnoj ranjivosti u samom jezgru biblioteke u izvorima koje sam proverio, dok poslednje release napomene čak navode dodatna side-channel poboljšanja u kritičnim putanjama koda. To ipak ne znači da je rizik nula, već da je praksa redovnog ažuriranja i dalje obavezna.

### **Zahtevi za bezbednu implementaciju**

Na osnovu prethodne analize, mehanizam treba da ispuni sledeće zahteve:

Master lozinka nikada ne sme da se koristi direktno kao AES ključ. Iz nje se ključ izvodi pomoću Argon2id sa jakim parametrima i jedinstvenim salt-om po korisniku ili po šifarnom kontekstu.

Za svaku korisničku lozinku treba koristiti poseban nasumično generisan DEK, dok se iz master lozinke izvodi KEK kojim se štiti DEK. Ključevi i šifrovani podaci treba da budu logički ili fizički razdvojeni kad god je to moguće.

Za enkripciju treba koristiti authenticated encryption, konkretno AES-256-GCM, sa jedinstvenim nonce/IV za svaku operaciju i sa autentikacionim tagom od najmanje 96 bita, praktično 128 bita. Nikada se ne sme ponovo koristiti isti nonce sa istim ključem.

Sve slučajne vrednosti, uključujući salt, DEK i IV, moraju biti generisane pomoću CSPRNG mehanizma koji preporučuje platforma ili biblioteka. OWASP posebno upozorava da obični PRNG ne smeju da se koriste za bezbednosno osetljive funkcije.

Sistem mora da podrži verzionisanje šeme i rotaciju ključeva. Svaki zapis treba da sadrži informaciju o verziji algoritma i parametara kako bi se omogućila kasnija migracija na novu konfiguraciju. OWASP preporučuje formalne procese za generisanje, skladištenje, rotaciju i povlačenje ključeva.

Ključevi treba da budu dostupni samo minimalnom delu sistema koji zaista mora da vrši dekripciju. Ako infrastruktura to dozvoljava, KEK treba čuvati u posebnom secrets management ili KMS rešenju, a ne u istoj bazi kao i šifrovani podaci. OWASP navodi da posebni secret management sistemi daju dodatni sloj zaštite.

Potrebno je redovno pratiti bezbednosna obaveštenja za izabrani provajder i odmah uvoditi zakrpe za podržane verzije. Posebno je rizično ostajanje na granama biblioteka koje su van podrške.

### **Linkovi i reference**

[OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)

[OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

[NIST SP 800-132 – Recommendation for Password-Based Key Derivation Part 1: Storage Applications](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-132.pdf)

[RFC 9106 – Argon2 Memory-Hard Function for Password Hashing and Proof-of-Work Applications](https://www.rfc-editor.org/rfc/rfc9106.html)

[NIST SP 800-38D – Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM) and GMAC](https://csrc.nist.gov/pubs/sp/800/38/d/final)

[NIST – Announcement on Revision of SP 800-38D](https://csrc.nist.gov/news/2024/nist-to-revise-sp-80038d-gcm-and-gmac-modes)

[Google Tink AEAD Documentation](https://developers.google.com/tink/aead)

[Libsodium – Official Repository](%20https://github.com/jedisct1/libsodium)

[Libsodium – Releases](%20https://github.com/jedisct1/libsodium/releases)

[OpenSSL Vulnerability Advisories](https://openssl-library.org/news/vulnerabilities/)

[Bouncy Castle – Java LTS 2.73.8 Release Notes](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-lts-2-73-8/)

## 

## 

## 

## 

## 

## 

## 

## **Mehanizam revizije (auditing)** 

Log datoteke predstavljaju ključnu komponentu svakog softverskog sistema, jer omogućavaju praćenje rada aplikacije, detekciju grešaka i analizu bezbednosnih događaja. Pored njihove uloge u održavanju sistema, logovi imaju značajnu funkciju u oblasti bezbednosti, posebno u kontekstu neporicanja odgovornosti (non-repudiation), gde omogućavaju identifikaciju aktera i njihovih akcija.

Cilj ovog rada je dizajn mehanizma za logovanje koji zadovoljava zahteve u pogledu bezbednosti, pouzdanosti, preciznosti i efikasnosti, uz korišćenje modernih alata kao što je ELK stack (Elasticsearch, Logstash, Kibana).

## **Zahtevi i način njihove realizacije**

### **1\. Logovi kao alat za rešavanje problema**

Da bi logovi bili korisni za dijagnostiku, potrebno je obezbediti dovoljno konteksta. Svaka log stavka treba da sadrži nivo logovanja (INFO, ERROR, DEBUG), vremensku oznaku, identifikator zahteva (traceId), naziv servisa i opis događaja.

Preporučuje se korišćenje strukturiranih logova u JSON formatu, jer omogućavaju lakšu obradu i pretragu. Biblioteke poput Logback-a ili Winston-a omogućavaju jednostavno generisanje ovakvih logova.

### **2\. Non-repudiation (neporicanje odgovornosti)**

Za bezbednosno kritične događaje potrebno je evidentirati:

* identitet korisnika  
* IP adresu i uređaj  
* izvršenu akciju  
* rezultat akcije

Kako bi se sprečilo manipulisanje logovima, mogu se koristiti tehnike kao što su digitalni potpisi ili ulančavanje hash vrednosti (hash chain). Takođe, preporučuje se čuvanje ovih logova u centralizovanom sistemu sa ograničenim pristupom i append-only principom.

### **3\. Zaštita osetljivih podataka**

Logovi ne smeju sadržati poverljive informacije kao što su lozinke, tokeni ili finansijski podaci.

Ovo se postiže:

* maskiranjem podataka (npr. email adrese ili brojeva kartica)  
* sanitizacijom ulaza pre logovanja  
* filtriranjem podataka u obradi (npr. kroz Logstash)

### **4\. Pouzdanost, dostupnost i integritet**

Pouzdan sistem za logovanje zahteva:

* centralizovano skladištenje  
* replikaciju podataka  
* redovan backup

U ELK arhitekturi, Elasticsearch omogućava replikaciju indeksa, dok se sigurnost dodatno unapređuje korišćenjem TLS enkripcije i kontrole pristupa (RBAC).

### **5\. Precizno vreme**

Svi logovi moraju koristiti standardizovan vremenski format (ISO 8601\) i UTC vremensku zonu. Sinhronizacija servera se obezbeđuje putem NTP protokola kako bi se izbegla odstupanja.

### **6\. Optimizacija i smanjenje pretrpanosti**

Da bi logovi ostali pregledni:

* koristi se odgovarajući nivo logovanja  
* DEBUG logovi se ograničavaju na razvojno okruženje  
* primenjuje se sampling i agregacija događaja

## **Rotacija logova**

### **Tradicionalni pristup (logrotate)**

Alat logrotate omogućava automatsku rotaciju, kompresiju i brisanje starih logova. Međutim, postoje poznati problemi:

* log injection (ubacivanje lažnih linija)  
* race condition tokom rotacije

Ovi problemi se ublažavaju validacijom ulaza i pravilnim podešavanjem dozvola nad fajlovima.

### **Moderni pristupi**

Docker omogućava rotaciju logova putem opcija kao što su max-size i max-file, čime se automatski ograničava veličina i broj log fajlova.

Cloud rešenja poput AWS CloudWatch ili GCP Logging dodatno pojednostavljuju upravljanje logovima kroz automatsku skalabilnost i integrisani monitoring.

## **ELK Stack arhitektura**

### **Pipeline za prijem logova**

Proces započinje u aplikaciji, gde se generišu logovi. Filebeat agent ih prikuplja i prosleđuje Logstash-u, koji vrši obradu i transformaciju. Nakon toga, logovi se skladište u Elasticsearch-u.

### **Elasticsearch (indeksiranje i čuvanje)**

Logovi se organizuju u indekse, gde svaki zapis predstavlja dokument. Definišu se mapping pravila za tipove podataka (npr. timestamp kao date, nivo kao keyword).

Sharding omogućava skaliranje, dok replike obezbeđuju visoku dostupnost i otpornost na greške.

### **Kibana (vizualizacija i analiza)**

Kibana omogućava pretragu i filtriranje logova kroz upite, kao i kreiranje dashboard-a.

Moguće je:

* filtrirati greške po servisu  
* analizirati broj neuspešnih login pokušaja  
* identifikovati sumnjive IP adrese

Ovo je posebno značajno za detekciju bezbednosnih incidenata.

## **Reference**

[Elastic documentation – Elasticsearch, Logstash, Kibana](https://www.elastic.co/docs/get-started)

[OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)

[Linux logrotate documentation](https://linux.die.net/man/8/logrotate)

[Docker logging drivers documentation](https://docs.docker.com/engine/logging/configure/)

[AWS CloudWatch Logs documentation](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/WhatIsCloudWatchLogs.html)

[Google Cloud Logging documentation](https://docs.cloud.google.com/logging/docs) 

## 

## **Višefaktorska autentifikacija**

Višefaktorska autentikacija (MFA) predstavlja mehanizam kojim se povećava bezbednost sistema tako što se od korisnika zahteva potvrda identiteta korišćenjem više nezavisnih faktora. U kontekstu sistema koji zavise od master lozinke, MFA značajno smanjuje rizik kompromitacije naloga, jer napadač mora da kompromituje više različitih elemenata autentikacije.

Faktori autentikacije se najčešće dele na nešto što korisnik zna (lozinka), nešto što korisnik ima (token, telefon) i nešto što korisnik jeste (biometrija).

## **Tipovi višefaktorske autentikacije**

Najčešći tipovi faktora u web aplikacijama su:

* **Knowledge faktor** – lozinka ili PIN  
* **Possession faktor** – mobilni uređaj, hardverski token, aplikacija za generisanje kodova  
* **Biometric faktor** – otisak prsta, prepoznavanje lica

Pored ovih osnovnih kategorija, u praksi se koriste i:

* SMS ili email kodovi (slabiji possession faktor)  
* push notifikacije (npr. odobravanje logina)  
* hardverski sigurnosni ključevi (FIDO2/WebAuthn)

SMS i email se danas smatraju slabijim zbog mogućnosti presretanja (SIM swap napadi), dok se TOTP i hardverski tokeni smatraju znatno sigurnijim.

## **Odabrani faktori**

Za ovaj sistem izabrana je kombinacija:

* lozinka (knowledge faktor)  
* TOTP (Time-based One-Time Password) putem aplikacije kao što je Google Authenticator

Ova kombinacija je široko prihvaćena i predstavlja dobar balans između bezbednosti i jednostavnosti implementacije.

## **Implementacija lozinke**

Lozinka se koristi kao prvi faktor autentikacije. Ona se ne čuva u čitljivom obliku, već kao kriptografski hash (npr. Argon2id).

Proces autentikacije:

1. korisnik unosi korisničko ime i lozinku  
2. sistem proverava hash vrednost  
3. ako je uspešno, prelazi se na drugi faktor

## **Implementacija TOTP mehanizma**

TOTP je standardizovan u RFC 6238 i zasniva se na deljenoj tajni (secret key) i trenutnom vremenu.

### **Generisanje TOTP koda**

* sistem generiše nasumični secret (npr. 160-bit)  
* secret se prikazuje korisniku kao QR kod  
* korisnik ga skenira u aplikaciji (npr. Google Authenticator)  
* aplikacija generiše kod na svakih 30 sekundi

### **Verifikacija TOTP koda**

Tokom logovanja:

1. korisnik unosi TOTP kod  
2. server generiše očekivani kod koristeći isti secret i trenutno vreme  
3. dozvoljava se mali vremenski prozor (npr. ±1 interval)  
4. ako se kod poklapa → autentikacija uspešna

### **Obnavljanje (recovery)**

Pošto korisnik može izgubiti uređaj, potrebno je obezbediti:

* backup kodove (jednokratni)  
* reset MFA uz dodatnu verifikaciju (npr. email \+ identitet)

Backup kodovi moraju biti:

* nasumični  
* dovoljno dugi  
* čuvani kao hash vrednosti

## **Najčešće greške i bezbednosni propusti**

### **Problemi sa TOTP-om**

* **Sinhronizacija vremena**  
   Ako server i klijent nisu sinhronizovani, validni kodovi mogu biti odbijeni.  
   Rešenje: NTP sinhronizacija i tolerancija u vremenskom prozoru.  
* **Brute-force napadi**  
   Napadač pokušava veliki broj kodova.  
   Rešenje: rate limiting i zaključavanje naloga.  
* **Neadekvatna zaštita secret ključa**  
   Ako se secret kompromituje, napadač može generisati kodove.  
   Rešenje: čuvanje u enkriptovanom obliku.  
* **Slabi backup kodovi**  
   Kratki ili predvidivi kodovi mogu biti pogođeni.

### **Opšti MFA problemi**

* MFA bypass (npr. fallback na email bez zaštite)  
* phishing napadi (korisnik unese kod na lažnoj stranici)  
* čuvanje podataka u logovima (npr. TOTP kodova)  
* nedostatak rate limiting-a

## **Integracija MFA u ELK okruženju**

### **MFA u Kibani (admin login)**

U ELK stack-u, MFA se integriše kroz sigurnosne mehanizme Elasticsearch-a i Kibane.

Proces:

1. korisnik pokušava login u Kibanu  
2. autentikacija se vrši preko identity provajdera (npr. SAML, OAuth, OpenID Connect)  
3. MFA se implementira na nivou tog provajdera

### **Mogući pristupi**

* integracija sa identity provider-om koji podržava MFA  
* korišćenje TOTP ili push autentikacije  
* korišćenje enterprise rešenja (npr. Okta, Auth0)

### **Primer toka autentikacije**

1. korisnik unosi kredencijale u Kibani  
2. preusmerava se na identity provider  
3. unosi lozinku  
4. unosi TOTP kod  
5. nakon uspešne verifikacije dobija pristup Kibani

### **Bezbednosne prednosti u ELK okruženju**

* zaštita administrativnog pristupa  
* smanjenje rizika od kompromitacije log sistema  
* kontrola pristupa osetljivim podacima

## **Reference**

[OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

[OWASP Multifactor Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)

[RFC 6238 – TOTP: Time-Based One-Time Password Algorithm](https://datatracker.ietf.org/doc/html/rfc6238)

[NIST Digital Identity Guidelines (SP 800-63B)](https://nvlpubs.nist.gov/nistpubs/specialpublications/nist.sp.800-63b.pdf)

[Elastic Security Documentation](https://www.elastic.co/docs/solutions/security)

[Google Authenticator documentation](https://support.google.com/accounts/answer/1066447?hl=en&co=GENIE.Platform%3DAndroid)


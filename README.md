# Ecom Whats Me
Créé et déployé par Ecom Academy — ecom.academy.tg@gmail.com

Archive automatiquement les messages WhatsApp / WhatsApp Business (texte, photos, vidéos, audios, vocaux),
y compris pendant une discussion ouverte ou archivée, et affiche en priorité ceux qui ont été supprimés.

## Ce dépôt peut être PUBLIC
La clé de signature n'est plus stockée dans le dépôt (voir ci-dessous) : elle est reconstruite à chaque
compilation à partir d'un secret GitHub. Le dépôt peut donc être public sans rien exposer de sensible.

## Clé de signature — à faire une seule fois
1. Génère une clé si tu n'en as pas déjà une (sur ta machine, avec `keytool`, déjà installé avec Java) :
   ```
   keytool -genkeypair -noprompt -keystore ecom.keystore -alias ecom -keyalg RSA -keysize 2048 \
     -validity 36500 -storepass TON_MOT_DE_PASSE -keypass TON_MOT_DE_PASSE \
     -dname "CN=Ecom Academy, OU=Ecom Whats Me, O=Ecom Academy, L=Lome, C=TG"
   ```
2. Encode-la en base64 :
   - Linux/Mac : `base64 -w0 ecom.keystore`
   - Windows (PowerShell) : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("ecom.keystore"))`
3. Dans GitHub : Settings > Secrets and variables > Actions > *New repository secret*, crée :
   - `KEYSTORE_BASE64` = le texte obtenu à l'étape 2
   - `KEYSTORE_STORE_PASSWORD` = ton mot de passe
   - `KEYSTORE_KEY_ALIAS` = `ecom`
   - `KEYSTORE_KEY_PASSWORD` = ton mot de passe (le même que `KEYSTORE_STORE_PASSWORD` si tu as suivi l'étape 1)
4. **Garde une copie de `ecom.keystore` et de ces mots de passe en lieu sûr, en dehors de GitHub.**
   Si tu les perds, plus aucune future mise à jour ne pourra s'installer par-dessus l'app déjà
   installée chez tes clients — il faudrait republier une toute nouvelle application.

## Compilation
Chaque push sur `main` :
1. reconstruit la clé de signature à partir des secrets ci-dessus, puis compile l'APK → onglet Actions
   > artefact `EcomWhatsMe-apk` ;
2. déploie `admin/index.html` sur GitHub Pages.

## Activer GitHub Pages — à faire une seule fois
Repo public requis (c'est le cas ici). Settings > Pages > Source = **GitHub Actions**.
Après le prochain push, la page admin sera disponible à l'adresse indiquée dans l'onglet Pages
(généralement `https://tonpseudo.github.io/nom-du-depot/`).

## Base de données
Exécute une seule fois le SQL fourni dans Supabase (SQL Editor > Run). Il crée les tables, verrouille
l'accès direct, et ne laisse passer que les fonctions RPC utilisées par l'application et par `admin/index.html`.
Pense à changer le mot de passe admin par défaut dans ce script avant de l'exécuter.

## Boutique des clés
- Clé 30 jours (9 caractères, ex `jY8k 4Asx`) : https://wldadmwl.mychariow.shop/prd_hv0ptlrs
- Clé 90 jours (12 caractères, ex `Hg78kB32S4xP`) : https://wldadmwl.mychariow.shop/prd_t34vacc7

# Ecom Whats Me
Créé et déployé par Ecom Academy — ecom.academy.tg@gmail.com

Archive automatiquement les messages WhatsApp / WhatsApp Business (texte, photos, vidéos, audios, vocaux),
y compris pendant une discussion ouverte ou archivée, et affiche en priorité ceux qui ont été supprimés.

## Compilation
Chaque push sur `main` compile l'APK signé → onglet Actions → artefact `EcomWhatsMe-apk`.

Garde ce dépôt en **PRIVÉ** : la clé de signature `app/ecom.keystore` y est stockée.

## Interface admin / GitHub Pages
GitHub Pages via Actions ne fonctionne pas sur un dépôt privé avec un compte gratuit. La page
`admin/index.html` est donc publiée automatiquement vers un **second dépôt, public, sans aucun secret**
(la clé Supabase qu'il contient est une clé publique protégée par mot de passe côté base de données,
la même que celle déjà embarquée dans l'APK).

À faire une seule fois :
1. Crée un nouveau dépôt GitHub, **public**, vide, par exemple `EcomWhatsMe-Admin`.
2. Dans ce nouveau dépôt : Settings > Pages > Source = *Deploy from a branch* > branche `gh-pages` > dossier `/ (root)`.
   (La branche `gh-pages` sera créée automatiquement au premier déploiement, l'option peut rester grisée en attendant.)
3. Crée un jeton d'accès (github.com/settings/tokens) de type *Fine-grained*, limité à ce seul dépôt
   `EcomWhatsMe-Admin`, avec la permission *Contents: Read and write*.
4. Dans **ce dépôt-ci** (le privé) : Settings > Secrets and variables > Actions > *New repository secret*,
   et crée deux secrets :
   - `ADMIN_PAGES_TOKEN` = le jeton créé à l'étape 3
   - `ADMIN_PAGES_REPO` = `tonpseudo/EcomWhatsMe-Admin` (nom exact du dépôt public)
5. Repousse n'importe quel commit sur `main` : la page se publie sur
   `https://tonpseudo.github.io/EcomWhatsMe-Admin/`.

Tant que ces deux secrets ne sont pas définis, cette étape est simplement ignorée (la compilation de
l'APK continue de fonctionner normalement).

## Base de données
Exécute une seule fois `supabase/schema.sql` dans Supabase (SQL Editor > Run). Il crée les tables, verrouille
l'accès direct, et ne laisse passer que les fonctions RPC utilisées par l'application et par `admin/index.html`.
Pense à changer le mot de passe admin par défaut (`change-me-now`) dans ce fichier avant de l'exécuter.

## Boutique des clés
- Clé 30 jours (9 caractères, ex `jY8k 4Asx`) : https://wldadmwl.mychariow.shop/prd_hv0ptlrs
- Clé 90 jours (12 caractères, ex `Hg78kB32S4xP`) : https://wldadmwl.mychariow.shop/prd_t34vacc7

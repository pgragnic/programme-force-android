# UpRep — appli Android

Appli Kotlin (Jetpack Compose) du programme de musculation sur 8 semaines avec haltères réglables.
Données synchronisées dans Supabase (une ligne par séance, accès réservé au compte connecté).

## Installer
Page **Releases** du dépôt → dernière version → télécharger `ProgrammeForce.apk` sur le téléphone → l'ouvrir
(Android demande d'autoriser l'installation depuis le navigateur la première fois).

## Étapes
1. Séance guidée, voix, saisie série par série, historique, Supabase ← en cours
2. Personnage 3D natif (Filament) et carte des muscles
3. Pas via Health Connect, notifications, commandes sur l'écran verrouillé
4. Reprise de l'historique de la version web

## Configuration Supabase
- `supabase/schema.sql` : à exécuter une fois dans l'éditeur SQL du projet Supabase.
- `supabase.properties` (racine) : `url=` et `key=` (clé *anon/publishable* uniquement, jamais la clé secrète).

Personnage 3D : MakeHuman (domaine public, CC0).

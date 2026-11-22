# Formulaire « Sécurité des données » (Play Console) — réponses pour UpRep 1.2

Préparé le 18 novembre 2026 (Philippe, 23 h 15 : « Que pourrait-on encore améliorer avant déploiement ? » ; 23 h 18 : « Yes start »).
Play Console → Règles et programmes → Contenu de l'appli → Sécurité des données → Modifier.

## 1. Collecte et sécurité
- L'appli collecte-t-elle ou partage-t-elle des données utilisateur ? **Oui**
- Toutes les données sont-elles chiffrées en transit ? **Oui** (HTTPS vers Supabase, Anthropic, Google)
- Les utilisateurs peuvent-ils demander la suppression de leurs données ? **Oui** — dans l'appli (Réglages → À propos → Supprimer mon compte) et sur https://pgragnic.github.io/programme-force-android/suppression.html

## 2. Types de données (cocher « Collectées » ; « Partagées » = **Non** partout)
Anthropic (coach) et Supabase (hébergement) traitent les données pour le compte d'UpRep : pour Google, ce sont des prestataires, pas un partage.

| Catégorie Play | Type | Pourquoi | Facultatif ? |
|---|---|---|---|
| Infos personnelles | Adresse e-mail | Gestion du compte | Oui (compte facultatif) |
| Infos personnelles | Nom | Gestion du compte (connexion Google) | Oui |
| Infos personnelles | ID utilisateur | Gestion du compte, fonctionnalité | Oui |
| Infos personnelles | Autres infos (taille, sexe, année de naissance) | Fonctionnalité (profil) | Oui |
| Santé et remise en forme | Infos sur la santé (gênes, restrictions de santé, zones ménagées, poids du corps) | Fonctionnalité, personnalisation | Oui |
| Santé et remise en forme | Infos sur la remise en forme (séances, charges, pas via Health Connect) | Fonctionnalité, personnalisation | Oui |
| Messages | Autres messages dans l'appli (discussion avec le coach) | Fonctionnalité | Oui (coach réservé aux comptes) |
| Activité dans l'appli | Autres contenus générés par l'utilisateur (avis et idées envoyés depuis l'appli, objectifs du coach) | Fonctionnalité, assistance | Oui |
| Infos sur les applis et performances | Journaux de plantage | Analyse, correction des plantages | **Non** (envoyés automatiquement, sans lien avec l'identité ; e-mails, jetons et identifiants masqués avant l'envoi depuis la 1.2.4, audit de sécurité du 22 novembre) |

Pour chaque type : « Traitées de façon éphémère » = **Non** (sauf rien) ; pas de publicité, pas de marketing.

## 3. Comptes (questions de la même section)
- L'appli permet-elle de créer un compte ? **Oui** — adresse e-mail et mot de passe, ou connexion Google (OAuth) ; compte facultatif.
- Lien de suppression du compte et des données : https://pgragnic.github.io/programme-force-android/suppression.html
- Suppression de certaines données sans supprimer le compte : **Oui** — « Ce que sait le coach » (souvenirs supprimables) et effacement des séances dans l'appli.

Relu le 22 novembre 2026 après l'audit de sécurité (Philippe : « Formulaire sécurité des données ») : aucun nouveau type de données depuis la 1.2 ; seuls changements, le masquage dans les journaux de plantage et cette section Comptes.

Sauvegarde chiffrée de nuit (22 novembre 2026, plan de reprise) : copie de la base gardée 7 jours chez Anthropic, prestataire ; rien à changer dans le formulaire (pas de nouveau type de données, pas de partage, suppression toujours possible). Politique de confidentialité et pages de suppression mises à jour (Philippe : « Ok pour les textes »).

## 4. Autres formulaires à vérifier
- Accès à l'appli : « Certaines fonctionnalités sont limitées » → compte de test (e-mail + mot de passe) avec la consigne « Coach : onglet Coach → Parler au coach ».
- Health Connect : lecture des pas seulement, pour l'onglet Progrès → Activité, facultatif.
- Politique de confidentialité : https://pgragnic.github.io/programme-force-android/confidentialite.html (mentionne Anthropic et Google).

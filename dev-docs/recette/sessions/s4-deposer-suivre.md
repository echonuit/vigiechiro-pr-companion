# S4 · Déposer et suivre

> **Écran propriétaire** : lot (préparer, générer, déposer, déposé, alertes).
> **Features** : lot, depot-vigiechiro, synchronisation-participation. · **Statut : à jouer** (passe
> statique prête ; le script sera annoté après la séance).
> Retour à la [méthode](../index.md).

## Objectif

Déposer une nuit vérifiée : le lot en quatre temps, le dépôt **réel** sur la plateforme, « Lancer la
participation », puis le suivi du traitement. S4 est la première session qui **écrit sur le serveur**.

!!! danger "Ce qu'un dépôt écrit, et que l'application ne peut pas défaire"
    Sur un carré **relié** (130711), un dépôt écrit, dans l'ordre : `POST …/participations` (participation),
    puis par archive `POST /fichiers` + `PUT` S3 signé + `POST /fichiers/{id}` (5 en parallèle), puis au
    clic « Lancer la participation » `POST …/compute` (calcul Tadarida national). **Aucun `DELETE`** n'existe
    côté client : « Réinitialiser le dépôt » et « Annuler le dépôt » sont **100 % locaux**. Le nettoyage
    éventuel se fait **à la main sur le portail**. Piège : la participation naît **dès l'import** sur un site
    relié (`ServiceImport.creerParticipationSiPossible`), pas au dépôt.

## Décision arbitrée : dépôt réel sur données réelles

- **Données** : enregistreur **PR1997632**, carte SD réelle, **nuit du 05/07 (1623 wav, 11 Go)** non
  encore déposée : c'est une donnée réelle qui va à sa vraie place (le calcul devient l'usage nominal,
  pas une pollution), et enfin le vrai test de volume (#26/#27).
- **Carré cible : 130711** (le vrai carré où PR1997632 était posé, déjà relié).
- **Mode : IHM en ZIP** (c'est l'écran Lot qu'on recette : parallélisme, reprise).
- **Workspace : le workspace habituel (production)**, pas celui de recette.
- **Amenée de la nuit : par réactivation** (#1302) : la nuit existe en production comme passage archivé
  (audio purgé) ; le réimport depuis la carte SD la réactive (empreinte vérifiée). **Bonus : S4 recette
  donc aussi la réactivation en vrai.**
- **Délai Tadarida réel ≈ 2 h** (pas 24-48 h) : lancer le calcul **tôt**, dérouler le bloc A pendant qu'il
  tourne, et enchaîner **S5 le jour même** sur les vrais résultats.

!!! bug "Bug bloquant déjà tracé, à filmer en séance : #1514"
    Un passage **déposé peut régresser vers Vérifié** depuis l'écran de vérification (carte active, aucune
    garde de statut, `ServiceQualification` écrit `VERIFIE` en contournant le workflow, `deposeLe`
    conservé). La CLI, elle, refuse. À filmer dans le bloc A sur un passage marqué déposé manuellement.

**Sémantique de « lot » (tranchée)** : un lot = un passage = une nuit (`Lot` porte un unique
`idPassage`). « À jeter » ne retire pas un morceau d'un panier : il **rejette la nuit entière**.

## Le script (une case = un fait observable)

**Bloc A · Local, zéro écriture serveur (carré 640380, passages existants)**

*A1 · Préparer (passage n° 1, Vérifié / OK)*

1. Le stepper affiche 4 temps : « 1 · Préparer », « 2 · Générer les archives », « 3 · Téléverser »,
   « 4 · Marquer déposé ».
2. L'étape courante est ① sur un passage Vérifié.
3. La checklist de cohérence affiche une ligne par contrôle, même satisfait (✓ / ✗ / ⚠).
4. Un ⚠ (relevé climatique absent) n'empêche pas de préparer.
5. « Vérifier et préparer le lot » → statut « Prêt à déposer », étape courante ②.
6. Sur un passage au verdict « À jeter » : la préparation est refusée, message qui nomme le passage.
7. « Préparer » reste actif même avec un contrôle en ✗ (la fiche dit grisé, le code dit actif : S4-C05).

*A2 · Générer les archives*

8. La génération affiche une barre de progression déterminée.
9. Le libellé « Compression X/N » donne une estimation du temps restant.
10. Les actions sont neutralisées pendant la génération.
11. Le tableau des archives permet de choisir/réordonner ses colonnes.
12. Réglages ▸ Dépôt : le plafond d'archive (700 Mo par défaut) est réglable entre 50 et 700 Mo.
13. Le nouveau plafond s'applique à la génération suivante sans redémarrage.
14. Garde-fou disque : bandeau rouge et « Générer » désactivé **avant** le clic si l'espace est
    insuffisant.

*A3 · Téléverser (refus « site non relié »)*

15. « ☁ Téléverser sur Vigie-Chiro » est présent (l'application est connectée).
16. Le clic échoue proprement avec « Site non rattaché à VigieChiro… » (640380) : **rien** n'a été écrit.
17. Rien, avant ce clic, n'annonçait le dépôt impossible sur ce site (S4-C01 : le garde-fou arrive après
    la génération).
18. « 📂 Ouvrir le dossier (dépôt manuel) » ouvre le dossier `depot/`.
19. Ce bouton est grisé sans archives, avec une infobulle explicative.

*A4 · Marquer déposé, réinitialiser, régression*

20. Sans participation liée, le bouton est « ✅ Marquer déposé ».
21. Après « Marquer déposé » : statut « Déposé », et **toutes** les étapes du stepper sont franchies
    (S4-C03 : y compris « Lancer la participation », qui n'a pas eu lieu).
22. Sur le passage déposé, la carte « Sons & validation » se déverrouille.
23. « 🔄 Réinitialiser le dépôt » est visible, avec son infobulle.
24. Après réinitialisation : table vidée, statut « Prêt à déposer », message explicite.
25. **#1514 à filmer** : sur le passage déposé, la carte « Vérifier l'enregistrement » est-elle active ?
    Poser un nouveau verdict : le passage régresse-t-il en « Vérifié » en gardant sa date de dépôt ?
26. « ↩ Annuler le dépôt » (M-Passage) : visible sur un passage déposé, avec confirmation.
27. Passage archivé : les archives survivent-elles à la purge ?

**Bloc B · Dépôt réel (130711, nuit du 05/07, ZIP, calcul lancé)**

28. Réactivation : réimport de la nuit depuis la carte SD → empreinte vérifiée → passage réactivé.
29. Qualification : verdict OK posé.
30. Réglages ▸ Dépôt : plafond d'archive abaissé (~50 Mo) pour obtenir plusieurs archives.
31. La table de dépôt affiche une ligne par archive (en attente → en cours → déposé).
32. Cinq lignes sont « en cours » simultanément (parallélisme de 5).
33. Une barre de progression par archive reflète les octets envoyés.
34. Couper le réseau pendant le dépôt : échecs avec raison au survol, le bouton devient « ↻ Reprendre le
    dépôt ».
35. Reprendre ne renvoie que les archives manquantes.
36. Fermer puis rouvrir l'écran : la table se réhydrate.
37. Le passage ne devient « Déposé » que lorsque **toutes** les unités le sont.
38. « 🚀 Lancer la participation » : la carte « Traitement Vigie-Chiro » apparaît (« Analyse planifiée »).
39. « 🔄 Actualiser » relève l'état, **sans polling** automatique.
40. Hors connexion, « Actualiser » dit « Impossible de joindre Vigie-Chiro » **sans effacer** le dernier
    état connu.
41. Fermer/rouvrir l'application : le dernier état connu est réaffiché avec sa date, sans réseau.
42. **Noter l'identifiant de la participation** (nettoyage manuel éventuel + matériau de S5).

## Constats candidats (desk-check, à confirmer en séance)

| # | Axe | Constat |
|---|---|---|
| S4-C01 | E/F | Le dépôt exige un site relié mais **rien ne le dit avant le clic** : bouton actif, archives générées, échec à la fin. À griser dès l'ouverture ou à mettre dans la checklist |
| S4-C02 | C/D | **Aucun retour arrière serveur** : « Réinitialiser » / « Annuler » sont locaux ; la doc ne dit pas que les fichiers téléversés restent en ligne ; « réinitialiser puis re-téléverser » **duplique** (avec S4-C08) |
| S4-C03 | F | Le stepper affiche **toutes les étapes franchies** dès « Déposé », alors que « Lancer la participation » reste à faire (la doc martèle « déposer ≠ faire traiter ») |
| S4-C04 | F | Cette action critique n'est **jamais mise en avant** : bouton secondaire, libellés qui disent encore « Marquer le passage déposé » |
| S4-C05 | C | La fiche affirme que « Préparer » **reste grisé** tant qu'un contrôle échoue ; le code le laisse **actif** (relançable). C'est la fiche qui est fausse |
| S4-C06 | P/E | Le choix **ZIP / WAV** n'existe **ni en IHM ni en Réglages** : l'IHM impose le ZIP, WAV n'est atteignable qu'en CLI ; or ce choix détermine si l'audio reste récupérable côté serveur (→ #1515) |
| S4-C08 | E | En dépôt ZIP, la réconciliation serveur ne fait rien (elle ne lit que les WAV) : rien n'empêche de redéposer les mêmes archives |
| S4-C09 | D | **Aucune capture** ne montre le dépôt automatique, la reprise, la carte de traitement, ni Réglages ▸ Dépôt : le harnais assemble `lot` **sans connexion**, la moitié de l'écran documenté n'est jamais rendue |
| S4-C10 | C | La doc annonce « 24 à 48 h » et l'alerte « trop long » se déclenche à > 24 h, alors que le délai réel ≈ 2 h : **S4 mesure le délai réel** pour recaler doc et seuil |

## Parité CLI (desk-check)

Bonne couverture, deux surprises : la CLI **surpasse** l'IHM (`--wav` / `--archives`, `--forcer`, et
surtout `verifier-depot-vigiechiro`, sans équivalent IHM alors que c'est le seul moyen de confirmer qu'un
dépôt ZIP est bien arrivé). Écart de nommage : la commande `deposer` ne dépose rien (elle prépare et
marque déposé, sans réseau) : confusion avec `deposer-vigiechiro`.

## Prérequis avant de lancer

- Espace disque libéré (~22 Go pour le chemin ZIP) ; dépôt sur `main` ; `clean compile`.
- **Ne pas builder pendant qu'une instance tourne** (leçon S3 : une classe synthétique chargée à chaud
  peut disparaître → erreurs fantômes type `QualificationController$1`).

## Renvois

Cache d'état et « résultats à importer » → #1338 ; formulation « lot » → #1510 ; parité qualification →
#1512 ; régression de workflow → **#1514** ; confirmations génériques → #1499 ; point d'écoute non
modifiable → #1495 ; choix ZIP/WAV en Réglages → #1515.

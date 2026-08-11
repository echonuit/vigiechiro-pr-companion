# S1 · Premier contact

> **Écrans propriétaires** : accueil, modale Connexion, Mes sites, détail de carré, modale Point.
> **Features** : connexion, sites, synchronisation-sites. · **Jouée** le 2026-07-13.
> Ce script est à la fois la **checklist rejouable** de la session et le **relevé** de sa dernière passe.
> Retour à la [méthode](../index.md).

## Objectif

Premier lancement sur base vierge : découvrir l'accueil, se connecter à Vigie-Chiro (les quatre issues
distinctes), déclarer un site et un point d'écoute. On valide que le tout premier contact guide le geste.

## Environnement

- Lancement sur workspace vierge :
  `JAVA_TOOL_OPTIONS="-Dvigiechiro.workspace=<workspace-recette>" ./mvnw javafx:run`.
- Compte Vigie-Chiro de test ; un carré plateforme synchronisé + un carré créé à la main (640380).

## Le script (une case = un fait observable)

**Étape 1 · Premier lancement (workspace vierge)**

1. L'accueil affiche le hero « Deux entrées… » et ses 2 sections (Collecte & passages / Espèces &
   biodiversité). Les cartes sont **contribuées par les features** : ne pas figer leur liste ici, mais
   vérifier que chacune de celles qui s'affichent porte un intitulé, une destination annoncée, et
   **ouvre bien ce qu'elle annonce**.
2. Le bandeau de compteurs est masqué sans donnée.
3. Le fil d'Ariane, la recherche (Ctrl+F) et le menu principal (☰) sont présents.

**Étape 2 · Connexion (modale)**

4. La modale déroule 3 étapes (Ouvrir Vigie-Chiro, Copier le marque-page, coller le token).
5. Token vide : message d'information.
6. Token bidon : 401 « Token invalide ou expiré… ».
7. Réseau coupé : « Vigie-Chiro est injoignable… le jeton n'est peut-être pas en cause ».
8. Succès : bandeau vert « référentiel à jour : N taxons, N sites », badge vert identité + rôle.
   Avec **un seul** site, le bandeau écrit « 1 site » et non « 1 sites » (#1373).
9. **Sans quitter l'accueil**, fermer la modale : le bandeau de compteurs, masqué à la case 2, est
   maintenant **visible** et annonce les sites rapatriés. On n'a navigué nulle part entre-temps.
   ⚠️ C'est le défaut #1376 : avant, il fallait un aller-retour de navigation pour que les compteurs
   bougent. Une case qui navigue d'abord ne verrait rien.
10. Menu ☰ → « Restaurer une sauvegarde », depuis l'accueil : après la restauration, les quatre
    compteurs reflètent la base restaurée, toujours **sans** avoir quitté l'accueil. Le retour à
    l'accueil ne suffisait pas à les rafraîchir, l'écran courant ne changeant pas.
11. Déconnexion : confirmation, badge repasse gris, l'entrée du menu principal (☰) redevient « Se connecter… ».

**Étape 3 · Mes sites**

12. État vide : icône 🌐, « + Ajouter mon premier site de suivi », hint-box.
13. Création : carré 6 chiffres, « Créer » grisé tant qu'invalide, le champ rougit.
14. Carte de site complète : nom, points, passages, badge fraîcheur, badges « Enregistré / Verrouillé
    sur Vigie-Chiro », chevron ›.
15. Navigation clavier (Tab / Entrée / Espace) sur les cartes.
16. « Récupérer depuis Vigie-Chiro » est présent, et masqué hors connexion.
17. Voile d'occupation au chargement.

**Étape 4 · Détail de carré**

18. Le bandeau affiche carré, département, protocole, créé le, dernière nuit, passages.
19. Gardes des boutons : « Ouvrir sur Vigie-Chiro » grisé si non relié ; « Supprimer » grisé si des
    passages existent ; « Importer une nuit » masqué si la feature est off.
20. Points d'écoute : badges GPS ✓/⚠, distance au plus proche, repli sans point.
21. Tableau des passages (7 colonnes dont « Déposé le »), placeholder, double-clic vers le passage.
22. Le dialog « Modifier le site » s'ouvre.

**Étape 5 · Modale Point**

23. Création vs édition : titre et bouton dynamiques.
24. Garde de validité : code « 1 lettre + chiffres », GPS décimal/DMS synchronisés avec la carte-outil.
25. « Annuler » ne touche rien.

**Étape 6 · Ce que la stabilisation a changé au premier contact** (#3424)

26. La modale de connexion s'ouvre **sans saut** : la saisie est en place, rien ne se replace après
    coup. #1373
27. Pendant la récupération qui suit « Se connecter », **rien ne se redimensionne** : le contenu ne sort
    pas avant que le bandeau d'état ait pris sa place.
28. Le menu ☰ ne porte **plus** d'entrée « fiche espèce » : la source des fiches vit dans
    **Réglages ▸ Général**, où [S7](s7-reglages.md) la déroule. #3433
29. Le menu ☰ porte toujours ses autres entrées, et chacune ouvre ce qu'elle annonce.

> Le point 27 est le garde-fou du 26 : retirer un doublon est juste, en emporter un voisin ne l'est pas.
> Il coûte quelques secondes et couvre la seule façon dont ce retrait pouvait mal tourner.
>
> Les points 24 et 25 sont **perceptifs** : aucun test ne voit un contenu qui se replace, il voit un
> contenu correct une fois posé. C'est précisément ce qui les met ici plutôt qu'en TestFX.

## Verdict par axe (dernière passe)

| Écran | C | E | F | R | P | D |
|---|---|---|---|---|---|---|
| Accueil | remarque (#1381) | remarque (#1376) | remarque (#1375) | OK | s.o. | remarque (#1381) |
| Modale Connexion | OK | OK | remarque (#1373, #1374) | OK | OK | remarque (#1382) |
| Mes sites | remarque (#1381) | OK | remarque (#1377) | OK | remarque (#1383) | remarque (#1381) |
| Détail de carré | OK | OK | remarque (#1378, #1379, #1380) | OK | remarque (#1383) | remarque (#1381) |
| Modale Point | OK | OK | remarque (#1374) | OK | remarque (#1383) | OK |

Aucun cas de perte de données (b) ni d'impasse (c) : les constats relèvent de (a) doc contredite et
(e) friction sur le parcours nominal.

## Issues produites (11)

#1373 (bandeaux modale Connexion + « 1 site »), #1374 (troncatures modales Connexion/Point), #1375
(carte Audit → ☰), #1376 (compteurs d'accueil vivants), #1377 (carte de site : repli des codes + badges
+ points utilisés), #1378 (« Voir sur la carte » : centrage + pile de navigation), #1379 (☰ près du
tableau + explication distance), #1380 (édition site verrouillé : « locale seulement »), #1381 (docs
accueil/prise-en-main/sites), #1382 (docs connexion : 4 états + capture), #1383 (parité CLI :
modifier-site, supprimer-site, modifier-point).

## Renvois et décisions

- « 0 passage · jamais utilisé » sur un carré ayant des participations plateforme → #1305 (nuancé
  là-bas).
- Connexion CLI par jeton ponctuel (`--token`) → choix documenté, confirmé volontaire.
- Effacement du jeton sur panne réseau → déjà instruit au chantier #1284.

## Notes de méthode

**Delta rejoué en ouverture de S2 (7/7)** : entre S1 et S2, plusieurs chantiers ont touché ces écrans.
La modale de site a remplacé les deux Dialog (#1454), un badge d'état Vigie-Chiro (#734) et la
confirmation STOC du carré (#733) sont apparus, le jeton n'est plus effacé sur panne réseau (#1369). Tous
re-vérifiés sans régression. **Leçon rejouable** : rouvrir un écran déjà recetté après un chantier qui l'a
touché, au delta.

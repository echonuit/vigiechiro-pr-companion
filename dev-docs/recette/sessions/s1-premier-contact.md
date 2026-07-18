# S1 · Premier contact

> **Écrans propriétaires** : accueil, modale Connexion, Mes sites, détail de carré, modale Point.
> **Features** : connexion, sites, synchronisation-sites. · **Jouée** le 2026-07-13.
> Ce script est à la fois la **checklist rejouable** de la session et le **relevé** de sa dernière passe.
> Retour à la [méthode](../index.md).

## Objectif

Premier lancement sur base vierge : découvrir l'accueil, se connecter à VigieChiro (les quatre issues
distinctes), déclarer un site et un point d'écoute. On valide que le tout premier contact guide le geste.

## Environnement

- Lancement sur workspace vierge :
  `JAVA_TOOL_OPTIONS="-Dvigiechiro.workspace=<workspace-recette>" ./mvnw javafx:run`.
- Compte VigieChiro de test ; un carré plateforme synchronisé + un carré créé à la main (640380).

## Le script (une case = un fait observable)

**Étape 1 · Premier lancement (workspace vierge)**

1. L'accueil affiche le hero « Deux entrées… » et ses 2 sections (Collecte & passages / Espèces &
   biodiversité), 5 cartes (Mes sites, Carte & passages, Audit de cohérence, Espèces & observations,
   Sons & validation).
2. Le bandeau de compteurs est masqué sans donnée.
3. Le fil d'Ariane, la recherche (Ctrl+F) et le menu ☰ sont présents.

**Étape 2 · Connexion (modale)**

4. La modale déroule 3 étapes (Ouvrir VigieChiro, Copier le marque-page, coller le token).
5. Token vide : message d'information.
6. Token bidon : 401 « Token invalide ou expiré… ».
7. Réseau coupé : « VigieChiro est injoignable… le jeton n'est peut-être pas en cause ».
8. Succès : bandeau vert « référentiel à jour : N taxons, N sites », badge vert identité + rôle.
9. Déconnexion : confirmation, badge repasse gris, l'entrée du menu ☰ redevient « Se connecter… ».

**Étape 3 · Mes sites**

10. État vide : icône 🌐, « + Ajouter mon premier site de suivi », hint-box.
11. Création : carré 6 chiffres, « Créer » grisé tant qu'invalide, le champ rougit.
12. Carte de site complète : nom, points, passages, badge fraîcheur, badges « Enregistré / Verrouillé
    sur VigieChiro », chevron ›.
13. Navigation clavier (Tab / Entrée / Espace) sur les cartes.
14. « Récupérer depuis VigieChiro » est présent, et masqué hors connexion.
15. Voile d'occupation au chargement.

**Étape 4 · Détail de carré**

16. Le bandeau affiche carré, département, protocole, créé le, dernière nuit, passages.
17. Gardes des boutons : « Ouvrir sur Vigie-Chiro » grisé si non relié ; « Supprimer » grisé si des
    passages existent ; « Importer une nuit » masqué si la feature est off.
18. Points d'écoute : badges GPS ✓/⚠, distance au plus proche, repli sans point.
19. Tableau des passages (7 colonnes dont « Déposé le »), placeholder, double-clic vers le passage.
20. Le dialog « Modifier le site » s'ouvre.

**Étape 5 · Modale Point**

21. Création vs édition : titre et bouton dynamiques.
22. Garde de validité : code « 1 lettre + chiffres », GPS décimal/DMS synchronisés avec la carte-outil.
23. « Annuler » ne touche rien.

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
La modale de site a remplacé les deux Dialog (#1454), un badge d'état VigieChiro (#734) et la
confirmation STOC du carré (#733) sont apparus, le jeton n'est plus effacé sur panne réseau (#1369). Tous
re-vérifiés sans régression. **Leçon rejouable** : rouvrir un écran déjà recetté après un chantier qui l'a
touché, au delta.

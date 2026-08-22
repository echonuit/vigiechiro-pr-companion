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

- **S1-01** · L'accueil affiche le hero « Deux entrées… » et ses 2 sections (Collecte & passages / Espèces &
  biodiversité). Les cartes sont **contribuées par les features** : ne pas figer leur liste ici, mais
  vérifier que chacune de celles qui s'affichent porte un intitulé, une destination annoncée, et
  **ouvre bien ce qu'elle annonce**.
- **S1-02** · Le bandeau de compteurs est masqué sans donnée.
- **S1-03** · Le fil d'Ariane, la recherche (Ctrl+F) et le menu principal (☰) sont présents.

**Étape 2 · Connexion (modale)**

- **S1-04** · La modale déroule 3 étapes (Ouvrir Vigie-Chiro, Copier le marque-page, coller le token).
- **S1-05** · Token vide : message d'information.
- **S1-06** · Token bidon : 401 « Token invalide ou expiré… ».
- **S1-07** · Réseau coupé : « Vigie-Chiro est injoignable… le jeton n'est peut-être pas en cause ».
- **S1-08** · Succès : bandeau vert « référentiel à jour : N taxons, N sites », badge vert identité + rôle.
  Avec **un seul** site, le bandeau écrit « 1 site » et non « 1 sites » (#1373).
- **S1-09** · **Sans quitter l'accueil**, fermer la modale : le bandeau de compteurs, masqué à la case 2, est
  maintenant **visible** et annonce les sites rapatriés. On n'a navigué nulle part entre-temps.
  ⚠️ C'est le défaut #1376 : avant, il fallait un aller-retour de navigation pour que les compteurs
  bougent. Une case qui navigue d'abord ne verrait rien.
- **S1-10** · Menu ☰ → « Restaurer une sauvegarde », depuis l'accueil : après la restauration, les quatre
  compteurs reflètent la base restaurée, toujours **sans** avoir quitté l'accueil. Le retour à
  l'accueil ne suffisait pas à les rafraîchir, l'écran courant ne changeant pas.
- **S1-11** · Déconnexion : confirmation, badge repasse gris, l'entrée du menu principal (☰) redevient « Se connecter… ».

**Étape 3 · Mes sites**

- **S1-12** · État vide : icône 🌐, « + Ajouter mon premier site de suivi », hint-box.
- **S1-13** · Création : carré 6 chiffres, « Créer » grisé tant qu'invalide, le champ rougit.
- **S1-30** · Vérification, carré **libre** : saisir `999999`, cliquer « Vérifier sur Vigie-Chiro ». Un encart
  **vert** apparaît sous le champ : « Ce carré n'existe pas encore sur Vigie-Chiro : vous pouvez le déclarer
  ici. » Le bouton redevient cliquable aussitôt après.
- **S1-31** · Vérification, carré **déjà déclaré** : saisir le carré du site plateforme synchronisé (prérequis),
  cliquer. Un encart **ambre** nomme le site trouvé, protocole compris (« Vigiechiro - Point Fixe-<carré> »), et
  renvoie à « Mes sites », « Récupérer depuis Vigie-Chiro ». Il ne propose **pas** de déclarer quand même.
- **S1-32** · Le verdict ne survit pas à ce qu'il jugeait : après S1-31, changer un chiffre du carré. L'encart
  **disparaît** (il portait sur l'ancien numéro). Recliquer le repose sur le nouveau.
- **S1-33** · Vérification **hors connexion** : se déconnecter (S1-11), rouvrir la déclaration, saisir six
  chiffres, cliquer. L'encart dit « Vérification impossible … Ce carré n'a donc PAS été vérifié » - jamais « il
  n'existe pas ». Le bouton reste offert, non grisé.
- **S1-34** · Récupération : après S1-31, cliquer « **Récupérer ce carré** ». La fenêtre se ferme, on
  **reste sur « Mes sites »**, le carré récupéré paraît dans la liste, et le bandeau de l'écran annonce
  « Carré <n°> récupéré depuis Vigie-Chiro : N point(s) d'écoute positionné(s) ». Les points portent
  leurs coordonnées, sans ressaisie - on le vérifie en ouvrant la fiche, ce qui est S1-35.

    ⚠️ Cette case décrivait « la fiche du carré s'ouvre » jusqu'à #4099 : la même modale avait deux
    issues selon qu'on déclarait ou qu'on récupérait, et rien ne justifiait la divergence. Le geste se
    termine désormais là où il a commencé. La planche a mis un chantier de retard sur le produit, et
    c'est le clip qui l'a montré (#4180).
- **S1-35** · Le carré récupéré est **rattaché** : sur sa fiche, le badge « Enregistré sur Vigie-Chiro » est
  présent, et « Ouvrir sur Vigie-Chiro » n'est plus grisé.
- **S1-36** · Pendant le verdict « il existe déjà », le bouton « **Créer** » est **fermé** ; son infobulle dit
  « Ce carré existe déjà sur Vigie-Chiro : récupérez-le plutôt que de le redéclarer ». Corriger un chiffre du
  numéro le rouvre.
- **S1-37** · *perceptif* · L'enchaînement « je récupère → la fenêtre se ferme → la fiche s'ouvre » paraît
  **naturel** : on comprend où l'on a atterri et pourquoi, sans relire le dialogue deux fois. Le film
  **établit d'abord qu'on est connecté** : l'entrée de menu porte l'identité (« Vigie-Chiro : pseudo
  (rôle) ») au lieu de « Se connecter à Vigie-Chiro… ». Un rapatriement suppose un jeton, et un clip
  qui commence au « + Nouveau site » demande de le croire sur parole.
- **S1-14** · Carte de site complète : nom, points, passages, badge fraîcheur, badges « Enregistré / Verrouillé
  sur Vigie-Chiro », chevron ›.
- **S1-15** · Navigation clavier (Tab / Entrée / Espace) sur les cartes.
- **S1-16** · « Récupérer depuis Vigie-Chiro » est présent, et **fermé hors connexion** : grisé, avec un
  motif qui nomme le geste manquant - « Connectez-vous depuis le menu principal, entrée "Se connecter à
  Vigie-Chiro…" ». Se connecter le rouvre **sans quitter l'écran**, et la récupération rend compte dans
  le bandeau, qui nomme ce qui a été rapatrié.

    ⚠️ Cette case disait **« masqué »**, et le produit ne le faisait ni dans un sens ni dans l'autre : le
    bouton restait offert, ouvrait son dialogue, ne rapatriait rien et ne disait pas pourquoi (#4194).
    En le corrigeant, il a fallu choisir entre les deux mots. C'est **grisé** qui l'emporte, parce que le
    dépôt l'a tranché : « désactivé, il documente ce qui manque (affordance #789) plutôt que de
    disparaître ». Un bouton qui disparaît n'apprend rien ; un bouton fermé qui dit pourquoi enseigne le
    geste. La case suit donc la doctrine, et non l'inverse.

    ⚠️ « Se connecter le rouvre sans quitter l'écran » était écrit ici **avant** d'être vrai. Le produit
    ne rouvrait rien : le motif conseillait de se connecter, on suivait ce conseil, on revenait, et le
    bouton restait grisé à répéter le même conseil (#4205). Il fallait quitter l'écran et y revenir.
    L'état de connexion n'était **observable** nulle part - on pouvait le demander, pas le suivre.
- **S1-17** · Voile d'occupation au chargement.

**Étape 4 · Détail de carré**

- **S1-18** · Le bandeau affiche carré, département, protocole, créé le, dernière nuit, passages.
- **S1-19** · Gardes des boutons : « Ouvrir sur Vigie-Chiro » grisé si non relié ; « Supprimer » grisé si des
  passages existent ; « Importer une nuit » masqué si la feature est off.
- **S1-20** · Points d'écoute : badges GPS ✓/⚠, distance au plus proche, repli sans point.
- **S1-21** · Tableau des passages (7 colonnes dont « Déposé le »), placeholder, double-clic vers le passage.
- **S1-22** · Le dialog « Modifier le site » s'ouvre.

**Étape 5 · Modale Point**

- **S1-23** · Création vs édition : titre et bouton dynamiques.
- **S1-24** · Garde de validité : code « 1 lettre + chiffres », GPS décimal/DMS synchronisés avec la carte-outil.
- **S1-25** · « Annuler » ne touche rien.

**Étape 6 · Ce que la stabilisation a changé au premier contact** (#3424)

- **S1-26** · *perceptif* · La modale de connexion s'ouvre **sans saut** : la saisie est en place, rien ne se
  replace après coup. #1373
- **S1-27** · *perceptif* · Pendant la récupération qui suit « Se connecter », **rien ne se redimensionne** : le
  contenu ne sort pas avant que le bandeau d'état ait pris sa place.
- **S1-28** · Le menu ☰ ne porte **plus** d'entrée « fiche espèce » : la source des fiches vit dans
  **Réglages ▸ Général**, où [S7](s7-reglages.md) la déroule. #3433
- **S1-29** · Le menu ☰ porte toujours ses autres entrées, et chacune ouvre ce qu'elle annonce.

> **S1-27** est le garde-fou de **S1-26** : retirer un doublon est juste, en emporter un voisin ne l'est pas.
> Il coûte quelques secondes et couvre la seule façon dont ce retrait pouvait mal tourner.
>
> **S1-26** et **S1-27** sont **perceptifs** : aucun test ne voit un contenu qui se replace, il voit un
> contenu correct une fois posé. C'est précisément ce qui les met ici plutôt qu'en TestFX.
>
> Cette phrase était vraie, elle était au bon endroit, et **aucune machine ne la lisait** : le garde de
> correspondance rangeait ces deux cas parmi ceux « qu'il reste à couvrir », c'est-à-dire dans la file
> des tests à écrire, alors qu'il n'y a rien à y écrire. La marque `*perceptif*` en tête de case le dit
> désormais à une machine (#3764) : elle leur donne leur propre bac, et fait rougir le garde si un test
> prétend les asserter.

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

# Ma saison

L'écran **Ma saison** répond à une seule question, celle qu'on se pose au milieu d'une saison de
terrain : **qu'est-ce qu'il me reste à faire ?** Le protocole Point Fixe demande **deux passages par
an et par point d'écoute**, dans des fenêtres calendaires. L'application connaît ces règles et les
vérifie déjà ; cet écran est ce qui les **restitue**, point par point.

On y accède depuis la carte **« Ma saison »** de l'accueil (section *Collecte & passages*).

![L'écran Ma saison : une ligne par point, l'état des deux passages en pastilles, et la colonne « reste à faire ».](../assets/captures/apercu-saison.png)

## Le tableau, une ligne par point

Chaque ligne est un **point suivi** de vos sites. Les colonnes disent, d'un coup d'œil, où en est ce
point pour la saison choisie :

| Colonne | Contenu |
|---|---|
| **Carré**, **Point** | l'identité du point (numéro de carré, code du point) |
| **Passage 1** | l'état du premier passage et sa date, ou « Non planifié » s'il manque |
| **Passage 2** | l'état du second passage et sa date, ou « Non planifié » s'il manque |
| **Reste à faire** | **la phrase d'action** à mener sur ce point, ou « rien » si le point est à jour |

Les **états et leurs couleurs sont ceux du reste de l'application** : l'écran ne crée pas un second
vocabulaire de statuts. Un passage **« Inexploitable »** (jugé inutilisable à la vérification) compte
comme un passage **à refaire**, et non comme un passage fait : c'est le cas où un décompte naïf induit
en erreur.

## « Reste à faire » : un état se décrit, une action se fait

La dernière colonne est le cœur de l'écran. Elle ne décrit pas un état, elle **formule l'action**
suivante pour ce point :

- **« Poser l'enregistreur avant le 30/09 »** quand un passage manque, avec l'échéance de sa fenêtre ;
- **« Téléverser la nuit du 22/06 »** (ou l'étape de traitement suivante) quand un passage existe mais
  n'est pas encore déposé ;
- **« Refaire le 1er passage »** quand un passage est inexploitable ;
- **« rien »** quand les deux passages sont faits et déposés.

## En-tête et signalement de fenêtre

L'**en-tête** récapitule la saison : nombre de points suivis, passages faits sur attendus, et
l'échéance de la fenêtre du second passage. Ce résumé et le tableau proviennent de la **même source** :
ils ne peuvent pas diverger.

Quand la **fenêtre du second passage** approche de sa fermeture, une ligne le **signale** (« la fenêtre
se referme dans N jours pour M points »). L'application **signale**, elle n'alerte pas : elle ne pose
pas de rappel et ne programme pas de sortie terrain.

## Choisir l'année

Un **sélecteur d'année** en tête d'écran permet de revenir sur les saisons antérieures, consultables en
lecture. Par défaut, l'écran affiche la **saison courante**.

## Ouvrir un point

Un **double-clic** sur une ligne ouvre le **passage concerné** ; s'il n'existe pas encore de passage
pour ce point, il ouvre le **carré** du point, pour en saisir un.

!!! note "Sans campagne pour l'instant"
    Une colonne **Campagne** (regrouper les points d'un même suivi) est prévue mais n'est pas encore
    livrée : l'écran fonctionne sans elle, point par point.

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

L'**en-tête** récapitule la saison en **ventilant** les passages attendus : combien sont faits,
combien sont à refaire, combien restent à réaliser. Les trois nombres somment exactement au nombre
de passages attendus, de sorte qu'aucun manquant ne se perd en route : une proportion seule
(« 5 sur 10 ») laisserait deviner où sont les cinq autres.

Les **nuits hors protocole** se comptent **à côté**, jamais dans le total. Elles ont bien eu lieu,
mais ce ne sont pas des passages attendus : les fondre dans les 10 referait l'erreur que la colonne
« Hors protocole » corrige dans le tableau. La mention n'apparaît que s'il y en a.

Suit l'échéance de la fenêtre du second passage. Ce résumé et le tableau proviennent de la **même
source** : ils ne peuvent pas diverger.

Quand la **fenêtre du second passage** approche de sa fermeture, une ligne le **signale** (« la fenêtre
se referme dans N jours pour M points »). L'application **signale**, elle n'alerte pas : elle ne pose
pas de rappel et ne programme pas de sortie terrain.

## Choisir l'année

Un **sélecteur d'année** en tête d'écran permet de revenir sur les saisons antérieures, consultables en
lecture. Par défaut, l'écran affiche la **saison courante**.

## Trouver un lieu, isoler ce qui reste

Sur une saison chargée (plusieurs carrés, plusieurs points par carré), la liste devient longue et deux
questions n'ont pas de réponse directe dans le tableau. Deux contrôles y répondent, à côté des
sélecteurs d'année et de campagne :

- **Chercher un lieu** : la saisie garde les lignes dont le **carré** ou le **code du point**
  correspond. La recherche ignore la casse et les accents. Vider le champ rend la saison entière.
- **Reste à faire** : la case ne garde que les points qui ne sont **pas à jour**, c'est-à-dire ceux dont
  la colonne « Reste à faire » porte une action. Décocher rend la saison entière.

!!! note "L'en-tête continue de compter toute la saison"
    Filtrer change ce que **le tableau montre**, pas ce qu'il y a **à faire**. Le résumé et le
    signalement de fenêtre restent calculés sur la saison entière : chercher un lieu ne fait pas
    disparaître les passages qui vous attendent ailleurs.

Les sélecteurs **Saison** et **Campagne** restent, eux, des listes déroulantes toujours visibles : une
saison *est* une année et une campagne, et les garder sous les yeux donne la lecture immédiate « je suis
sur telle saison ».

## Ouvrir un point

Un **double-clic** sur une ligne ouvre le **passage concerné** ; s'il n'existe pas encore de passage
pour ce point, il ouvre le **carré** du point, pour en saisir un.

## Ne voir qu'une campagne

Une **campagne** regroupe les nuits qui relèvent d'un même suivi. Le solde peut s'y restreindre pour
répondre à « où en est ma campagne ? » plutôt qu'à « où en est ma saison ? ».

Il s'agit d'un **filtre**, et non d'une colonne : une ligne du solde est un **point**, avec ses **deux**
passages, qui peuvent relever de campagnes différentes : une colonne aurait dû choisir laquelle
afficher. Le filtre, lui, retient un point dès qu'**au moins un** de ses deux passages appartient à la
campagne, et le montre **en entier** : le second passage reste visible même s'il n'en fait pas partie,
puisque c'est l'état complet du point qui dit ce qu'il reste à y faire.

La correspondance est **partielle et insensible à la casse** : taper `ens` retient « Suivi ENS ». Un
point dont aucune nuit n'est rattachée à une campagne n'est jamais retenu par un filtre de campagne.

Le sélecteur **Campagne**, à côté de celui de l'année, applique ce filtre. « Toutes les campagnes »
revient au solde entier. Le tableau **et** le résumé d'en-tête se restreignent ensemble : ils sont
calculés à partir du même solde, ils ne peuvent pas se contredire.

Le sélecteur n'apparaît que s'il y a une campagne à proposer. Tant que vous n'en avez créé aucune, la
barre reste telle quelle : un contrôle vide n'aide personne. Pour en créer une, voir
[Le passage](passage.md#gerer-les-campagnes).

Le même filtre existe en ligne de commande : `vigiechiro solde-saison --campagne ens`.

## Les nuits opportunistes n'y comptent pas

Une **participation opportuniste** est une nuit réalisée sur le **carré d'un autre observateur**. Elle
ne relève pas du protocole Point Fixe : le solde ne la compte donc **pas** dans les « deux passages
attendus » du point, et n'en tire aucun « reste à faire ».

Elle reste visible pour autant, dans une colonne à elle : **Hors protocole**, en pastille
« Opportuniste » suivie de sa date. Elle a sa propre colonne précisément pour ne pas occuper celle
d'un passage attendu : la voir en « Passage 1 » se lirait « le passage 1 est fait », alors que la
même ligne réclame encore de poser l'enregistreur. Les colonnes Passage 1 et Passage 2 restent donc
sur « Non planifié » tant que le passage du protocole manque réellement.

Un carré **entièrement** possédé par un tiers sort quant à lui du solde : y participer est une
occasion, pas une obligation de protocole.

Comment une nuit devient opportuniste : voir [Le passage](passage.md#participation-opportuniste).

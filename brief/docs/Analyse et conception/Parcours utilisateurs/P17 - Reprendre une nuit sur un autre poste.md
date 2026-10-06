# P17 - Reprendre une nuit sur un autre poste 🧳

[← Retour au sommaire des parcours](index.md) · **Section C - Après le dépôt & exploitation**

> **Persona principal** : Karim (confier une nuit à un collègue, puis reprendre son avis). Le trajet
> de Samuel, du poste de terrain au poste de bureau, n'est pas livré : voir plus bas.
> **Objectifs qualité visés** : facilité de remplacement, intégrité des annotations.
> **Issue** : #3848. **Maquette** : [M-Paquet](../Maquettes/M-Paquet.md).

Karim hésite sur une nuit et veut qu'un collègue la juge à son tour. Avant ce parcours, deux
mécanismes existaient, et ni l'un ni l'autre ne répondait :

| Ce qui existe | Ce que cela fait | Pourquoi cela ne convient pas |
|---|---|---|
| Sauvegarde et restauration | copie la base entière et l'audio | tout ou rien : restaurer chez un collègue écrase son propre travail |
| [P13 - Envoyer un sous-ensemble à un expert](P13%20-%20Envoyer%20un%20sous-ensemble%20a%20un%20expert.md) | une archive de séquences et un CSV filtré | conçu pour faire **écouter** ; rien ne se réimporte |

Le parcours livré se tient entre les deux : une nuit part dans un paquet, un collègue la juge sur son
poste, et son avis revient se ranger à côté de celui de Karim.

## Le parcours

Les quatre gestes sont dans le menu ☰ de la liste de l'écran de vérification
([M-Qualification](../Maquettes/M-Qualification.md)), en deux groupes : les deux qui font voyager le
paquet de la nuit, puis les deux qui font voyager l'avis.

1. Sur la nuit ouverte, Karim choisit « **Emporter cette nuit…** » et désigne où écrire le paquet.
   L'application annonce le nombre de séquences, le volume total et la part d'audio, et n'écrit
   qu'après son accord. Refuser ne laisse aucun fichier.
2. Le paquet est une archive ZIP. Elle porte un manifeste, les séquences de la sélection d'écoute de
   la nuit et les verdicts que Karim a déjà posés. Elle ne porte ni les enregistrements bruts, ni le
   reste de la nuit, ni les identifiants de la plateforme.
3. Sur son poste, le collègue choisit « **Ouvrir un paquet reçu…** ». L'application prévient que la
   sélection d'écoute de cette nuit sera remplacée par celle de l'expéditeur et que les verdicts posés
   ici seront perdus. Après accord, la sélection de Karim devient la sienne, figée : « Régénérer » est
   refusé, avec le motif. Le compte rendu donne le nombre de séquences à relire et le pseudo relevé, et
   la liste se recharge.
4. Il juge, puis choisit « **Renvoyer mon avis…** ». Le paquet d'avis ne contient aucune séquence,
   puisque Karim les a déjà : seulement le manifeste, avec les verdicts et le pseudo de celui qui
   signe.
5. Karim choisit « **Reprendre un avis reçu…** ». Chaque verdict du collègue se range dans la colonne
   « Avis relecteur » de la même ligne, suivi de son pseudo. Les verdicts de Karim ne bougent pas, et
   la liste se recharge.

L'avis d'un relecteur s'affiche, il ne vote pas : le verdict de la nuit continue de se dériver des
seuls verdicts de Karim. Une nuit ne garde qu'un avis de relecteur. Si un second arrive, l'application
nomme celui qui est déjà là, compte les verdicts qui seraient remplacés et attend un accord.

## Ce que le poste du relecteur doit déjà détenir

Ouvrir un paquet n'installe pas la nuit. Le service cherche sur le poste le carré, le point, l'année et
le numéro de passage que le manifeste désigne, puis rattache chaque séquence du paquet à celle que le
poste détient sous le même nom de fichier. Si l'un des cinq manque, il refuse en disant lequel, avant
la première écriture. La reprise d'un avis suit la même règle sur le poste de Karim.

Le parcours suppose donc deux postes qui ont chacun importé la même nuit.

## En ligne de commande

Chaque geste a sa commande. Là où l'écran pose une question, la commande attend une option.

| Geste | Commande | Ce qu'elle demande pour écrire |
|---|---|---|
| Emporter cette nuit… | `emporter-nuit --passage <id> --vers <fichier>` | `--oui` ; sans lui, elle annonce le volume et s'arrête |
| Ouvrir un paquet reçu… | `ouvrir-paquet-recu --fichier <paquet>` | `--remplacer` si la nuit porte déjà une sélection ; sans lui, elle refuse en comptant les séquences et celles déjà jugées |
| Renvoyer mon avis… | `renvoyer-avis --passage <id> --vers <fichier>` | une identité connectée |
| Reprendre un avis reçu… | `reprendre-avis --fichier <avis>` | `--remplacer` si un avis est déjà rangé |

## Les trois questions, et leurs réponses

Ce parcours s'est ouvert sur trois questions. Elles sont tranchées par
l'[ADR 4517](https://companion-dev.echonuit.fr/decisions/4517-un-avis-de-relecteur-se-range-a-cote/),
amendée par
l'[ADR 4627](https://companion-dev.echonuit.fr/decisions/4627-le-paquet-fige-la-selection-d-ecoute/).

**Copie ou transfert : la copie signée.** La nuit reste chez l'expéditeur. Le relecteur juge sur son
exemplaire, et son avis revient signé de son pseudo, rangé à côté du premier et jamais fondu dedans.
Deux avis divergents ne sont pas une anomalie à réduire.

**Ce que le paquet contient : les séquences de la sélection, pas les bruts.** La première réponse
était « toutes les séquences transformées », pour laisser le relecteur tirer son propre échantillon.
Une arithmétique l'a défaite : deux tirages de trente séquences dans une nuit de cinq cents n'en ont
que deux en commun en espérance, et deux avis sur des échantillons disjoints ne se comparent pas. Le
paquet emporte donc la sélection, et la fige. Les identifiants de la plateforme ne voyagent pas : le
relecteur juge, l'expéditeur publie.

**Le retour : le même format, sans les séquences, et l'avis se range.** Il ne fusionne rien.

## Ce qui n'est pas livré

**Le passage du terrain au bureau.** Samuel récupère ses cartes sur un portable, dans une camionnette,
et qualifie au bureau. Ce trajet demande que le paquet apporte une nuit que le poste d'arrivée ne
connaît pas, et le service la refuse. Le porteur a sorti ce scénario du chantier le 6 octobre 2026 ;
il est consigné dans l'issue de cadrage #6043, qui dira s'il devient une cible.

**Le pseudo relevé à l'ouverture n'est pas conservé** (#4703). L'ouverture exige une identité et
l'affiche dans son compte rendu, mais elle ne l'enregistre pas. Le renvoi signe avec le profil
connecté au moment du renvoi. Sans connexion à ce moment-là, « Renvoyer mon avis… » le dit et n'écrit
rien, comme sa commande.

**Le nom du paquet n'est pas daté.** Le sélecteur propose `nuit.zip` pour l'emport et `avis.zip` pour
le retour, et l'utilisateur les renomme s'il le veut. Le premier lot du chantier annonçait un fichier
unique horodaté. La question reste posée au porteur.

## Sous l'écran

Le chemin d'une séquence peut être stocké relatif à sa session. L'emport le résout contre la racine de
cette session avant de peser et d'écrire, au lieu de le lire tel quel.

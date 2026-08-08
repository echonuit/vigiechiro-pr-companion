# Passe de vérification · ce que la stabilisation a livré tient-il debout ?

> **Écrans traversés** : accueil, connexion, importation, passage, rattachement, audit, réglages, plus
> la ligne de commande. · **Statut : à jouer.**
> Ce n'est pas une session propriétaire : c'est une **passe de recette de la recette**. Retour à la
> [méthode](../index.md).

## Objectif

La phase de stabilisation qui précède la campagne 2 (#3424) a livré **onze correctifs fonctionnels** en
trois jours. Chacun a ses tests, chacun est passé en CI, et plusieurs ont été vérifiés à la main au fil
de l'eau. Cette passe répond à une question que ni les tests ni ces vérifications ponctuelles ne
tranchent : **est-ce que l'application, prise dans son ensemble, marche toujours ?**

La distinction n'est pas rhétorique. Un correctif vérifié isolément le jour où il est livré peut être
défait le lendemain par le voisin, et personne ne le saura : le test du voisin est vert, celui du
correctif défait n'existe peut-être que sur le chemin qui a changé. Six des onze correctifs touchent
**le même parcours** - importer, rattacher, déposer, supprimer - et trois modifient ce que l'écran
**dit** plutôt que ce qu'il fait, ce qu'aucune garde statique ne compare à la réalité.

⚠️ **Cette passe ne remplace pas la campagne 2.** Elle en est la condition : jouer une campagne
d'acceptation sur une base dont on ne sait pas si elle tient produirait des constats qu'on ne saurait
pas attribuer.

## Ce que cette passe ne couvre pas, et pourquoi

Quatre livraisons de la période sont **délibérément absentes** du script :

| Livraison | Pourquoi elle n'est pas ici |
| --- | --- |
| Fidélité des aperçus (#3439, #3483) | Vérifiée par les gardes de captures, qui comparent des pixels. Un œil humain est moins fiable qu'elles sur ce point précis |
| Typographie embarquée (#3361, #3412) | Idem : quatre gardes comparent le rendu CI et poste. Ce que l'œil apporterait ici, c'est du doute, pas de la preuve |
| Réalignement des fiches (#1381, #1501) | Documentation. Sa garde est `DocumentationAJourTest`, qui compare les inventaires au code |
| Compilation ECJ, épinglage des actions | Chaîne d'intégration. Rien à voir dans l'application |

Les nommer évite la question « et ça, c'est vérifié ? » posée à la fin, quand il est trop tard pour
l'instruire.

## Environnement

Base **jetable**, jamais un workspace de travail : plusieurs cas suppriment définitivement.

```bash
env -u DISPLAY ./mvnw -q test-compile exec:java@generer-sd \
  -Dexec.args="recette/fixtures/spec /tmp/recette-sd"
```

⚠️ **Lancer l'application SANS `-Dvigiechiro.workspace`.** Cette propriété système gagne en silence sur
le réglage persisté, et c'est précisément ce qui a produit un faux positif lors de l'instruction de
#3459 : le mécanisme était sain, le harnais le contredisait. Un dispositif de vérification fait partie
de ce qu'il faut vérifier.

Il faut de quoi jouer : un **site avec au moins deux points**, une **nuit importée**, et une
**connexion à la plateforme** pour les cas D-01, E-02 et E-03. Les cas marqués 🔌 exigent d'être
connecté ; ceux marqués 🔒, de ne pas l'être.

## Le script

Une case = **un fait observable**. Si une case demande deux regards, elle est mal écrite : la couper.

### Étape 1 · Le menu et l'accueil ont bougé (#1375, #3433)

- [ ] **VS-01** · L'accueil ne porte **plus** de carte « Audit de cohérence ».
- [ ] **VS-02** · Le menu ☰ porte « Audit de cohérence », et il ouvre bien l'écran d'audit.
- [ ] **VS-03** · Le menu ☰ ne porte **plus** d'entrée « fiche espèce » (elle vit dans les Réglages).
- [ ] **VS-04** · Réglages : la source des fiches espèces s'y trouve toujours, et se modifie.

> VS-04 est le garde-fou de VS-03 : retirer un doublon est juste, retirer les **deux** copies ne l'est
> pas. Le cas coûte dix secondes et couvre la seule façon dont ce correctif pouvait mal tourner.

### Étape 2 · La modale de connexion ne saute plus (#1373)

- [ ] **VS-05** 🔒 · Ouvrir la modale de connexion : la saisie est en place, sans contenu qui se replace.
- [ ] **VS-06** · Cliquer « Se connecter » : pendant la récupération, **rien ne se redimensionne** - le
      contenu ne sort pas avant que le bandeau d'état ne prenne sa place.
- [ ] **VS-07** 🔌 · Le bandeau affiche « **1 site** » et non « 1 sites » quand il n'y en a qu'un.

### Étape 3 · Les dialogues portent l'habillage du produit (#1499, #3437, #3418)

- [ ] **VS-08** · Provoquer une confirmation (supprimer un passage, par exemple) : la fenêtre porte la
      **typographie et les couleurs du produit**, pas celles du système.
- [ ] **VS-09** · Cette même fenêtre n'affiche **aucune icône système** (le point d'interrogation bleu).
- [ ] **VS-10** · Son bouton par défaut se distingue visuellement de l'autre.

### Étape 4 · L'import dit ce qu'il a fait, et fait ce qu'il dit (#3448, #3471)

- [ ] **VS-11** · Après le choix de la carte SD, le sélecteur de point **ne dit pas** « sélectionner un
      site d'abord » alors qu'un site est renseigné.
- [ ] **VS-12** · Réglages : cocher « Conserver les originaux ». Importer une nuit. Le dossier de la
      nuit contient un sous-dossier `bruts/` **non vide**.
- [ ] **VS-13** · Décocher le réglage. Réimporter. Il n'y a **pas** de `bruts/`.
- [ ] **VS-14** 🔌 · Connecté, importer : le compte rendu annonce une participation créée **et** elle
      existe réellement sur la plateforme (le bouton « Voir la participation » l'ouvre).
- [ ] **VS-15** 🔒 · Déconnecté, importer : le compte rendu **ne prétend pas** avoir créé de
      participation.

> VS-12 et VS-13 forment une paire : le réglage devait être lu **au moment de servir**, pas mémorisé au
> démarrage. Vérifier une seule des deux positions laisserait passer un réglage figé sur la bonne
> valeur par hasard. Et changer le réglage **sans redémarrer** l'application est le cœur du cas.
>
> ⚠️ VS-14 exige de **regarder la plateforme**, pas seulement le message. C'est tout le sujet de
> #3448 : l'écran annonçait une création qui n'avait pas eu lieu.

### Étape 5 · Le rattachement rend compte des deux moitiés (#3449)

- [ ] **VS-16** · Rattacher une nuit dont des séquences doivent être renommées : le compte rendu
      **chiffre les séquences renommées**.
- [ ] **VS-17** 🔒 · Faire échouer l'envoi (se déconnecter avant de valider) : le compte rendu dit
      **à la fois** le renommage réussi **et** l'échec de l'envoi.

> VS-17 est le cas qui a motivé le correctif : une opération en deux temps dont la seconde échoue
> annonçait un échec sec, et l'utilisateur ne savait pas que la première avait abouti - donc s'il
> pouvait relancer sans risque.

### Étape 6 · Le dépôt refuse plutôt que d'amputer (#3451, #3406)

- [ ] **VS-18** 🔌 · Déposer une nuit **sans heure de début ou de fin** : le dépôt est **refusé**, avec
      un motif lisible. Rien n'est envoyé.
- [ ] **VS-19** 🔌 · Déposer une nuit complète : la date et l'heure visibles sur la plateforme
      correspondent à l'heure **du site**, pas à celle du poste.

> VS-19 se lit sur la plateforme, jamais dans l'application : une conversion qui se relit avec la même
> zone est juste sous tout fuseau, y compris quand la donnée déposée est fausse. C'est exactement le
> faux vert que #3450 a levé.

### Étape 7 · Supprimer dit ce qu'il ne fait pas (#3482)

- [ ] **VS-20** · Supprimer un passage : la confirmation dit que les **fichiers audio restent sur le
      disque**, et affiche **où**.
- [ ] **VS-21** · Confirmer, puis vérifier sur le disque : le dossier de la nuit est **toujours là**.
- [ ] **VS-22** · Ouvrir l'audit et relancer : un constat « **Dossier sans session** » désigne ce
      dossier.

### Étape 8 · L'audit ramasse, en chiffrant (#3482)

- [ ] **VS-23** · Le bouton porte le **nombre** de dossiers concernés (« Retirer 2 dossier(s)… »).
- [ ] **VS-24** · Cliquer : la fenêtre **chiffre la place regagnée** et **nomme** les dossiers.
- [ ] **VS-25** · Répondre **non** : les dossiers sont **toujours** sur le disque.
- [ ] **VS-26** · Recommencer et répondre **oui** : les dossiers ont disparu du disque.
- [ ] **VS-27** · Le bandeau annonce le nombre retiré **et** la place regagnée.
- [ ] **VS-28** · Le bouton est redevenu **désactivé**, et son infobulle dit qu'il n'y a rien à retirer.

> VS-25 est le cas de sécurité. Un ménage qui s'exécute quand on a répondu non détruit des
> enregistrements irremplaçables : c'est la seule case de ce script dont l'échec justifierait
> d'arrêter la campagne sur-le-champ.

### Étape 9 · L'audit reste lisible sur un vrai workspace (#3490)

- [ ] **VS-29** · Provoquer des préfixes non conformes sur une nuit entière (renommer des fichiers à la
      main). Relancer l'audit : il produit **un seul constat pour ce passage**, pas un par fichier.
- [ ] **VS-30** · Ce constat **chiffre** les fichiers concernés, donne le **préfixe attendu**, et cite
      **un exemple**.

### Étape 10 · La ligne de commande a rattrapé l'écran (#1383)

```bash
./vigiechiro modifier-site --aide
./vigiechiro supprimer-site --aide
./vigiechiro modifier-point --aide
```

- [ ] **VS-31** · Les trois commandes existent et décrivent ce qu'elles font.
- [ ] **VS-32** · `supprimer-site` sur un site **dont un point porte un passage** : la commande
      **refuse d'emblée**, nomme les points bloquants, et sort en code **2**.
- [ ] **VS-33** · Après ce refus, le site est **intact** en base.

> Le code 2 n'est pas cosmétique : il distingue un **refus métier** (état intact) d'un échec inattendu
> (état incertain). Un script qui enchaîne s'arrête dessus sans avoir rien détruit.

## Ce qu'on fait des résultats

Une case rouge **n'ouvre pas une issue tout de suite**. Elle se qualifie d'abord :

1. **le correctif est défait** - régression franche, à traiter avant la campagne 2 ;
2. **le correctif tient, mais un voisin a bougé** - le plus instructif : c'est un chaînon que personne
   ne gardait ;
3. **le cas était mal écrit** - il demandait deux regards, ou supposait un état qu'on n'avait pas.

Le troisième verdict est fréquent et ne vaut pas aveu : il corrige le script, pas le produit. Les deux
premiers ouvrent une issue qui **cite la case**, pour que le prochain qui la rejoue sache ce qu'elle a
déjà attrapé.

⚠️ **Une case rouge à l'étape 7 ou 8 se traite avant tout le reste.** Ce sont les seules qui touchent à
la destruction de données.

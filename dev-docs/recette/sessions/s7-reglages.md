# S7 · Réglages, interrupteurs OFF, états dégradés

> **Écrans propriétaires** : Réglages (tous les onglets) et le **chrome** de l'application. · **Statut :
> à jouer.**
> Session propriétaire au sens de la [méthode](../index.md) : c'est **ici** que l'écran est déroulé en
> entier, et nulle part ailleurs.

## Objectif

Un réglage se juge sur **une** question, et ce n'est pas « la case est-elle cochée ? » :

> **Quand prend-il effet, et l'a-t-il vraiment pris ?**

L'écran porte les trois réponses possibles, et elles ne se vérifient pas de la même façon :

| Quand | Exemples | Ce qu'il faut faire pour le voir |
| --- | --- | --- |
| **immédiatement** | source des fiches espèces, palette daltonienne | changer, puis regarder l'écran concerné |
| **au moment de s'en servir** | conserver les originaux (#3471) | changer **sans redémarrer**, puis déclencher l'action |
| **au prochain démarrage** | fonctionnalités, emplacements | changer, **quitter, relancer**, puis regarder |

La deuxième ligne est celle qui a produit un défaut réel : le réglage était lu au démarrage et mémorisé,
donc le changer en cours de route ne changeait rien. **Un réglage vérifié dans une seule position, ou
après redémarrage, laisse passer exactement ce défaut-là** - il aurait suffi que la valeur soit bonne
par hasard.

## L'écran ne se déroule pas depuis cette page

**Les onglets sont contribués par les features**, et n'apparaissent que s'ils ont quelque chose à
montrer (`EcranReglagesController.estAffichable`). La liste **change** quand une feature est ajoutée ou
désactivée, et c'est voulu.

Ce script ne fige donc **pas** le nombre d'onglets : il déroule ceux qui sont **présents**. Une case qui
verrouillerait la liste attendue deviendrait fausse à la première feature ajoutée, et serait mise à jour
sans qu'on regarde ce qu'elle vérifie. Le comptage est le travail de `DocumentationAJourTest`, qui sait
lire le code ; le vôtre est de regarder ce que chaque onglet **fait**.

Au moment d'écrire ce script, six onglets se présentent : **Général**, **Emplacements**,
**Fonctionnalités**, **Import**, **Audio**, **Dépôt**. Si vous en voyez un de plus, il n'a pas de cases
ici : c'est un **constat**, pas un échec, et il ouvre une issue pour compléter cette session.

## Environnement

Base **jetable** : la session bascule des fonctionnalités et déplace le dossier de travail.

```bash
env -u DISPLAY ./mvnw -q test-compile exec:java@generer-sd \
  -Dexec.args="recette/fixtures/spec /tmp/recette-sd"
```

Une carte **`sd-nominale`** suffit : elle ne sert qu'à l'étape 4, pour observer l'effet d'un réglage
d'import. Il faut aussi une **nuit déjà importée** pour l'étape 3 (on n'écoute pas sans séquences).

**Lancer l'application sans AUCUNE propriété `-Dvigiechiro.*`.** Ce n'est pas une précaution de
forme : sur ce chemin précis, une propriété système **gagne en silence** sur le réglage persisté. C'est
vrai de l'emplacement de travail (elle a produit un faux positif à l'instruction de #3459) **et** des
fonctionnalités, dont la résolution consulte `vigiechiro.feature.<id>` **avant** le réglage enregistré.
Une session jouée avec une propriété posée vérifierait la propriété, pas l'écran.

## Le script

Une case = **un fait observable**. Les étapes 1 à 5 se jouent **sans redémarrer** ; les étapes 6 et 7
exigent un redémarrage et le disent.

### Étape 1 · L'écran s'ouvre et se peuple

- [ ] **S7-01** · *geste: ouvrir-les-reglages* · Ouvrir les Réglages : l'écran affiche un bandeau d'onglets, pas un cadre vide.
- [ ] **S7-02** · *geste: ouvrir-les-reglages* · Chaque onglet visible porte un **titre** et, s'il en a une, son **icône**.
- [ ] **S7-03** · *geste: ouvrir-les-reglages* · Chaque réglage affiche son **libellé** et sa **description** : aucun n'est nu.

> S7-03 n'est pas cosmétique. Un commutateur sans description oblige à deviner ce qu'il fait, et c'est
> sur ce genre de devinette qu'un utilisateur coche « conserver les originaux » sans savoir qu'il double
> son occupation disque.

### Étape 2 · Général : un réglage à effet immédiat

- [ ] **S7-04** · *geste: basculer-la-source-des-fiches-especes* · Onglet **Général**, basculer « Fiches espèces sur Wikipédia (sinon GBIF) ».
- [ ] **S7-05** · *geste: basculer-la-source-des-fiches-especes* · **Sans redémarrer**, ouvrir une fiche d'espèce hors chiroptères : la source consultée
      est bien celle qu'on vient de choisir.
- [ ] **S7-06** · *geste: basculer-la-source-des-fiches-especes* · Rebasculer, rouvrir une fiche : la source a changé **dans l'autre sens**.

> S7-06 est le garde-fou de S7-05 : un réglage figé sur la bonne valeur passe le premier cas et rate le
> second. Les deux positions, toujours.

### Étape 3 · Audio : ce qui change l'écoute

- [ ] **S7-07** · *geste: regler-l-ecoute* · Activer « Lecture automatique à la sélection », ouvrir une nuit, sélectionner une
      séquence : la lecture **démarre seule**.
- [ ] **S7-08** · *geste: regler-l-ecoute* · Activer « Lecture en boucle » : la séquence en cours **se répète**.
- [ ] **S7-09** · *geste: regler-l-ecoute* · Activer « Spectrogramme adapté au daltonisme » : la palette du spectrogramme **change**.
- [ ] **S7-10** · *geste: inclure-le-mode-de-validation-a-l-export* · Activer « Inclure le mode de validation à l'export _Vu », exporter : le CSV porte une
      colonne « mode de validation ».
- [ ] **S7-11** · *geste: inclure-le-mode-de-validation-a-l-export* · Désactiver ce dernier, réexporter : la colonne **a disparu**.

> S7-10 et S7-11 se lisent dans le **fichier produit**, jamais à l'écran : c'est le seul endroit où ce
> réglage a un effet observable.

### Étape 4 · Import : le réglage lu au moment de s'en servir (#3471)

- [ ] **S7-12** · *geste: conserver-les-originaux-a-l-import* · Onglet **Import**, cocher « Conserver les originaux ». Importer `sd-nominale` : le
      dossier de la nuit contient un sous-dossier `bruts/` **non vide**.
- [ ] **S7-13** · *geste: conserver-les-originaux-a-l-import* · **Sans quitter l'application**, décocher le réglage. Réimporter : il n'y a **pas** de
      `bruts/`.
- [ ] **S7-14** · *geste: conserver-les-originaux-a-l-import* · Recocher, réimporter une troisième fois : `bruts/` est **de retour**.

> **Ne pas redémarrer entre S7-12 et S7-13.** C'est tout l'objet de #3471 : le réglage était lu une
> fois au démarrage et mémorisé, donc le changer en cours de session ne changeait rien. Un redémarrage
> masquerait exactement le défaut que ces trois cases existent pour attraper.
>
> S7-14 n'est pas un doublon : il vérifie que le retour à la première valeur **fonctionne aussi**, et
> non qu'on a seulement appris à lire une fois de plus.

### Étape 5 · Dépôt : une énumération et un entier

- [ ] **S7-15** · *geste: regler-la-forme-du-depot* · Onglet **Dépôt**, « Forme du dépôt » propose **deux** formes (archives ZIP, séquences
      WAV) et retient celle qu'on choisit.
- [ ] **S7-16** · *geste: regler-la-forme-du-depot* · « Taille maximale d'une archive (Mo) » accepte une valeur, et la **refuse** si elle
      est absurde (zéro, négative, texte) en le disant plutôt qu'en l'ignorant.
- [ ] **S7-17** · *geste: regler-la-forme-du-depot* · Rouvrir les Réglages : les deux valeurs choisies sont **toujours là**.

> S7-16 est le seul champ **libre** de l'écran, donc le seul endroit où une saisie invalide peut
> exister. Un champ qui avale une valeur absurde sans rien dire est le mode de panne à guetter ici.

### Étape 6 · Fonctionnalités : les interrupteurs, et ce qu'ils ne coupent pas

- [ ] **S7-18** · *geste: lire-les-interrupteurs-de-fonctionnalites* · Onglet **Fonctionnalités** : chaque fonctionnalité **désactivable** porte un
      interrupteur.
- [ ] **S7-18b** · *geste: lire-les-interrupteurs-de-fonctionnalites* · La liste se lit dans l'**ordre alphabétique des libellés affichés** (#3833) : rien ne
      paraît rangé au hasard. Un libellé accentué est à sa lettre - « Étang » entre « Analyse » et
      « Zone », jamais après le Z.
- [ ] **S7-19** · *geste: lire-les-interrupteurs-de-fonctionnalites* · Une fonctionnalité **du cœur** n'en porte **pas** : elle est présentée comme toujours
      active, sans commutateur à actionner.
- [ ] **S7-20** · *geste: desactiver-une-fonctionnalite* · Désactiver une fonctionnalité optionnelle : un message annonce que l'effet arrive **au
      prochain démarrage**.
- [ ] **S7-21** · *geste: desactiver-une-fonctionnalite* · *carton: l'application est quittée puis relancée* · Quitter, relancer : l'entrée correspondante a **disparu** du menu ☰ ou de l'écran qui
      la portait.
- [ ] **S7-22** · *geste: desactiver-une-fonctionnalite* · *carton: l'application est quittée puis relancée* · La réactiver, quitter, relancer : l'entrée est **revenue**, et l'écran fonctionne.

> S7-19 mérite d'être joué même s'il paraît trivial : une fonctionnalité socle dont d'autres dépendent
> ne doit pas offrir un interrupteur qui ne coupe rien. Un commutateur sans effet est pire qu'une
> absence de commutateur, parce qu'il se coche.
>
> S7-22 est la moitié qu'on saute. Vérifier qu'une désactivation retire l'entrée sans vérifier que la
> réactivation la ramène laisse la porte ouverte à un aller sans retour.

### Étape 7 · Emplacements : ce que le réglage fait, et ce qu'il ne fait pas

- [ ] **S7-23** · *geste: lire-les-emplacements* · Onglet **Emplacements** : le **dossier de travail** et la **base de données** sont
      affichés, chacun avec son emplacement courant et son défaut.
- [ ] **S7-38** · *geste: lire-les-emplacements* · Chaque chemin porte, **à côté de lui**, un bouton « Copier » ; le cliquer place ce
      chemin dans le presse-papier, et ce qui s'y trouve est **exactement** ce que l'écran affiche
      (le coller dans un éditeur pour le lire). Les deux rangées l'offrent (#3882).

    Le numéro sort de la suite : les cases de S7 vont jusqu'à **S7-37**, et les renuméroter
    rendrait faux les renvois que ce script porte déjà entre ses propres cases (S7-24 est cité par
    S7-27, par exemple).
- [ ] **S7-24** · *geste: changer-le-dossier-de-travail* · L'écran annonce que choisir un emplacement change **où l'application ira lire au
      prochain démarrage**, et **ne déplace pas** les données existantes.
- [ ] **S7-25** · *geste: changer-le-dossier-de-travail* · Choisir un nouveau dossier de travail : un **avis de redémarrage** apparaît, et il est
      difficile à manquer.
- [ ] **S7-26** · *geste: changer-le-dossier-de-travail* · *carton: l'application est quittée puis relancée* · Quitter, relancer, rouvrir les Réglages : l'emplacement affiché est bien le **nouveau**.
- [ ] **S7-27** · *hors-portée: un fait de DISQUE : ce que le cas observe est que les données sont toujours dans l'ancien dossier, et l'écran ne le montre pas* · Vérifier l'ancien dossier sur le disque : les données y sont **toujours**, conformément
      à ce qu'annonçait S7-24.

> S7-25 et S7-27 portent le constat qui a motivé #3459 : un utilisateur au disque saturé cherche à
> **déplacer**, là où le réglage **repointe**. Les deux cases vérifient que l'écran ne laisse pas croire
> autre chose. Le déplacement effectif des fichiers est un besoin distinct (#3486), pas un défaut de
> cet écran.

### Étape 8 · Le chrome : les fenêtres que l'application ouvre

- [ ] **S7-28** · *geste: l-habillage-des-fenetres-de-l-application* · Provoquer une confirmation (supprimer un passage, par exemple) : la fenêtre porte la
      **typographie et les couleurs du produit**, pas celles du système.
- [ ] **S7-29** · *geste: l-habillage-des-fenetres-de-l-application* · Elle n'affiche **aucune icône système** (le point d'interrogation ou l'avertissement
      du bureau).
- [ ] **S7-30** · *geste: l-habillage-des-fenetres-de-l-application* · Son bouton par défaut se **distingue** visuellement de l'autre.
- [ ] **S7-31** · *geste: l-habillage-des-fenetres-de-l-application* · Provoquer un message d'erreur et un message d'information : ils portent le même
      habillage, et leur **sévérité** se lit.

> Le chrome appartient à cette session parce qu'il n'appartient à aucun écran : il apparaît **par-dessus**
> tous. Le vérifier ailleurs reviendrait à le vérifier partout, c'est-à-dire nulle part.

### Étape 9 · États dégradés

- [ ] **S7-32** · *geste: les-reglages-hors-connexion* · 🔒 Hors connexion, ouvrir les Réglages : l'écran s'ouvre, et ce qui dépend du réseau
      le **dit** plutôt que de rester muet ou de faire attendre.
- [ ] **S7-33** · *geste: les-reglages-sans-dossier-de-travail* · *carton: le dossier de travail est rendu inaccessible hors de l'application* · Rendre le dossier de travail inaccessible (le renommer hors de l'application), puis
      ouvrir les Réglages : l'écran **dit** que l'emplacement est introuvable, et propose d'en choisir
      un autre.

> S7-33 est le cas qu'aucun test automatisé ne joue et que la vraie vie produit : un disque externe
> débranché, un dossier synchronisé encore en cours. Si l'écran ne dit rien, l'utilisateur conclut que
> l'application est cassée.

### Étape 10 · Sauvegarder et restaurer

Le menu ☰ appartient au chrome, donc à cette session. Ces cases n'y étaient pas : la capacité était
livrée et testée, sans qu'aucun script ne dise comment la vérifier à la main.

**Fixture** : une sauvegarde complète prise sur une base portant **au moins deux nuits** (la carte SD
de recette importée deux fois suffit), et un **support amovible** pour les deux derniers cas - une clé
USB dont on remplit l'espace libre avec des fichiers quelconques jusqu'au seuil voulu.

- [ ] **S7-34** · *geste: sauvegarder-et-restaurer* · ☰ > « Sauvegarde complète (base + audio)… » : la demande de confirmation annonce le
      **volume** et rappelle que l'archive porte les localisations **en clair**, avant de copier.
- [ ] **S7-35** · *geste: sauvegarder-et-restaurer* · *carton: un dossier de nuit est renommé hors de l'application* · Renommer un dossier de nuit hors de l'application, puis restaurer cette sauvegarde
      complète : le compte rendu **nomme la nuit déplacée** et dit où elle a atterri. Rouvrir l'écoute
      de cette nuit : l'audio est retrouvé.
- [ ] **S7-36** · *hors-portée: un support dont l'espace libre est mesurable et insuffisant : le banc ne sait pas fabriquer un disque presque plein* · Pointer le dossier de travail vers un support dont l'espace libre est **inférieur au
      total** des nuits de la sauvegarde mais **supérieur à la plus grosse**, puis restaurer : la
      restauration aboutit, et le compte rendu dit que les nuits ont été remises **une nuit à la
      fois**.
- [ ] **S7-37** · *hors-portée: un support rempli jusqu'à laisser moins que la plus grosse nuit, même raison* · Remplir le support jusqu'à laisser **moins que la plus grosse nuit**, puis
      restaurer : refus qui dit **combien libérer et où**. Vérifier ensuite que l'audio local et la
      base sont **ceux d'avant** : rien n'a été touché.

> S7-36 et S7-37 sont les deux faces d'un même arbitrage (ADR 3563) : on dégrade la garantie plutôt
> que l'usage, et on ne refuse que lorsque même une nuit ne tient pas. Une recette qui ne jouerait que
> le refus laisserait croire que l'application est rigide ; une qui ne jouerait que le régime dégradé
> laisserait croire qu'elle accepte tout.

## Ce qu'on fait des résultats

Une case rouge se qualifie avant d'ouvrir une issue :

1. **le réglage ne prend pas effet** - c'est le défaut, et sa gravité se lit dans la colonne « quand »
   du tableau d'objectif : un réglage à effet immédiat qui ne fait rien se remarque, un réglage lu au
   moment de servir qui ne fait rien passe pour un caprice de l'utilisateur ;
2. **il prend effet, mais l'écran annonce autre chose** - défaut de ce que l'écran *fait comprendre*,
   et non de ce qu'il fait. #3459 en est l'exemple : le mécanisme était sain ;
3. **le cas était mal écrit** - il supposait un onglet, une fonctionnalité ou une donnée qu'on n'avait
   pas.

**Avant d'ouvrir une issue sur un réglage qui « ne marche pas », vérifier qu'aucune propriété
système ne le surcharge.** C'est la première hypothèse à écarter sur cet écran, et celle qui a déjà
coûté une instruction complète.

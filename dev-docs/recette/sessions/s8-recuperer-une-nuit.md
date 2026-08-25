# S8 · Récupérer une nuit déposée

> **Écran propriétaire** : aucun en propre, la session traverse **Connexion**, **Mes sites**, **Toutes
> mes nuits** et la **fiche passage**.
> **Features** : reconstruction-passages, passage, sites, connexion, lot, multisite, saison.
> · **Statut : à jouer.**
> Retour à la [méthode](../index.md).

## Objectif

Rejouer le parcours **P12** de bout en bout sur un vrai compte, tel que l'EPIC #2554 l'a refait : une
base neuve se connecte, récupère ses nuits **avec leur contenu**, et une carte SD leur rend leur audio.

Ce que cette session couvre et qu'aucun test ne peut couvrir : la **durée** réellement ressentie, la
lisibilité des barres, le comportement du bouton **Annuler** sur une opération longue, et le fait qu'une
vraie carte SD soit reconnue.

## Environnement

- **Compte réel**, carré **130711**, point **Z41** (4 participations, dont 2 analysées et 2 non).
- **Base neuve** (workspace de recette, jamais celui de production).
- **Carte SD réelle** de l'enregistreur **PR1997632**.
- ⚠️ Session **en lecture seule** côté plateforme : aucun dépôt, aucune écriture. Si un geste se met à
  écrire, c'est un constat en soi.

## Le script (une case = un fait observable)

**Étape 1 · La connexion devient une opération longue**

- **S8-01** · *geste: connexion-longue* · Coller le jeton : l'avancement de la récupération s'affiche.
- **S8-02** · *geste: connexion-longue* · La barre **avance** et son libellé nomme la nuit en cours (« Nuits k / N »), il ne reste pas figé.
- **S8-03** · *geste: connexion-longue* · Une **estimation du temps restant** apparaît une fois l'avancement mesurable.
- **S8-04** · *geste: annuler-pendant-la-recuperation* · Le bouton **Annuler** est atteignable pendant toute l'opération (pas masqué, pas hors fenêtre).
- **S8-05** · *geste: connexion-longue* · L'avancement paraît **dans** la modale de connexion, sans seconde fenêtre, et « Fermer » y est grisé
  tant que l'opération tourne (#2642).
- **S8-06** · *geste: connexion-longue* · À la fin, la modale de connexion annonce l'identité **et** le résumé de ce qui a été récupéré.

**Étape 2 · Ce que le compte rendu affirme**

- **S8-07** · *geste: compte-rendu-de-recuperation* · Le résumé distingue les nuits **récupérées** de celles **en attente d'analyse Vigie-Chiro**.
- **S8-08** · *hors-portée: couper le réseau en cours de synchronisation, qu'un banc ne sait pas provoquer sans mentir sur la cause* · Couper le réseau avant une synchronisation, la relancer : le résumé dit « **non récupérée(s), à
  réessayer** », et **jamais** « en attente d'analyse ». (C'est le défaut P1-C du chantier.)
- **S8-09** · *geste: compte-rendu-de-recuperation* · Les nombres annoncés correspondent à ce que la table montre réellement.

**Étape 3 · Annuler, et reprendre**

- **S8-10** · *geste: annuler-pendant-la-recuperation* · Lancer « Synchroniser depuis VigieChiro » depuis **Mes sites**, puis **Annuler** en cours.
- **S8-11** · *geste: annuler-pendant-la-recuperation* · Le message dit que ce qui a été récupéré **est conservé** et que la suivante reprendra le reste.
- **S8-12** · *geste: annuler-pendant-la-recuperation* · La table ne montre **aucune nuit à moitié faite** (une nuit a ses observations, ou n'en a aucune).
- **S8-13** · *geste: annuler-pendant-la-recuperation* · Relancer la synchronisation : elle **reprend** le travail restant, sans recréer ce qui existe.

**Étape 4 · La fiche d'une nuit récupérée**

- **S8-14** · *geste: fiche-de-la-nuit-recuperee* · La nuit apparaît avec ses **observations** et son décompte de séquences.
- **S8-15** · *geste: fiche-de-la-nuit-recuperee* · « Audio » indique **ABSENTE (0 / N)**.
- **S8-16** · *geste: fiche-de-la-nuit-recuperee* · « Réactiver ce passage » est **actif** (c'est le défaut d'origine du chantier).
- **S8-17** · *geste: fiche-de-la-nuit-recuperee* · Les heures de la nuit sont marquées **attestées par les enregistrements**, pas « déclarées ».

**Étape 4 bis · Ce que le statut « Récupéré » change (EPIC #2581)**

Le bandeau annonce **« Récupéré »**, pas « Déposé ». Chaque case ci-dessous est un geste dont la réponse
a changé : à jouer une par une, l'œil sur le motif affiché autant que sur l'état du bouton.

- **S8-18** · *geste: fiche-de-la-nuit-recuperee* · La pastille de statut est **violette**, distincte du bleu de « Déposé ».
- **S8-19** · *geste: fiche-de-la-nuit-recuperee* · **Aucune frise** de workflow : une seule étape, « Récupéré ». Les jalons Importé / Transformé /
  Vérifié ne sont pas affichés comme franchis.
- **S8-20** · *geste: fiche-de-la-nuit-recuperee* · La carte mise en avant est **« Réactiver ce passage »**.
- **S8-21** · *geste: fiche-de-la-nuit-recuperee* · **Supprimer** est **actif**. Le survol dit ce qu'il enlève (la copie locale) et ce qu'il laisse (la
  participation).
- **S8-22** · *geste: fiche-de-la-nuit-recuperee* · **Vérifier** est **grisé**, et le motif dit « cette nuit vient de Vigie-Chiro, où elle est déjà
  déposée : son verdict s'y décide », et **non** « cette nuit est déposée ».
- **S8-23** · *geste: fiche-de-la-nuit-recuperee* · **Annuler le dépôt** est **visible mais désactivé**, avec son motif. Il ne doit ni disparaître ni
  être cliquable.
- **S8-24** · *geste: modifier-un-passage-recupere* · **Modifier le passage** ouvre la modale, mais l'année et le n° y sont **verrouillés**.
- **S8-25** · *geste: ecouter-une-nuit-recuperee* · **Sons & validation** est **ouvert** : les observations s'écoutent et se valident dès maintenant.
- **S8-26** · *geste: ranger-une-nuit-recuperee* · Sur **Carte & passages**, la vue **« À réactiver »** liste cette nuit, et le tri par statut la range
  **avec** les nuits déposées, pas après elles.
- **S8-27** · *geste: ma-saison-compte-la-nuit-recuperee* · Sur **Ma saison**, sa case est **remplie** : le protocole est tenu pour cette nuit-là.
- **S8-28** · *geste: relever-l-analyse-d-une-nuit-recuperee* · La vue **« Résultats à importer »** la voit toujours : son état d'analyse se relève normalement.

**Étape 5 · Réactiver depuis la carte SD**

- **S8-29** · *hors-portée: une carte SD réelle d'enregistreur, que le banc ne peut pas présenter* · Désigner la carte : **aucune question** n'est posée à ce moment. Sur une carte qui ne contient que
  des bruts, il ne doit **jamais** y en avoir - les tranches sont régénérées, il n'y a rien à laisser
  en place (#2577). Si des séquences déjà transformées s'y trouvent, la question paraît **dans la
  modale**, à la place des barres, et la procédure reprend après la réponse.
- **S8-30** · *hors-portée: une carte SD réelle d'enregistreur, que le banc ne peut pas présenter* · La modale de réactivation montre ses **deux barres de phase**.
- **S8-31** · *hors-portée: de l'audio réellement revenu de la carte : aucun carton ne peut affirmer qu'une nuit est écoutable* · À la fin, « Audio » passe à **COMPLETE (N / N)** et la nuit est **écoutable**.
- **S8-32** · *hors-portée: une carte SD réelle d'enregistreur, que le banc ne peut pas présenter* · Ouvrir une séquence : le son correspond bien à l'observation affichée.
- **S8-33** · *hors-portée: une carte SD réelle d'enregistreur, que le banc ne peut pas présenter* · **Le statut est passé à « Déposé »** : la nuit a rejoint les nuits ordinaires, elle sort de la vue
  « À réactiver », et « Annuler le dépôt » redevient actif. C'est la seule transition que le moteur
  autorise depuis « Récupéré » : et elle ne doit se produire **que** si de l'audio est effectivement
  revenu : une réactivation infructueuse laisse la nuit en « Récupéré ».

**Étape 6 · Le rattrapage**

- **S8-34** · *geste: completer-une-nuit-sans-contenu* · Menu ☰ › **Compléter une nuit récupérée** : seules les nuits **sans contenu** y figurent.
- **S8-35** · *geste: completer-une-nuit-sans-contenu* · L'état de liste ne prétend plus qu'elles « n'existent pas sur cette machine ».
- **S8-36** · *geste: completer-une-nuit-sans-contenu* · Compléter une nuit : elle disparaît de la liste et gagne ses observations.

**Étape 7 · La migration, sur une base qui a déjà servi (EPIC #2581)**

- **S8-37** · *geste: migration-d-une-base-anterieure* · Ouvrir une base **antérieure** au chantier, portant des nuits rapatriées en « Déposé ». Après
  migration, elles portent **« Récupéré »**, et **elles seules** : une nuit que vous aviez déposée
  garde « Déposé », même rattachée à sa participation.
- **S8-38** · *geste: migration-d-une-base-anterieure* · Le carré 130711 est le bon terrain : ses quatre nuits ont été rapatriées avant ce chantier.

## Ce que la session doit mesurer

- **La lisibilité du statut** : « Récupéré » se comprend-il sans explication, ou faut-il lire la doc ?
  C'est le seul juge de l'EPIC #2581 : le reste est tenu par des tests.
- **Durée** de la première synchronisation, et nombre de nuits traitées. Repère mesuré au banc :
  10,5 s pour 2 nuits (≈ 9 000 séquences, 24 542 observations). C'est ce chiffre qui a permis d'écarter
  un bornage du balayage ; s'il ne tient pas en usage réel, la décision est à rouvrir.

## Verdict par axe (dernière passe)

À remplir en séance.

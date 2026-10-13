## Context

`MoteurTraitementGroupe` déroule une `ActionGroupee` sur une liste de `CiblePassage`. Il consulte le jeton
d'annulation avant chaque cible, demande à l'action son `motifNonEligible`, l'exécute, et range chaque cible
sous l'un de quatre statuts. Quatre gestes s'y branchent, tous des singletons sans état, et deux surfaces
rendent son compte rendu : `TraitementLot` à l'écran, `TraiterPassages` en ligne de commande.

`ServiceReconstructionPassages.reconstruireTout` fait le même travail par une boucle à elle, sur des
`ParticipationOrpheline`. La cartographie de #2755 liste ce que chacun fait et que l'autre ne fait pas.
Quatre points empêchent de brancher la reconstruction telle quelle, relus dans le code le 13 octobre 2026 :

- `CiblePassage` exige un identifiant de passage non nul, et une nuit à reconstruire n'a pas encore de passage ;
- `executer` rend `void`, et la reconstruction rend un `RapportReconstruction` dont les surfaces font des totaux ;
- le moteur range toute exception sous `ECHEC`, annulation comprise, et la reconstruction s'annule en cours de
  nuit par `OperationAnnuleeException` ;
- le moteur ne publie qu'un journal de lignes, et l'écran de la reconstruction montre deux avancements.

L'ADR 2357 gouverne le moteur. Elle pose que l'annulation s'arrête entre deux passages, et que l'éligibilité
s'annonce sans effet de bord ni réseau.

## Goals / Non-Goals

**Goals:**

- La reconstruction groupée est une `ActionGroupee` que le moteur déroule, et `reconstruireTout` disparaît.
- Les sept exigences de `multisite/reconstruction-de-toutes-les-nuits` tiennent sur les deux surfaces.
- Les quatre gestes existants gardent leur comportement, vérifié par leurs tests tels qu'ils sont.

**Non-Goals:**

- Une sélection de nuits par cases à cocher dans la modale.
- Une barre d'avancement par passage pour les quatre autres gestes.
- Arrêter un lot quand la plateforme devient injoignable : chaque nuit restante échoue à son tour, comme
  aujourd'hui.
- Rendre la reconstruction joignable par `traiter-passages` : ses cibles ne sont pas des passages.

## Decisions

### 1. La cible est un type que le geste choisit

`ActionGroupee` devient générique sur sa cible, `ActionGroupee<C extends CibleDeLot>`, où `CibleDeLot` n'exige
qu'une `designation()`. `CiblePassage` l'implémente sans changer. La reconstruction apporte la sienne, bâtie
sur `ParticipationOrpheline`, dont la désignation est le carré, le point et la date de la nuit. Le moteur,
`IssueTraitement` et `ResultatTraitementGroupe` prennent le même paramètre.

Écarté : donner à `CiblePassage` un identifiant factice pour une nuit sans passage. Les deux surfaces du lot
lisent cet identifiant, et il mentirait. Écarté aussi : créer d'abord un squelette pour chaque nuit, puis
traiter des passages. Cela écrirait en base avant l'annonce, que l'utilisateur peut refuser.

### 2. Le résultat chiffré passe par un rappel, pas par le contrat

`executer` continue de rendre `void`. Le porteur de la reconstruction reçoit, à sa construction pour un lot,
un consommateur de rapports : il lui remet le `RapportReconstruction` de chaque nuit complétée. La surface y
branche ce qu'elle veut, ses totaux pour l'écran, sa ligne par nuit pour la commande. C'est le rappel
`issueParNuit` d'aujourd'hui, réduit aux nuits complétées, les autres sorts venant du moteur.

Écarté : un second paramètre de type pour le résultat, `ActionGroupee<C, R>`. Il traverserait six classes et
deux surfaces pour le besoin d'un seul geste, et les quatre autres le rempliraient de `Void`. Écarté aussi : un
porteur qui garde ses rapports en champ. Les porteurs sont des singletons, et deux lots lancés à la suite s'y
mélangeraient.

### 3. Un geste peut écarter une cible pendant qu'il la traite

Le moteur reconnaît une exception dédiée, levée par `executer`, qui porte un motif : la cible est alors
`ECARTE`, et non `ECHEC`. Le porteur de la reconstruction la lève pour une nuit qui ne peut pas encore, ou n'a
plus à, être complétée : l'analyse non terminée, le passage déjà pourvu, le squelette disparu entre l'annonce
et le traitement. Une panne reste un échec : poste non connecté, plateforme injoignable ou qui refuse.

`motifNonEligible` garde sa règle de l'ADR 2357, sans réseau. Pour la reconstruction il répond avec ce que le
poste sait déjà : le point d'écoute inconnu, que `ParticipationOrpheline` porte, et l'import Vigie-Chiro
désactivé.

Écarté : faire rendre un sort à `executer` à la place de `void`. Les quatre gestes existants devraient tous
rendre « réussi », et le dépôt signale déjà ses refus par exception. Écarté aussi : lire la plateforme pendant
l'annonce pour savoir d'avance quelles analyses sont terminées. C'est un appel par nuit avant toute
confirmation, et l'entorse que l'ADR 2357 interdit.

### 4. Une annulation en cours de cible la rend non traitée

Le moteur attrape `OperationAnnuleeException` avant les autres exceptions. La cible en cours est
`NON_TRAITE`, le lot est interrompu, et les cibles suivantes sont `NON_TRAITE` sans être touchées. Le contrat
d'`ActionGroupee` le dit : un geste n'honore le jeton en cours de route que s'il laisse sa cible dans son état
d'avant, ce que fait la compensation de la reconstruction, ou dans un état nommé et reprenable, ce que fait
le téléversement.

Vérifié le 13 octobre 2026 : le dépôt reçoit l'annulation comme un test et ne lève pas cette exception. Le
téléversement groupé ne change donc pas de comportement.

Écarté : attendre la fin de la nuit en cours, comme la page utilisateur le promet. Le porteur a tranché de
garder le comportement du code et de corriger la page.

### 5. L'avancement a deux niveaux, et les gestes existants n'en voient rien

Le moteur accepte, en option, un consommateur d'avancement du lot, auquel il publie la cible en cours sur le
total. Le geste reçoit un consommateur d'avancement pour sa cible, par une méthode à implémentation par
défaut qui renvoie à `executer` tel qu'il existe : les quatre gestes ne sont pas réécrits.

Écarté : ajouter le paramètre à `executer` pour tous. Quatre porteurs changeraient de signature pour ignorer
un argument.

### 6. Les surfaces de la reconstruction reprennent celles du lot

La modale annonce et rend compte avec les mots du lot, appliqués aux nuits : combien sur combien, les
écartées avec leur motif, les quatre sorts dans le compte rendu. La commande écrit sa ligne par nuit, son
résumé, et sort en 0 ou en 1 selon la règle de `traiter-passages`. Le geste unitaire,
`ServiceReconstructionPassages.reconstruire`, ne change pas : c'est lui que le porteur appelle, ce que le
titre de l'ADR 2357 demande d'un lot.

## Risks / Trade-offs

- **Le paramètre de type traverse le moteur et ses deux surfaces.** Le compilateur garde les quatre gestes, et
  leurs tests ne doivent pas bouger : un test du lot qui change dans le lot 1 est le signal d'une régression.
- **Le JSON de `reconstruire-passage --tout` change.** Aucun cas bats ne joue cette option et `dev-docs/cli.md`
  ne la documente pas, ce qui limite l'exposition sans la mesurer chez les utilisateurs.
- **Une plateforme injoignable fait échouer les nuits une à une.** C'est le comportement d'aujourd'hui pour
  les refus métier, et le porteur l'a retenu : une nuit défectueuse ne doit pas bloquer les suivantes.
- **Les aperçus de la modale sont à régénérer.** L'annonce est un état neuf, sans capture à ce jour.
- **L'ADR 2357 est étendue sur deux points.** L'ADR du lot 1 le dit et pose l'encart de révision, pour que
  l'ancienne ne se lise plus seule.

## Migration Plan

Le lot 1 étend le contrat sans brancher la reconstruction : le moteur et ses quatre gestes restent verts.
Le lot 2 branche la reconstruction et retire `reconstruireTout`, avec ses deux surfaces dans leurs lots
propres. La commande change de sortie dans un seul lot, pour que le journal des versions le dise une fois.

Revenir en arrière tient à la fusion de chaque lot : aucun schéma de base ne change, et rien n'est migré.

## Open Questions

- **Une nuit que la plateforme ne permet pas de dater** est aujourd'hui « ignorée ». Écartée, comme une nuit
  qui ne peut pas être complétée, ou en échec, comme une donnée fautive ? La note propose écartée ; le lot 2
  le confirme en lisant le cas.
- **Le changement de sortie de la commande porte-t-il la marque de rupture des commits conventionnels ?** Elle
  ferait monter la version majeure à la publication suivante. C'est une décision du porteur, à prendre avant
  le lot de la commande.

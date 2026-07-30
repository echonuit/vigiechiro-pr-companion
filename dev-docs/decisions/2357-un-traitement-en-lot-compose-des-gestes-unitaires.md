# ADR 2357 - Un traitement en lot compose des gestes unitaires, il n'en invente pas un nouveau

- **Statut** : Accepté - 2026-07-28
- **Chantier** : #2357 (lot 3 de l'EPIC #2349)
- **Vérification** : certaine - `MoteurTraitementGroupeTest#annulation_apres_le_passage_courant`

> Le contrat d'annulation est celui qui rend le reste tenable, et c'est le seul qui se prouve d'un
> test. Les autres conséquences ont les leurs, dans la même veine :
> `LancementCalculGroupeTest#ne_force_jamais` et `ImportResultatsGroupeTest` tiennent l'absence des
> options destructrices, `TraitementLotTest#annonce_les_ecartes_avant_de_lancer` tient l'annonce
> préalable, et `TraiterPassagesTest#ecartes_annonces_sans_etre_tentes` la tient côté terminal.

## Contexte

Rentrer de terrain avec six cartes SD, c'est six fois la même suite de gestes : préparer le dépôt,
téléverser, déclencher le calcul, importer les résultats. L'écran « Carte & passages » les proposait
une nuit à la fois, depuis la fiche de chacune.

La tentation, en écrivant le lot, est d'en faire un **geste neuf** : un moteur de lot qui saurait
téléverser, une modale qui suivrait la progression, un plafond de parallélisme accordé au nombre de
nuits. C'est ainsi qu'on se retrouve avec deux chemins pour un même dépôt, dont un seul est éprouvé.

Trois éléments du socle existaient pourtant déjà, et disaient quoi faire : `Confirmateur` pour annoncer,
`SuiviOperation` pour exécuter avec barre et bouton « Annuler », `Notificateur` pour rendre compte.

## Décision

**Un lot applique N fois un geste qui existe déjà, et n'ajoute que ce qui relève du nombre.**

Le geste reste celui de la nuit unique, avec ses règles et ses garde-fous. Ce que le lot ajoute est
l'ordonnancement, l'annonce préalable et le compte rendu : rien d'autre. Cinq conséquences en découlent,
et chacune est un endroit où l'on serait tenté de faire autrement.

**1. Le moteur est séquentiel, et le plafond de parallélisme reste celui d'un passage.**
`DepotVigieChiro` téléverse déjà cinq unités de front, plafond calqué sur le front web
(`max_concurrent_uploads`). Lancer N passages en même temps multiplierait ce plafond par N et
transformerait un confort en rafale que le serveur rejette. Le parallélisme reste **à l'intérieur** d'un
passage, jamais entre eux : ce qui n'est pas une omission mais le respect de la borne de politesse
décrite par l'[ADR 0044](0044-le-mecanisme-de-parallelisme-suit-la-nature-de-l-attente.md).

**2. L'éligibilité est locale, peu coûteuse, et consultée avant de partir.** Chaque `ActionGroupee`
porte la sienne (`motifNonEligible`), lue sur l'état déjà en base : statut du dépôt, rattachement à une
participation, présence de résultats. Elle ne joint **jamais** la plateforme : elle est appelée sur
**toute** la sélection avant le premier geste, et vingt allers-retours réseau pour afficher une annonce
seraient un défaut, pas une précaution. Ce qui exige le réseau ressort donc en **échec avec son motif**,
pas en écart.

**3. L'annulation s'arrête entre deux passages.** Le jeton est consulté **avant** chaque passage, jamais
pendant : un passage commencé va au bout, un passage non commencé est rendu `NON_TRAITE`. Chaque nuit est
donc **soit dans son état d'avant, soit dans son état d'après, jamais entre les deux** : c'est ce qui rend
un lot interrompu reprenable, et c'est tenu par construction plutôt que par vigilance. Le téléversement
fait exception en passant aussi le jeton au dépôt, et c'est légitime : `DEPOT_EN_COURS` avec son plan
persisté est un état **nommé et reprenable**, pas un entre-deux.

**4. Un échec n'arrête pas le lot.** L'action qui lève est enregistrée avec son motif, et le suivant est
tenté. Rentrer avec six cartes et voir cinq nuits abandonnées parce que la première a échoué serait le
contraire du service rendu.

**5. Les options destructrices ne sont pas exposées en lot.** `--forcer` (relancer un calcul) et
`--remplacer` (réimporter un jeu de résultats) existent sur les gestes unitaires, où un humain décide
pour **une** nuit en connaissance de cause. Les actions groupées ne les portent pas, et rien ne permet de
les activer : un recalcul détruit les observations d'une nuit déposée en archives (l'audio n'y est pas
conservé, #1244), et un remplacement touche à ce que l'observateur a validé. Vingt d'un coup ne serait
pas un service.

## Conséquences

**Ajouter une action de lot est un exercice cadré** : implémenter `ActionGroupee` (libellé, éligibilité
locale, exécution), la lier par `OptionalBinder` sous son nom depuis la feature qui la possède, et
l'entrée de menu apparaît. Absente (feature désactivée), l'entrée **disparaît** au lieu de rester grisée
sans recours ([ADR 0003](0003-feature-plugin-desactivable-ports-optionnels.md)). Le moteur n'a rien à
savoir de plus.

**Le geste entier se teste sans écran.** N'employer que des ports injectables plutôt qu'inventer une
modale rend le lot vérifiable **jusqu'à son effet**, headless. Une modale dédiée aurait été plus riche :
le journal ligne à ligne pendant l'exécution, et intestable au même prix. Le journal existe malgré tout :
il est remis à `SuiviOperation`, préfixé du passage concerné.

**La ligne de commande en hérite gratuitement.** Le moteur et les actions ne connaissent aucune surface :
`traiter-passages` les réutilise tels quels ([ADR 0014](0014-parite-cli-ihm.md)). Ce qu'elle apporte n'est
pas la boucle (un terminal sait boucler), mais **l'écran d'éligibilité**, que rien d'autre n'expose.

**Un motif d'échec est rédigé par la surface, pas par le moteur.** Le moteur reçoit sa rédaction
(`GesteAttendu::message` pour l'application, `GesteAttenduCli::message` pour le terminal) : sans cela, un
jeton expiré au septième passage donnerait treize lignes disant ce qui manque et aucune disant quoi faire
([ADR 2635](2635-un-refus-dit-ce-qui-manque-la-surface-dit-quoi-faire.md)).

## Alternatives écartées

- **Paralléliser entre les passages**, avec un plafond global réparti. Séduisant sur le papier : six
  nuits à téléverser vont six fois plus vite. En pratique il faut alors partager une borne entre des
  gestes qui ne se connaissent pas, et le premier « optimiseur » qui la desserrera n'aura aucun moyen de
  savoir ce qu'elle protégeait. La séquentialité rend la borne évidente : c'est celle d'un passage.
- **Déduire l'éligibilité de l'échec**, sans annonce préalable. Moins de code, et l'information est la
  même, sauf qu'elle arrive trop tard : téléverser crée une participation côté plateforme avant de
  refuser trois lignes plus loin. On aurait laissé des traces distantes pour des nuits qu'on savait
  refuser.
- **Une modale dédiée au lot**, avec son journal et sa progression détaillée. Meilleure expérience,
  effectivement : mais elle n'aurait été éprouvée par aucun test headless, sur un geste qui touche à
  vingt nuits d'un coup. Le compromis a été assumé dans l'autre sens.
- **Un moteur qui sait faire les quatre gestes**, plutôt qu'un moteur aveugle et quatre actions. C'est le
  chemin qui produit deux implémentations du dépôt, dont une seule est maintenue.

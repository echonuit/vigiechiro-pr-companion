# ADR 2554 — La synchro amène chaque nuit à un niveau de complétude, et le dit sans supposer

- **Statut** : Accepté — 2026-07-27
- **Chantier** : EPIC #2554 (#2555, #2557, #2558)
- **Amende** : ADR 0016
- **Vérification** : humaine — « à quel niveau chaque nuit doit être amenée » est une politique de récupération, arbitrée par le coût réseau du moment ; aucun motif statique ne la capture. Les invariants qui en découlent sont, eux, tenus par des tests (ventilation exhaustive du bilan, idempotence, reprise après annulation)

## Contexte

L'ADR 0016 a réparti la récupération d'une nuit entre **trois gestes** : la synchro rapatrie la structure, la reconstruction rapatrie les observations, la réactivation rebranche l'audio et l'ancrage. Sa section Conséquences annonçait des coutures « réutilisables et composables ».

Dans les faits, une seule composition a été écrite. Une nuit fraîchement synchronisée n'avait **aucune séquence**, donc rien à confronter à un dossier : sa fiche grisait « Réactiver ce passage » avec pour tout motif « ce passage n'a aucune séquence importée localement », et la seule porte de sortie s'annonçait comme concernant des nuits « qui n'existent pas sur cette machine » — ce qu'un squelette n'est pas. L'utilisateur voyait ses nuits revenir et ne pouvait rien en faire.

Ce que l'ADR 0016 n'avait pas prévu, c'est que **son propre chiffrage vieillirait**. Elle écarte « tout rapatrier à la synchro » pour coût prohibitif : ce coût était celui de la pagination `donnees`, une cinquantaine de pages par nuit. Depuis #1565, le CSV d'observations se télécharge en **deux GET**. Et #1814 avait déjà fait payer à la synchro **un appel de détail par nuit nouvelle**. La frontière posée en 0016 défendait un coût qui n'existait plus.

## Décision

**La question n'est pas « quel geste rapatrie quoi », mais « à quel niveau de complétude chaque nuit est amenée ».** Trois niveaux, chacun avec un repli **best-effort** :

| Niveau | Ce qu'il pose | Ce qu'il coûte |
|---|---|---|
| **structure** | point, date, n° | la liste des participations, gratuite |
| **identité** | enregistreur, météo, micro, fin de nuit (#1814) | un appel de détail par nuit nouvelle |
| **contenu** | séquences et observations | deux GET par nuit (le CSV) |

**La synchro va jusqu'au niveau contenu**, et pour **toutes** les nuits sans séquences — celles qu'elle vient de créer comme les squelettes hérités d'une synchro antérieure. Une politique qui ne rattraperait pas l'existant condamnerait les nuits déjà rapatriées à rester vides à vie, ce que #1814 avait déjà appris à ses dépens.

Trois règles encadrent cette politique.

**1. Le repli `donnees` est réservé au geste désigné.** Sur un balayage de compte, il ferait resurgir exactement le coût qui avait fait écarter « tout rapatrier ». Sur **une** nuit que l'utilisateur vient de désigner, il est justifié. Deux sources, donc, selon qui appelle.

**2. L'hydratation se fait en place sur le chemin de la réactivation.** L'ADR 0016 avait écarté l'hydratation en place au motif qu'elle dupliquerait le chemin de création. L'objection est tombée (`creerSequences` a depuis été factorisée), et deux raisons neuves l'imposent : un **écran est ouvert** sur cet identifiant de passage, et un squelette porte peut-être des **saisies manuelles** que la plateforme ignore — n° de série (#1828), météo (#1688), heures de nuit — qu'un delete + recreate écraserait en silence. La reconstruction, elle, garde son delete + recreate : elle s'exerce sur une nuit qu'on remplace délibérément.

**3. Le compte rendu ventile par cause, et n'affirme jamais une cause qu'il n'a pas constatée.** Le bilan d'un balayage couvre **exactement** les nuits en squelette du début : complétées, en attente d'analyse, non lues. La distinction entre les deux dernières n'est pas cosmétique. Le premier compte rendu annonçait « en attente d'analyse Vigie-Chiro » pour toute nuit non complétée, y compris celles dont le CSV n'avait pas pu être **lu** : on affirmait une cause qu'on n'avait pas observée, et on orientait vers l'attente là où il fallait réessayer.

## Conséquences

- Le cas nominal ne rencontre plus de nuit vide : après une synchro, une nuit arrive avec ses séquences et ses observations, sa fiche propose « Réactiver ce passage », et une carte SD lui rend son audio.
- La synchro devient une **opération longue** — mesurée à 10,5 s pour deux nuits (≈ 9 000 séquences, 24 542 observations) sur compte réel, soit de l'ordre de deux minutes pour cent nuits. Elle n'est pas **bornée** pour autant : l'**annulation** remplace la borne, ce qui n'est tenable que parce que chaque nuit est écrite entièrement ou pas du tout, et que la synchro est idempotente. Interrompue, elle reprend au tour suivant.
- La synchro tournant **aussi à la connexion**, les deux surfaces portent la même barre et le même bouton « Annuler ».
- Le parallélisme s'arrête au **réseau** : les téléchargements partent par huit (borne d'entrée/sortie), les écritures restent en série, SQLite étant mono-écrivain.
- Un modèle **ne nomme ni la surface ni le geste** dans ses refus. Les mêmes lectures distantes servent désormais la reconstruction, la réactivation, l'IHM et la CLI : un message qui disait « avant de reconstruire un passage (menu ☰ > Se connecter) » servait un menu à qui travaille dans un terminal, et un geste à qui en faisait un autre.

## Alternatives écartées

- **Laisser les observations à la seule reconstruction** (l'état de l'ADR 0016). C'est ce qui produisait le cul-de-sac : la nuit revenait, et le seul geste utile de sa fiche était grisé.
- **Hydrater à la réactivation uniquement**, sans toucher à la synchro. Ferme le cul-de-sac mais laisse le cas nominal en l'état : chaque nuit consultée demanderait un geste de plus. Retenu comme **filet** (#2555), pas comme politique — il reste nécessaire pour les nuits dont le CSV n'était pas prêt au moment de la synchro.
- **Borner le balayage** (top-N nuits par tour). Écarté **sur mesure** et non au jugé : les deux minutes extrapolées pour cent nuits ne justifient pas de tronquer silencieusement, et un silence sur ce qui a été laissé de côté est précisément ce que la règle 3 interdit.

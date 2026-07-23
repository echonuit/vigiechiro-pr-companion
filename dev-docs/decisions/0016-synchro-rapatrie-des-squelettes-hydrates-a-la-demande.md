# ADR 0016 — La synchro rapatrie les nuits en squelettes, hydratés à la demande

- **Statut** : Accepté — 2026-07-17, **amendé** par [ADR 0018](0018-la-synchro-rapatrie-l-identite-de-la-nuit.md) (#1814) et [ADR 0019](0019-ancrage-acquis-quand-il-sert.md) (#1838)
- **Chantier** : EPIC #1662 (refonte de la récupération d'une nuit)
- **Vérification** : humaine — le rapatriement en squelettes hydratés à la demande est un comportement de synchro, non observable par un motif statique

> [!NOTE]
> **Amendement (ADR 0018)** : le squelette n'est plus « sans aucun appel de détail par nuit ». La synchro
> paie désormais **un** détail par nuit **nouvelle** pour rapatrier son **identité** (enregistreur, météo,
> micro, fin de nuit) ; les observations et l'audio restent à l'hydratation. Le reste de cette décision
> (squelette léger, hydratation à la demande, remplacement à la reconstruction) tient toujours.

> [!NOTE]
> **Amendement (ADR 0019)** : la réactivation n'est plus le **seul** moment où l'ancrage s'acquiert. La
> publication des corrections le rapatrie elle-même quand il manque et que la nuit est rattachée : ancrage
> et audio servent deux gestes différents (publier / écouter), les lier obligeait à réactiver une nuit
> pour un geste qui ne demande aucun fichier. La couture « audio + ancrage » de la réactivation reste
> valable, elle n'est plus un passage obligé.

## Contexte

Récupérer une nuit déposée sur VigieChiro mais absente d'ici mêlait **trois gestes de natures différentes** en un seul appel : recréer la **structure** (passage, séquences), rapatrier les **observations**, rebrancher l'**audio + ancrage**. La reconstruction téléchargeait tout, nuit par nuit ; la synchro « mes sites » s'arrêtait aux points. L'historique des nuits n'apparaissait donc nulle part tant qu'on ne reconstruisait pas explicitement, une par une - or on ne sait pas quelles nuits récupérer si aucune ne s'affiche.

Les données ont pourtant trois **coutures naturelles** : la structure (ce qu'est la nuit : un point, une date), les observations (ce que Tadarida y a identifié), l'audio + ancrage (les fichiers et leur lien à la donnée serveur).

## Décision

La synchro « mes sites » rapatrie les nuits sous forme de **squelettes** : la structure **seule** (point + date + numéro, passage déposé et archivé, enregistreur « inconnu »), **sans observations ni audio**. Un squelette est **léger** (aucun appel de détail par nuit) et rend l'historique **immédiatement visible**. Il s'**hydrate à la demande** : la reconstruction (une nuit, ou toutes en lot) y rapatrie les observations ; la réactivation y rebranche l'audio + l'ancrage (#1571). Reconstruire une nuit déjà rapatriée en squelette la **remplace** (delete + recreate, le lien reposé), au lieu de la refuser.

## Conséquences

- L'historique des nuits **apparaît dès la synchro**, sans coûter un téléchargement d'observations par nuit ; l'hydratation, coûteuse, ne se paie que sur les nuits qu'on veut vraiment consulter.
- Les trois coutures deviennent **réutilisables et composables** : la reconstruction compose structure (`CreationPassageArchive`) + observations ; l'import groupé rejoue la reconstruction sur un lot ; la réactivation ajoute audio + ancrage. Aucune logique d'import dupliquée.
- Un squelette (archivé, 0 séquence, 0 résultat) est un état **bénin** pour l'audit (patron « archivé », ADR 0005 / #1300) : il informe, il ne crie pas.
- Compromis assumé : un site tout juste synchronisé voit ses passages à la **synchro suivante** (les rapprocheurs ne sont pas ordonnés, et un squelette exige son point déjà local). La synchro est **idempotente** ; rien n'est perdu.

## Alternatives écartées

- **Tout rapatrier à la synchro** (structure + observations + audio). Coût prohibitif (un appel de détail + un import par nuit, pour des nuits qu'on ne consultera peut-être jamais) et impossible pour l'audio, que le dépôt ZIP ne restitue pas (ADR 0006).
- **Ne rien rapatrier, garder la reconstruction une-par-une.** L'historique restait invisible : l'utilisateur ne savait pas quelles nuits existaient à récupérer.
- **Hydrater un squelette « en place »** plutôt que delete + recreate. Aurait dupliqué le chemin de création (séquences, matériel, météo) sur un passage existant ; le squelette ne portant **aucune** donnée, le remplacer ne perd rien et réutilise **tout** le chemin de reconstruction.

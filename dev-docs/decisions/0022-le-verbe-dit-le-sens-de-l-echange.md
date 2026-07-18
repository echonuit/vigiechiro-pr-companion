# ADR 0022 — Le verbe d'un geste dit le sens réel de l'échange

- **Statut** : Accepté — 2026-07-18
- **Chantier** : #1855, #1866 (suites de l'EPIC #1662 et du chantier #1838)

## Contexte

L'application et la plateforme s'échangent des données dans les deux sens, par une dizaine de gestes différents. Le mot qui nomme chacun de ces gestes est la **seule** indication que l'utilisateur a sur ce qui va se passer : il ne voit ni la requête, ni la direction, ni ce qui sera écrit chez qui.

Deux fois, à deux chantiers d'écart, ce mot a menti.

**#1855.** Deux boutons s'appelaient « Synchroniser depuis VigieChiro » alors qu'aucun ne pousse quoi que ce soit : celui de la modale tire météo, micro et enregistreur ; celui de Mes sites tire sites, points et passages. « Synchroniser » annonce un échange bidirectionnel. Le défaut est devenu criant quand #1839 a posé un vrai « Envoyer vers VigieChiro » juste à côté du premier : deux boutons voisins, l'un qui reçoit et l'autre qui envoie, portaient des verbes qui ne disaient pas la différence.

**#1838.** Une phase de progression s'appelait « Ancrage des observations **sur** VigieChiro… » pour un traitement qui **télécharge** des identifiants. Le libellé était partagé avec la réactivation (#1571) et figurait déjà dans une capture : le chantier n'avait pas créé le défaut, il l'avait propagé.

Entre les deux, rien n'a signalé la récidive. La règle posée par #1855 existait, elle était juste, et elle vivait **uniquement dans le corps d'un message de commit** — un endroit que personne ne relit.

Une troisième question est alors revenue (#1866) : le menu ☰ de « Sons & validation » dit « **Importer** depuis VigieChiro… » là où Mes sites dit « **Récupérer** depuis VigieChiro ». Même plateforme, même direction, deux verbes. Est-ce le même défaut ? Non, et la raison mérite d'être écrite : c'est précisément ce qui distingue une incohérence d'une distinction utile.

## Décision

**1. Le verbe dit le sens réel de l'échange.** Un geste qui ne fait que recevoir ne s'appelle pas « synchroniser ». Le verbe porte l'action, le complément porte la direction : « Récupérer **depuis** VigieChiro », « Envoyer **vers** VigieChiro ». Un verbe qui promet plus que le geste est un défaut, même quand la direction est correcte par ailleurs.

**2. Le vocabulaire se répartit par nature d'objet, pas par destination.** « Importer » désigne l'**entrée de données dans l'application**, quelle qu'en soit la source : une carte SD, un CSV local, ou la plateforme. « Récupérer » désigne le **rapatriement, depuis la plateforme, de ce que l'application connaît déjà** : le référentiel des taxons, ses sites, ses points, l'ancrage d'une nuit.

C'est pourquoi « Importer depuis VigieChiro » et « Récupérer depuis VigieChiro » **coexistent légitimement** : ils ne portent pas le même objet. Le premier fait naître des observations qui n'existaient pas ; le second complète ou rafraîchit ce qui est déjà là.

**3. La règle porte sur ce que l'utilisateur lit.** Libellés de boutons et de menus, messages de progression, noms de commandes CLI et leurs descriptions. Le vocabulaire **interne** n'est pas concerné tant qu'il décrit fidèlement ce qu'il fait : `RapprochementVigieChiro.synchroniser` opère bien un rapprochement entre deux états, et garde son nom.

## Conséquences

- « Importer / Réimporter depuis VigieChiro » **reste**. Ce n'est pas une exception tolérée par lassitude : c'est l'application de la règle 2. La question ne se rouvre pas au prochain chantier qui touchera ce menu.
- La commande CLI `synchroniser-vigiechiro` était le dernier résidu du vocabulaire écarté par #1855 : elle devient `recuperer-vigiechiro`, l'ancien nom restant comme **alias** pour ne pas casser les scripts existants (la parité CLI ↔ IHM de l'[ADR 0014](0014-parite-cli-ihm.md) porte aussi sur les mots).
- Un libellé partagé par deux gestes se change **en plusieurs endroits** : la source, la doc utilisateur, la fiche de recette, et la capture qui le montre. #1838 a livré le remède structurel — quand deux gestes partagent un libellé, il vit dans **une constante** (`AcquisitionAncrage.LIBELLE`) et non dans deux chaînes jumelles. Sans quoi le renommage se fait une fois sur deux, et rien ne le dit.
- Les termes vus par l'utilisateur sont désormais définis dans le [glossaire utilisateur](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/glossaire/) ; leur sens interne, dans le [glossaire du domaine](../glossaire.md). Une divergence entre les deux reste un défaut de vocabulaire à corriger, comme le pose déjà ce dernier.

## Alternatives écartées

- **Aligner « Importer » sur « Récupérer » pour n'avoir qu'un seul verbe vers la plateforme.** C'est la piste qu'avait déjà écartée #1855, avec sa raison : « importer » a un sens fort et précis dans l'application (importer une nuit depuis une carte SD), et le rabattre sur le seul cas plateforme brouillerait le vocabulaire métier. La réciproque est plus parlante encore : personne n'écrirait « Récupérer depuis la carte SD ».
- **Laisser la règle dans les messages de commit.** C'est l'état qui a produit la récidive de #1838. Une convention qui ne survit que dans l'historique git est une convention qui sera manquée.
- **Un lexique exhaustif, verbe par écran.** Fige des libellés qui bougent, et se périme au premier chantier qui en ajoute un. Une règle se transporte vers les cas qu'elle n'a pas prévus ; une liste, non.
- **Renommer aussi le vocabulaire interne** (`RapprochementVigieChiro.synchroniser`, `RapportSynchro`). Beaucoup de surface remuée pour aucun bénéfice utilisateur, et à contresens : ces classes réconcilient bien deux états, « synchroniser » y est le mot juste.

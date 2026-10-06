---
type: adr
title: "Les archives du repli manuel ne rendent au serveur que ce qu'il n'a pas"
status: stable
article: A17
heuristiques:
  - "nielsen-5"
chantier: "#5975, lot 4 du chantier #5967 (les suites de la clôture de #5596)"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/RegenerationPendantUnDepotTest.java"
  - "src/test/java/fr/univ_amu/iut/commun/api/plateforme/ArchiveDuRepliSurLaPlateformeDeTestTest.java"
verification_note: "la première classe tient la règle sur une vraie base, avec le moteur de dépôt : dix séquences dont quatre en ligne, six dans les archives, ses deux témoins et le refus quand tout est en ligne ; la seconde joue l extraction du serveur à la révision épinglée, le doublon d un son déjà en ligne puis son absence quand l archive ne rend que ce qui manque"
relations:
  amende: ["5867-le-repli-manuel-s-offre-apres-un-refus-sans-recours"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Les archives du repli manuel ne rendent au serveur que ce qu'il n'a pas

## Contexte

L'ADR 5867 offre un repli quand Vigie-Chiro refuse des séquences sans recours : générer des archives ZIP et
les déposer à la main sur le portail. Elle laissait le générateur intact, donc ces archives contenaient
**toute la nuit**, séquences déjà en ligne comprises, et tenait pour une hypothèse ce que le portail en ferait.

Le porteur a demandé de jouer le scénario (#5970). Sur le code du serveur à la révision épinglée, l'hypothèse
est fausse. Une archive n'est extraite qu'au traitement de la participation, et le serveur ajoute alors chaque
fichier qu'elle contient **sans chercher** s'il est déjà là : rien n'impose l'unicité d'un titre. Deux sons
déjà en ligne et une archive des quatre sons de la nuit donnent six fichiers pour quatre titres. L'analyse
regroupe par nom, donc les observations ne sont pas doublées ; mais la liste des fichiers l'est, et c'est elle
que l'application relit pour son audit et sa récupération.

## Décision

**1. Sur un dépôt entamé en séquences WAV, les archives ne contiennent que les séquences que le plan de dépôt
ne dit pas déposées.** La génération lit le plan, où chaque séquence déposée a son unité.

**2. La règle tient à la forme du plan, pas à l'offre du repli.** L'écran et `exporter-lot` passent par la
même génération : la commande ne peut pas rendre des doublons que l'écran éviterait.

**3. Quand toutes les séquences sont en ligne, la génération refuse en le disant.** Une archive vide ne
servirait qu'à faire croire qu'il reste quelque chose à déposer.

**4. Hors de ce cas, rien ne change.** Une nuit jamais déposée se génère en entier. Un dépôt entamé en
archives aussi : ses unités sont des archives, et le plan ne dit pas quelles séquences une archive déposée
contient. Sa régénération reste celle de l'ADR 5599.

## Ce qui a été écarté

**Garder toute la nuit, et assumer les doublons.** C'était l'état livré. Les observations ne sont pas doublées,
mais un compte de fichiers faux côté plateforme fausse ce que l'application en relit, sans rien signaler.

**Interroger la plateforme au moment de générer.** Elle dirait ce qui est vraiment en ligne. Mais la génération
ne joint pas le réseau, et le repli s'emploie précisément quand le dépôt connecté ne marche pas.

**Filtrer dans le compacteur.** Il ne connaît que des fichiers, et sert aussi le dépôt en archives, que cette
règle ne concerne pas.

## Conséquences

Le plan est la mémoire locale de ce qui est en ligne. Une séquence déposée puis retirée à la main sur le
portail reste « déposée » pour l'application, et ne sera pas dans l'archive. C'est déjà la limite de la reprise
d'un dépôt, qui lit le même plan ; « Réinitialiser le dépôt » la lève.

La consigne de la carte du repli dit « des séquences qui ne sont pas en ligne », et non plus « de la nuit ».

Le banc de la plateforme de test garde ses deux moitiés : le doublon, qui dit pourquoi la règle existe et
rougira le jour où le serveur dédoublonnera, et le remède. Le dépôt à la main dans le navigateur du portail
national reste à jouer en recette, cas `S4-105`.

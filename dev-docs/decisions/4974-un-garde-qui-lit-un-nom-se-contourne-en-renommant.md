---
type: adr
title: "Un garde qui lit un nom se contourne en renommant"
status: stable
article: A9
chantier: "#4974, lot 1 de l'EPIC #4804"
decided_at: 2026-08-31
verification: certaine
enforced_by:
  - "scripts/adr/4974-attente-reinventee.py"
ratchet: 2
verified:
  - by: machine:ci
    at: 2026-08-31
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-31
---

# Un garde qui lit un nom se contourne en renommant

## Contexte

Le dépôt porte `Attente`, qui attend une condition **et dit ce qu'elle attendait** en expirant. Sans
ce message, l'échec arrive plus tard sur l'assertion, qui accuse le code alors que c'est la mise en
place qui n'a pas eu lieu (ADR 2213).

L'issue #4847 a retiré treize attentes privées, et voulait clore par un cliquet sur la forme qui les
avait trouvées : `private void attendre` employant `waitFor`.

## Le défaut

**La mesure a refusé cette forme, deux fois.**

Les cinq méthodes `attendre` restantes sont **toutes légitimes** : un `CountDownLatch`, deux
cadencements de tournage, un ralentisseur, et l'attente de mise en page devenue une enveloppe
d'`Attente`. Un cliquet sur ce nom les aurait toutes interdites.

Et surtout, en mesurant **sans les noms**, neuf réinventions vivaient sous d'autres : `ouvrirLaFiche`,
`doubleClicVersPassage`, `situer`, `verifier`. Le compte de #4847 valait 13 quand la réalité valait 22.

## Décision

**Le cliquet lit les corps, jamais les noms.** Une méthode **privée**, quel que soit son nom et son
type de retour, dont le corps appelle `WaitForAsyncUtils.waitFor`, est une attente réinventée.

`Attente` est exemptée : elle **est** l'attente partagée, et compter le remède ferait monter le
cliquet à chaque fois qu'on l'enrichit.

**Une aide qui attend n'est pas fautive ; ce qui l'est, c'est d'attendre sans dire quoi.** Le cliquet
borne donc le nombre de sites qui sondent en propre, et chaque survivant porte sa raison dans sa
javadoc.

## Les deux survivants, et pourquoi le cliquet vaut 2 et non 0

Les deux `doubleClicVersPassage` sont des boucles de **reprise**. Leur expiration est rattrapée pour
retenter trois fois, et c'est leur `throw` final qui parle, en joignant les bornes observées de la
cellule, exactement ce que l'ADR 2213 demande. Ce ne sont pas des attentes muettes.

Convertir celle de `ParcoursSitesVersPassage` serait pire que ne rien faire : son `catch` ne reçoit
qu'une `TimeoutException`, et l'`AssertionError` d'`Attente` sortirait au premier essai. La boucle
n'aurait plus que **l'apparence** d'une reprise.

Un cliquet à 0 aurait donc exigé de casser ce que le dépôt a mis trois issues à construire.

## Ce qui prouve que le cliquet voit

Sept témoins, dont trois négatifs qui l'empêchent de tout absorber : un `sleep` n'est pas une
attente, `waitForFxEvents` n'est pas une sonde, un cas de test public n'est pas une aide.

Et un témoin qui porte la décision elle-même : renommer `ouvrirLaFiche` en `patienter` ne soustrait
rien. Sans lui, un cliquet qui lirait le nom paraîtrait juste.

## Conséquences

- Le compte passe de **9 à 2**, et les deux restants sont annotés.
- La population totale de la famille, mesurée sans les noms, valait **22** et non 15 : #4847 en a
  retiré 13, celle-ci 7, et 2 restent par décision.
- `ScenarioFicheSiteTest#ouvrirLaFiche` étant converti, l'issue #4696 perd l'essentiel de son objet :
  son défaut restant est le clic sur une référence tenue, pas l'attente.

## Alternatives écartées

- **Le cliquet sur `private void attendre`.** Refusé par la mesure : il interdit cinq aides légitimes
  et manque neuf réinventions. C'est le dessin que #4847 proposait.
- **Un cliquet à 0.** Il exigerait de convertir deux boucles de reprise dont l'une perdrait sa
  sémantique. Une décision de ne pas faire en est une.

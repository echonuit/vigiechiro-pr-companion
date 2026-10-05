---
type: adr
title: "Le dépôt part en WAV par défaut, et un dépôt entamé garde le mode dans lequel il a commencé"
status: stable
article: A15
heuristiques:
  - "nielsen-5"
chantier: "#5677, lot 18 du chantier #5596 (retour de terrain 2.193.0)"
decided_at: 2026-10-04
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/lot/di/ModeDepotParDefautTest.java"
  - "src/test/java/fr/univ_amu/iut/lot/RegenerationPendantUnDepotTest.java"
verification_note: "les deux classes tiennent le comportement : le mode sans réglage, le mode d une valeur inconnue, le défaut que l onglet affiche, et la reprise d un dépôt entamé. Elles ne disent rien du bien-fondé du défaut, qui repose sur un retour d expérience du porteur et non sur une mesure : aucun dispositif du dépôt ne compare la durée d un dépôt ZIP à celle d un dépôt WAV"
relations:
  amende: ["0006-depot-zip-par-defaut-perte-audio-serveur-assumee", "0034-la-forme-du-depot-se-choisit"]
  completee_par: ["5824-l-ecran-et-la-commande-n-offrent-et-ne-nomment-que-ce-qui-sert"]
verified:
  - by: machine:ci
    at: 2026-10-04
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-04
---

# Le dépôt part en WAV par défaut, et un dépôt entamé garde le mode dans lequel il a commencé

!!! warning "Ce qui fait foi aujourd'hui"
    **2026-10-04** : cette décision est **complétée** par
    [5824](5824-l-ecran-et-la-commande-n-offrent-et-ne-nomment-que-ce-qui-sert.md). Elle changeait le
    défaut sans avoir cherché ce qui supposait l'ancien : l'écran de lot et la ligne de commande
    parlaient encore d'archives pour un dépôt en séquences. Ils lisent désormais la forme du dépôt et
    n'offrent que ce qui la sert. Le reste fait foi.

## Contexte

L'[ADR 0006](0006-depot-zip-par-defaut-perte-audio-serveur-assumee.md) a fait du ZIP le mode par
défaut pour « garder l'acquis de vitesse », et l'[ADR 0034](0034-la-forme-du-depot-se-choisit.md) l'a
maintenu en le disant « le plus rapide ». Cette dernière laissait la question ouverte : faut-il
recommander le WAV par défaut ? Elle demandait un retour de terrain que son chantier n'avait pas.

Ce retour est venu, et il contredit la prémisse. Le porteur du projet le rapporte ainsi : il pensait
les ZIP plus rapides, et lors des dépôts réels la compression a pris beaucoup de temps et de disque
sans apporter de gain perceptible sur le téléversement. La place disque borne en outre le nombre de
compressions qui tournent ensemble, alors que les WAV partent en parallèle sans rien attendre.

**C'est un retour d'expérience, pas une mesure.** Aucun chiffre n'accompagne cette décision, et le
mot « rapide » avait lui-même été écrit sans qu'aucune mesure l'établisse.

Le reste de l'arbitrage ne dépend pas de la vitesse : en ZIP la plateforme supprime l'archive après
extraction, l'audio n'est plus téléchargeable et la participation ne peut plus être relancée. Le
défaut ZIP cumulait donc cette perte avec un gain de temps qui ne se constatait pas.

Changer le défaut a révélé un défaut plus ancien. Le mode n'était pas mémorisé avec le dépôt, il était
relu dans les réglages à chaque tentative. Un dépôt entamé en ZIP, repris en WAV, n'était pas refusé :
l'empreinte du lot ne couvre que la liste des séquences, la même dans les deux modes. Il **renvoyait
toutes les séquences en WAV à côté des archives déjà en ligne**, sans rien dire. Avec un défaut qui
bascule à la mise à jour, tout poste sans réglage et avec un dépôt interrompu l'aurait rencontré.

## Décision

**1. Sans réglage, le dépôt part en séquences WAV.** Le défaut a un seul nom,
`ModeDepot.PAR_DEFAUT`, que partagent le module d'injection, l'onglet des réglages et la lecture d'une
valeur absente ou inconnue. Les trois avaient chacun leur `ARCHIVES_ZIP`.

**2. Un réglage posé est respecté.** Le poste dont l'utilisateur a choisi le ZIP reste en ZIP. Seuls
changent les postes qui n'avaient rien choisi.

**3. Un dépôt entamé garde le mode de ce qui est déjà en ligne.** Dès qu'une unité du passage est
déposée, son type décide : une archive déposée impose le ZIP, une séquence déposée impose le WAV. Le
réglage ne vaut que pour un dépôt qui commence. La règle vit dans `ServiceLot.sourceDepotParDefaut`,
que partagent l'écran, le dépôt groupé et la ligne de commande.

**4. `--archives` et `--wav` priment toujours**, y compris sur un dépôt entamé : la règle 3 protège
d'un changement subi, pas d'un choix dit.

**5. Les textes ne promettent plus de vitesse.** Le libellé « Archives ZIP (rapide) » devient
« Archives ZIP (audio non conservé en ligne) ». L'aide et la page des réglages disent ce que chaque
forme coûte : de la bande passante pour le WAV, du temps et du disque de compression pour le ZIP.

## Ce que cette décision n'autorise pas

Elle n'autorise pas à écrire que le WAV est « plus rapide » : ce serait remplacer une affirmation
sans mesure par une autre.

Elle ne retire pas le mode ZIP, qui reste un bon choix sur une connexion lente ou limitée en volume.

Elle ne migre aucun réglage : ce serait défaire un choix que l'ADR 0034 a rendu possible.

## Conséquences

À la mise à jour, un poste sans réglage dépose ses prochaines nuits en WAV : davantage de requêtes et
de bande passante, plus d'attente de compression, et un audio qui reste en ligne.

Les avertissements sur la relance impossible et l'audio perdu restent justes : ils décrivent les
nuits déposées en ZIP, qui existent toujours.

Le mode d'un dépôt entamé se **déduit** des unités déposées, il n'est pas **écrit** avec le plan. Un
dépôt forcé par `--wav` sur des archives déjà en ligne mêle donc les deux types, comme avant.

## Ce qui a été écarté

**Mesurer d'abord.** Le porteur l'a écarté : l'audio conservé suffit à lui seul une fois le gain de
vitesse retiré de la balance.

**Refuser la reprise d'un dépôt dont le mode a changé.** Cela obligerait l'utilisateur à comprendre un
réglage pour finir un dépôt qu'il n'a pas modifié.

**Mémoriser le mode dans le plan.** Plus juste à terme, mais c'est une migration de schéma pour une
information que les unités déposées portent déjà.

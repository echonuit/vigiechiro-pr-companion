---
type: adr
title: "Les octets se comptent en base 1000, à un seul endroit"
status: stable
article: A5
chantier: "#3573, suite de la clôture du lot 1 (#3559)"
decided_at: 2026-08-11
verification: certaine
enforced_by:
  - "FormatsTest#octets_lisibles_en_base_1000"
verified:
  - by: machine:ci
    at: 2026-08-11
---

# Les octets se comptent en base 1000, à un seul endroit

## Contexte

Deux formateurs annonçaient des tailles **différentes pour les mêmes octets**, sous la **même
étiquette**. Pour 4 000 000 000 octets : `Formats.octetsLisibles` disait **3,7 Go** (base 1 073 741 824)
et `CompacteurDepot.enGigaoctets` disait **4,0 Go** (base 1 000 000 000). Les deux parlaient de place
disque, et le même utilisateur pouvait voir les deux à cinq minutes d'intervalle.

Le relevé par **diviseurs** - et non par noms de méthode - en a trouvé **sept** en tout : quatre en
base 1024 étiquetées « Mo » (`ExportBiblioSons`, `ExporterSons`, `ExportObservationsEtSons`,
`ExporteurAudio`), deux en base 1000 rendant un nombre nu suivi d'un « Go » écrit à la main
(`FormatsLot`, `MoteurImport`), plus `LotViewModel` qui divisait par un million pour son titre.

`Formats.octetsLisibles` n'avait **aucun test**. Le formateur le plus employé du produit était sans
garde, ce qui est aussi la raison pour laquelle les bases ont pu diverger sans que rien ne le dise.

## Décision

**Base 1000, étiquettes `Ko`/`Mo`/`Go`, et une seule implémentation.**

C'est l'unité dans laquelle les supports sont **vendus** : une carte « 128 Go » porte
128 000 000 000 octets. Or nos chiffres servent précisément à répondre à « est-ce que ça tient sur ma
clé ? ». En base 1024 sous une étiquette « Go », on faisait comparer deux grandeurs différentes portant
le même nom.

Écarté : `Kio`/`Mio`/`Gio`, techniquement exact mais qui ferait comparer des « Gio » à une clé vendue
en « Go » ; et le statu quo (base 1024 étiquetée « Go »), qui conserve une étiquette fausse.

### Une décimale en dessous de dix, aucune au-dessus

Consolider posait une question que la base ne posait pas : les quatre formateurs d'export affichaient
`%.1f Mo`, `Formats` affichait `%.0f Mo`. Aligner sur `Formats` aurait transformé « ~1,5 Mo » en
« ~2 Mo » sur des annonces d'export.

Sous dix, la décimale porte de l'information, et c'est dans cette plage que vivent la plupart de nos
volumes annoncés. Au-dessus, elle n'est plus que du bruit : personne ne décide rien à « 128,3 Go »
qu'il ne déciderait à « 128 Go ».

## Conséquences

- Les valeurs affichées **changent partout**, légèrement : un fichier de 4,5 Gio passe de « 4,2 Go » à
  « 4,5 Go ». Quatorze attendus de tests ont suivi.
- Deux d'entre eux étaient **pliés au formateur plutôt qu'à leur propre fixture** : le compte rendu
  de dépôt posait `4_500_000_000L` et attendait « 4,2 Go ». L'assertion redevient cohérente avec ce
  qu'elle décrit.
- La justification écrite dans `LotViewModel` - « et non `Formats.octetsLisibles` qui raisonne en base
  1024 » - est devenue **fausse** le jour de cette ADR. C'est le motif que la passe 3 traque : un
  commentaire qui décrit un mécanisme remplacé ne rougit nulle part.
- Une conversion d'octets hors de `Formats` est désormais un **défaut** : le contrôle se fait sur les
  **diviseurs**, pas sur les noms de méthode - la prochaine s'appellera autrement.

---
type: adr
title: "Le texte se lit, le JSON se parse : une commande écrit sa date deux fois différemment"
status: stable
article: A16
chantier: "#3990, passe 9 de la clôture des suites des finitions de recette (#3424)"
decided_at: 2026-08-18
verification: certaine
enforced_by:
  - "StatutPassageTest#json_passage_complet"
  - "StatutPassageTest#le_json_garde_les_heures_de_la_base"
  - "ServiceRechercheGlobaleTest#une_nuit_se_cherche_par_la_date_qu_on_lit"
verified:
  - by: machine:ci
    at: 2026-08-18
relations:
  prolonge: ["3828"]
---

# Le texte se lit, le JSON se parse : une commande écrit sa date deux fois différemment

## Contexte

`statut-passage` imprimait `Nuit : 2026-06-15` et `déposé le 2026-06-20` dans son **texte**, alors que
l'écran et deux autres commandes écrivent déjà la date en français. #3950 a francisé le compte rendu
d'import et creusé l'écart.

Le remède évident - tout franciser - casse un contrat. La même commande rend aussi un `--json`, où ces
dates sont des **clés destinées à des scripts**.

## Décision

**La même commande écrit sa date deux fois différemment, et ce n'est pas une incohérence.**

| Sortie | Format | Lecteur |
|---|---|---|
| texte | `15/06/2026` | un humain, qui lit |
| `--json` | `2026-06-15` | un script, qui parse |

Le format d'échange n'est pas une préférence d'affichage : c'est une **interface**. Le franciser
casserait tout consommateur en aval, et le tri lexicographique avec lui.

L'en-tête d'`Horodatage` déclare donc le JSON aux côtés des deux familles qu'elle ne couvrait déjà
pas - les noms de fichiers, qui doivent trier, et la lecture de fichiers tiers, qui doit rester fidèle
au producteur.

## Ce que le garde protège, et qui n'était gardé par rien

Le test du `--json` assertait huit clés et **ni `date` ni `deposeLe`**. Rien n'empêchait quelqu'un de
franciser le JSON par souci de cohérence, et **rien ne l'aurait signalé** : les tests seraient restés
verts, et le premier à s'en apercevoir aurait été l'utilisateur d'un script.

Les deux clés sont désormais assertées. C'est la moitié la plus importante de cette ADR : une décision
de ne **pas** changer quelque chose n'est tenue que si un test rougit quand on la défait.

## Ce que #5996 y ajoute : les heures, et ce qui se cherche

**La règle vaut pour les heures d'une nuit.** `statut-passage` les recopiait de la base, « 21:30:07 → 05:45:52 »,
quand la fiche du passage les dit sans secondes. Sa sortie texte passe par `Horodatage.heureCourte` ; son
`--json` garde la valeur de la base, et un test le tient sur une nuit aux secondes non nulles.

**Les heures réalignées gardent leurs secondes, en texte comme à l'écran.** `metadonnees-passage` et le compte
rendu d'envoi disent les heures d'une nuit avant et après leur réalignement sur les enregistrements. Ce
réalignement peut se jouer à la seconde : raccourcies, « 21:30:00 → 21:30:07 » se liraient « 21:30 → 21:30 »,
et la correction ne se verrait plus. Qui voudrait « finir le travail » y perdrait ce que la phrase sert à dire.

**Ce qui s'affiche se cherche.** La recherche globale dit la date d'une nuit en français, et la trouve par cette
forme comme par la forme ISO. Franciser l'affichage seul aurait fait taper à l'utilisateur ce qu'il lit sans
rien trouver ; retirer l'ISO aurait cassé une requête que des habitués tapent.

## Ce qui reste délibérément hors de cette décision

Deux commandes portent, sous le même nom `dateDebut`, des **datetimes avec décalage** venus de l'API
(`2026-07-03T22:00:00+02:00`) : `reconstruire-passage` et `lister-participations-vigiechiro`.

**`Horodatage.dateSeule` ne les parse pas et les rendrait inchangés, en silence** - un correctif qui
se présente en succès sans rien corriger. Et le fond y est autre : afficher un instant portant un
décalage est une décision de **fuseau**, pas de format, sur un terrain qui a déjà mordu
([ADR 3406](3406-une-nuit-porte-le-fuseau-de-son-site.md) : trois machines, trois instants envoyés).

Les traiter demande de décider dans quel fuseau la ligne de commande rend un instant distant. C'est une
question ouverte, pas un oubli.

## Alternatives écartées

- **Tout franciser, texte et JSON.** Cohérent en apparence, et casse les scripts.
- **Tout laisser en ISO.** C'est l'état d'avant, où l'utilisateur lit un format de base de données dans
  une interface en français.
- **Un drapeau `--format`.** Une option pour choisir entre deux formats déplace la décision chez
  l'appelant, qui n'a aucune raison de l'avoir. Les deux sorties ont déjà leur lecteur ; c'est ce qui
  décide, pas un réglage.

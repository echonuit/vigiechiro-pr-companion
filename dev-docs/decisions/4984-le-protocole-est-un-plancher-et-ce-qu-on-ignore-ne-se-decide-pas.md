---
type: adr
title: "Le protocole est un plancher : le respecter est un fait, et ce qu'on ne peut pas savoir ne se décide pas"
status: stable
article: A12
heuristiques:
  - "nielsen-1"
chantier: "sous-chantier #4984, lot 3 du chantier #4980"
decided_at: 2026-09-01
verification: certaine
enforced_by:
  - "AnalyseCoherenceHoraireTest#commencer_avant_le_coucher_respecte_le_protocole"
  - "AnalyseCoherenceHoraireTest#la_marge_du_protocole_vaut_trente_minutes"
  - "PariteCoherenceHoraireTest#les_deux_surfaces_tranchent_dans_le_meme_sens"
verified:
  - by: machine:ci
    at: 2026-09-01
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-01
---

# Le protocole est un plancher : le respecter est un fait, et ce qu'on ne peut pas savoir ne se décide pas

## Contexte

Le protocole Vigie-Chiro Point Fixe demande d'enregistrer **au moins** de 30 minutes avant le coucher
du soleil à 30 minutes après son lever. Le modèle lisait cette exigence à l'envers : il alertait
quand l'enregistrement **sortait** de la nuit astronomique, c'est-à-dire précisément quand
l'observateur faisait ce que le protocole demande. Trois documents enseignaient la règle inversée, et
l'écran comme la commande la restituaient fidèlement.

La marge n'est pas un défaut à corriger : elle produit une portion d'enregistrement en plein jour, et
c'est le but, les premières chauves-souris sortant avant la nuit complète et les dernières rentrant
après l'aube.

## Décision

**1. La fenêtre exigée se calcule, elle ne se devine pas.** `AnalyseCoherenceHoraire` dérive
`coucher - 30 min` et `lever + 30 min` des éphémérides au point d'écoute. `MARGE_DU_PROTOCOLE` est une
**constante nommée du protocole**, portée là où la règle métier vit. La nommer « tolérance » serait
déjà la trahir : ce n'est pas une marge d'erreur qu'on s'accorde, c'est ce que le programme exige.

**2. Couvrir la fenêtre est un fait, jamais un défaut.** Une nuit qui couvre l'exigence, et la
dépasse, rend une **information**. Aucune valeur ne distingue « couverte tout juste » de « couverte et
dépassée » : l'égalité à la seconde près n'arrive pas, et un niveau qu'aucune nuit n'atteint serait un
niveau mort.

**3. L'énumération du modèle nomme un état du domaine, jamais une gravité.**
`CoherenceHoraire.Couverture` vaut `COUVERTE`, `INCOMPLETE` ou `INDISPONIBLE`. La gravité se décide
**à la surface**, où l'on parle à quelqu'un : `DiagnosticViewModel.libelleEcart` en fait une
information ou un avertissement, `Diagnostiquer.coherenceLisible` en fait une phrase.

Le premier jet nommait ces valeurs `INFORMATION` et `AVERTISSEMENT`, et les deux surfaces les
convertissaient une pour une. C'était une **seconde échelle de sévérité dans le modèle**, exactement
ce contre quoi l'amendement de l'[ADR 0038](0038-l-echelle-de-severite-a-quatre-niveaux.md) met en
garde : « la fuite peut aller vers une seconde énumération, dans une autre couche, qui règle le même
problème sans le savoir ; celle-là ne se trouve pas en cherchant des glyphes, elle se trouve en
cherchant des **synonymes** ». Le défaut a été trouvé par la passe 0 de cette clôture, en relisant
cette ADR-là, et non par un garde.

**4. Le troisième niveau n'est pas livré, et c'est dit plutôt que simulé.** Une nuit **interrompue en
son milieu** est plus grave qu'une nuit trop courte. Elle n'est pas rendue, parce que la donnée
n'existe pas là où le diagnostic la lirait : la complétude d'un cycle est consommée **au seul import**
et n'est persistée dans aucune colonne. Le lot #5030 la persistera.

## Ce que la mesure a refusé

**Déduire une interruption d'un intervalle sans enregistrement.** Une nuit calme et une nuit
interrompue s'y ressemblent, et le dépôt n'a qu'**une seule nuit réelle, dix-huit fois recopiée** :
aucune population sur laquelle asseoir le seuil qui les séparerait. Un seuil posé sans population
aurait produit un troisième niveau qui se déclenche au hasard, ce qui est pire que son absence.

**Passer par les anomalies déjà persistées.** Elles viennent d'`AnalyseurLogPR`, un autre chemin, et
aucun de ses filtres ne reconnaît un motif de troncature. Le détour était plausible, il ne tenait pas.

## Conséquences

L'absence d'avertissement **ne prouve pas** qu'une nuit est entière : le journal du capteur est
circulaire (R19) et peut avoir effacé l'interruption. L'écran le dit, et aucune prose du dépôt ne doit
laisser entendre le contraire.

L'information se déclenche sur **la plupart** des nuits, si les observateurs suivent le protocole avec
de la marge. Elle n'est pas un défaut et ne se présente pas comme tel : encart bleu et pictogramme
d'information, distincts de l'ambre et du triangle de l'avertissement
([ADR 0038](0038-l-echelle-de-severite-a-quatre-niveaux.md) point 3).

La clé JSON `couvertureDuProtocole` de `diagnostiquer --json` porte les trois valeurs du domaine. Un
script qui veut alerter teste `INCOMPLETE`, et **jamais** l'absence de `COUVERTE` : `INDISPONIBLE` ne
signifie pas « mauvais » mais « on n'a pas pu savoir ».

## Ce que cette décision ne couvre pas

`ServiceQualification` mesure **aussi** une couverture horaire, contre les heures **déclarées** du
passage et non contre les éphémérides, avec son propre vocabulaire. Les deux mesures coexistent et
peuvent se contredire sur la même nuit. Le rapprochement est ouvert en #5055, et il n'appartient pas à
cette décision : celle-ci dit ce qu'est la fenêtre exigée, pas qui a le droit de la mesurer.

---
type: adr
title: "Adopter un arbre amont quand il doit parler notre cycle, et le sortir de ses exemptions"
status: stable
article: A5
chantier: "#4515, chantier #4511 (mise en service d'OpenSpec)"
decided_at: 2026-08-26
verification: certaine
relations:
  amendee_par: ["4516-une-commande-nomme-un-geste"]
enforced_by:
  - "scripts/adr/2843-tiret-cadratin.py"
  - "scripts/adr/4366-avertissement-en-pictogramme.py"
  - "scripts/methode/verifie-sous-commandes-openspec.py"
verified:
  - by: machine:ci
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
---

# Adopter un arbre amont quand il doit parler notre cycle, et le sortir de ses exemptions

!!! warning "Ce qui fait foi aujourd'hui"
    **Amendée le 2026-08-26** par [ADR 4516](4516-une-commande-nomme-un-geste.md) : les six commandes `opsx` ne restent pas
    amont, elles disparaissent. Six relais d'une ligne, nommés par le geste, les remplacent,
    et l'adoption des compétences est désormais gardée contre `openspec update --force`.

## Contexte

L'[ADR 4339](4339-un-arbre-repris-d-un-outil-amont-se-declare.md) a posé la règle : ce qui vient d'un
outil amont ne se réécrit pas, l'exemption se déclare nominativement, avec sa raison et la mesure qui
la prouve.

Le chantier #4511 a rencontré sa limite. Les six compétences OpenSpec décrivaient un cycle qui ignore
celui du dépôt : ni bloc d'ouverture, ni issue à assigner, ni boucle rouge-vert-refactor, ni passes
de clôture. Le canal prévu pour y injecter ces conventions sans toucher au texte est
`openspec/config.yaml`, et le typage de la version épinglée le borne à quatre clés d'artefact et deux
opérations.

**Aucun point d'accroche pour `propose` ni pour `explore`**, c'est-à-dire pour les deux gestes
d'ouverture, qui sont ceux où le cycle du dépôt a le plus à dire. La doctrine devait entrer dans les
fichiers que l'agent lit au moment du geste, ou n'exister nulle part.

## Décision

**Un arbre amont s'adopte quand il doit parler notre cycle, et l'adoption le sort de ses
exemptions.**

L'ADR 4339 n'est pas renversée : sa règle vaut pour ce qui reste amont. Ce qui s'ajoute est le motif
du passage. Un arbre amont se réécrit lorsque **la convention à y inscrire n'a aucun autre canal**,
jamais parce que sa typographie déplaît.

L'adoption est un tout et se paie en une fois : le texte se réécrit en français, tissé au cycle ; les
exemptions disparaissent nominativement ; la provenance se déclare en en-tête par `origine:` ; et un
dispositif garde ce que la réécriture met en jeu.

**Ce qui reste en anglais est un contrat**, pas de la prose : en-têtes structurels, `SHALL`, `MUST`,
noms de sous-commandes et de champs JSON. Les traduire ferait échouer la lecture de ce que l'outil
rend. La frontière se compte plutôt qu'elle ne se sent : neuf invocations, quinze champs.

**L'adoption se fait par famille.** Les six compétences sont adoptées ; les six commandes `opsx`
restent amont avec leur exemption. Adopter ce qu'on ne réécrit pas encore laisserait un fichier sans
exemption et sans raison d'en être sorti.

## Trois questions décident, dans cet ordre

**La convention a-t-elle un autre canal ?** Si la configuration peut la porter, elle la porte. Ici le
typage a été lu, pas supposé.

**Le texte a-t-il encore une source vivante ?** Un fichier adopté ne se régénère plus : `openspec
update` le rendrait à l'anglais. Ce risque s'assume et se garde.

**Ce qui reste en anglais se compte-t-il ?** Une frontière décrite en mots dérive ; chiffrée, elle se
vérifie.

## Alternatives écartées

**Injecter par la configuration.** Écartée par la mesure : `rules` n'accepte que `proposal`, `specs`,
`design` et `tasks`. Une clé pour `propose` n'échouerait même pas, elle avertirait et se perdrait en
silence.

**Une compétence maison à côté, les six intactes.** Moins de travail et moins de dette, mais la
doctrine reste à côté du geste au lieu d'être dedans. Rien ne garantit qu'un agent l'ouvre au bon
moment, et une cérémonie qu'on peut ne pas voir est la première à sauter.

**Adopter les douze d'un coup.** Écartée par la mesure : les commandes ne dérivent pas des
compétences, `archive` différant sur 67 lignes et `explore` sur 130. Les rendre dérivées aplatit un
texte amont, et mérite son propre arbitrage.

## Conséquences

Six fichiers de méthode de plus, en français, que le dépôt maintient. Ils entrent sous cinq régimes :
tolérance zéro cadratin, apostrophe ASCII, avertissement dit en mots, registre éditorial, humaniseur.

La table des exemptions de `2843-tiret-cadratin.py` passe de neuf entrées à trois, et le préfixe
`REPRIS` de `4366` de trois à un. Ce qui reste ne nomme que les commandes.

**Le dépôt hérite d'un risque qu'il n'avait pas** : ses compétences peuvent décrire un contrat de
ligne de commande périmé, en silence, si l'outil monte de version.
`scripts/methode/verifie-sous-commandes-openspec.py` (#4514) est le prix de l'adoption, écrit avant
elle pour tenir la réécriture pendant qu'elle se faisait.

Une montée de version cesse d'être un `npm update` : elle demande de relire les six compétences
contre le nouveau contrat, ce que le garde de version (#4512) rend visible.

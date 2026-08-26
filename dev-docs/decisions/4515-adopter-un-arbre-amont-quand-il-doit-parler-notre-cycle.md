---
type: adr
title: "Adopter un arbre amont quand il doit parler notre cycle, et le sortir de ses exemptions"
status: stable
article: A5
chantier: "#4515, chantier #4511 (mise en service d'OpenSpec)"
decided_at: 2026-08-26
verification: certaine
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

## Contexte

L'[ADR 4339](4339-un-arbre-repris-d-un-outil-amont-se-declare.md) a posé la règle : ce qui vient d'un
outil amont ne se réécrit pas, l'exemption se déclare nominativement, avec sa raison et la mesure qui
la prouve. Elle couvrait six compétences OpenSpec, leurs six adaptateurs et six commandes, pour
dix-sept cadratins qu'il aurait été vain de corriger puisque la mise à jour suivante les aurait
rendus.

Le chantier #4511 a buté sur la limite de cette règle. Les six compétences décrivent un cycle qui
ignore celui du dépôt : ni bloc d'ouverture, ni issue à assigner, ni boucle rouge-vert-refactor, ni
passes de clôture. Le canal prévu pour injecter ces conventions sans toucher au texte amont est
`openspec/config.yaml`, et le typage de la version épinglée le borne à quatre clés d'artefact et deux
opérations, `apply` et `archive`.

**Il n'existe aucun point d'accroche pour `propose` ni pour `explore`**, c'est-à-dire pour les deux
gestes d'ouverture, qui sont précisément ceux où le cycle du dépôt a le plus à dire. La doctrine de
l'ouverture ne pouvait donc pas être injectée. Elle devait entrer dans les fichiers que l'agent lit
au moment du geste, ou n'exister nulle part.

## Décision

**Un arbre amont s'adopte quand il doit parler notre cycle, et l'adoption le sort de ses
exemptions.**

L'ADR 4339 n'est pas renversée : sa règle vaut toujours pour ce qui reste amont. Ce que cette
décision ajoute, c'est le motif qui fait passer un fichier de l'autre côté. Un arbre amont se
réécrit lorsque **la convention à y inscrire n'a aucun autre canal**, et non parce que sa
typographie déplaît.

L'adoption est un tout, et se paie en une fois :

- le texte se réécrit en français, tissé au cycle du dépôt ;
- les exemptions du fichier disparaissent, **nommément**, et il entre sous les régimes ordinaires ;
- la provenance se déclare dans son en-tête, `origine:` disant d'où il vient et quelle ADR l'a
  adopté ;
- un dispositif garde ce que la réécriture met en jeu.

**Ce qui reste en anglais est un contrat, pas de la prose.** Les en-têtes structurels, `SHALL`,
`MUST`, les noms de sous-commandes et les noms de champs JSON rendus par l'outil. Les traduire ferait
échouer la lecture de ce que la ligne de commande rend. La frontière se mesure plutôt qu'elle ne se
sent : neuf invocations et quinze noms de champs.

**L'adoption se fait par famille, pas d'un bloc.** Les six compétences sont adoptées ; les six
commandes `opsx` restent amont et gardent leur exemption, jusqu'au lot qui les rendra dérivées des
compétences. Adopter ce qu'on ne réécrit pas encore laisserait un fichier sans exemption et sans
raison d'en être sorti.

## Comment on sait qu'une adoption est légitime, et pas un prétexte

Trois questions, dans cet ordre.

**La convention a-t-elle un autre canal ?** Si la configuration de l'outil peut la porter, elle la
porte. Ici, le typage de la version épinglée a été lu avant de conclure, et non supposé.

**Le texte amont a-t-il encore une source vivante ?** Une fois adopté, un fichier ne se régénère
plus : `openspec update` le rendrait à l'anglais. Ce risque s'assume et se garde, il ne se découvre
pas.

**Ce qui reste en anglais se compte-t-il ?** Une frontière décrite en mots dérive ; une frontière
chiffrée se vérifie.

## Alternatives écartées

**Injecter la doctrine par la configuration.** Écartée par la mesure : `rules` n'accepte que
`proposal`, `specs`, `design` et `tasks`, `operations` que `apply` et `archive`. Une clé pour
`propose` n'échouerait même pas, elle avertirait et se perdrait en silence.

**Écrire une compétence maison à côté, en laissant les six intactes.** C'est le moins de travail et
le moins de dette, mais la doctrine reste à côté du geste et non dedans. Rien ne garantit qu'un agent
l'ouvre au moment où il en a besoin, et une cérémonie qu'on peut ne pas voir est la première à
sauter.

**Adopter les douze fichiers d'un coup.** Écartée par la mesure, pas par prudence : les commandes ne
dérivent pas des compétences. `archive` diffère sur 67 lignes de contenu réel et `explore` sur 130,
la compétence portant 4 Ko de plus. Les rendre dérivées est une décision éditoriale qui aplatit un
texte amont, et elle mérite son propre arbitrage.

## Conséquences

Le dépôt porte désormais six fichiers de méthode de plus, en français, qu'il maintient lui-même. Ils
entrent sous cinq régimes : tolérance zéro cadratin, apostrophe ASCII, avertissement dit en mots,
registre éditorial, humaniseur.

La table des exemptions de `2843-tiret-cadratin.py` passe de neuf entrées à trois, et le préfixe
`REPRIS` de `4366-avertissement-en-pictogramme.py` de trois à un. Ce qui reste nomme les seules
commandes.

**Le dépôt hérite d'un risque qu'il n'avait pas** : ses six compétences peuvent décrire un contrat de
ligne de commande périmé, en silence, si l'outil monte de version.
`scripts/methode/verifie-sous-commandes-openspec.py` (#4514) est le prix de cette adoption, écrit
**avant** elle pour tenir la réécriture pendant qu'elle se faisait, et non pour la constater après.

Une montée de version d'OpenSpec cesse d'être un `npm update`. Elle demande de relire les six
compétences contre le nouveau contrat, ce que le garde de version (#4512) rend visible en comparant
la version du lockfile au `generatedBy` déclaré.

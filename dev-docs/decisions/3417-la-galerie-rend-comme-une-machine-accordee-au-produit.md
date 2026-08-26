---
type: adr
title: "La galerie rend comme une machine accordée au produit"
status: stable
article: A4
chantier: "#3417, suite de l'[ADR 3389](3389-ce-que-l-application-affiche-tient-dans-la-police-embarquee.md)"
decided_at: 2026-08-06
verification: humaine
loupe:
  - "capture-vues.yml"
verification_note: "l'étape « Aligner la police système » de `capture-vues.yml` journalise"
verified:
  - by: human:nedseb
    at: 2026-08-06
---

# La galerie rend comme une machine accordée au produit

## Contexte

Le produit **embarque** sa typographie et la demande par `base.css` ([ADR 3361](3361-la-typographie-est-embarquee.md),
[3374](3374-une-fenetre-porte-son-habillage-ou-elle-n-est-pas-le-produit.md),
[3412](3412-un-alias-n-est-pas-une-police.md)). Il ne dépend donc plus de la machine pour **peindre**.

Il en dépend encore pour **mesurer**. Instrumentation du runner (#3417) :

| Texte | Noto Sans | police système du runner | police système d'un poste |
| --- | --- | --- | --- |
| « 1 heure » | 46,92 px | **50,22 px** | 46,92 px |

`ChoiceBoxSkin` - et tout skin qui calcule sa taille avec un nœud de texte **hors du graphe de
scène** - mesure avec `Font.getDefault()`, à laquelle aucune feuille de style ne s'applique : le nœud
mesuré n'est pas dans la scène. Le contrôle est donc **mesuré dans une police et peint dans une
autre**, et il fait 82 px là où un poste en donne 78. Le graphique en dessous hérite du décalage :
6,67 % des pixels de l'écran.

**Le remède évident ne marche pas**, testé en CI : invalider la taille puis refaire une passe
`applyCss()` + `layout()` laisse 82 px avant comme après. Aucune passe n'atteint un nœud absent de la
scène.

## Décision

**La police système du runner est alignée sur celle que l'application déclare.** Le job de capture
installe `fonts-noto-core` et configure fontconfig pour que `sans-serif` et `system-ui` résolvent vers
`Noto Sans`, `monospace` vers `Noto Sans Mono`.

### Pourquoi ce n'est pas « discipliner la CI » au sens où on l'avait écarté

L'[ADR 3389](3389-ce-que-l-application-affiche-tient-dans-la-police-embarquee.md) écartait cette piste :
« installer une police sur le runner rendrait la galerie stable en laissant les utilisateurs
divergents ». C'était vrai et à côté de la question.

**Il n'existe pas UN rendu utilisateur.** Il dépend de la police système de chacun, que rien ne nous
laisse fixer. La galerie rendait donc comme *personne en particulier* : une machine où la mesure et la
peinture se contredisent, ce qu'aucun utilisateur ne voit puisque chez lui les deux polices sont au
moins cohérentes entre elles.

La seule référence **non arbitraire** est celle où mesure et peinture coïncident, c'est-à-dire une
machine dont la police système **est** celle que l'application déclare. C'est ce que la galerie montre
désormais - et, accessoirement, ce que voit tout utilisateur d'un poste Linux courant, où
`sans-serif` résout déjà vers Noto Sans.

## Conséquences

- **la revue visuelle redevient transportable** : un rejeu local et la CI rendent la même chose, donc
  le verdict du garde de troncature cesse de dépendre de la machine qui l'a rendu ;
- `fc-match` est **journalisé** à chaque exécution : le jour où l'image du runner change de police par
  défaut, la trace le dira au lieu de laisser 12 aperçus dériver en silence ;
- **ce n'est pas un correctif du produit.** Chez un utilisateur dont le système résout `System`
  autrement, ce `ChoiceBox` reste plus large de 4 px. Le défaut est cosmétique et il est **connu** ;
  le figer dans les FXML (`prefWidth`) supposerait de trouver tous les contrôles concernés et de
  choisir une largeur qui tienne dans toutes les langues. Non fait, assumé.

## Alternatives écartées

- **Figer la largeur des contrôles** dans les FXML : corrige aussi chez l'utilisateur, mais demande un
  inventaire exhaustif des skins qui mesurent hors du graphe - et une largeur en dur est une dette
  qu'une traduction rouvre ;
- **Exclure ces écrans de la comparaison** comme `apercu-a-propos` (version du JDK) et
  `apercu-reglages-emplacements` (nom de l'utilisateur système) : légitime pour du **contenu**
  machine, pas pour du **rendu** - on s'aveuglerait sur une vraie régression au même endroit.

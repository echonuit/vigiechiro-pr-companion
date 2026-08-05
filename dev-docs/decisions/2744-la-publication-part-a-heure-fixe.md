# ADR 2744 - La publication part à **heure fixe**, pas à chaque fusion

- **Statut** : Accepté - 2026-08-05
- **Chantier** : #2744, lot 3 (#2723) du chantier de dette #2720
- **Vérification** : humaine - le premier train du mercredi suivant, dont les notes doivent agréger la
  semaine et non un seul commit

## Contexte

`release.yml` partait à **chaque** fusion sur `main`. Conséquence mécanique, mesurée le 2026-08-05 :

- **497 tags** depuis mai ;
- sur les quatorze derniers jours : `13, 8, 1, 2, 31, 16, 30, 22, 24, 19, 4, 8, 25, 2` - jusqu'à
  **31 versions dans la même journée** ;
- une seule journée de travail soutenu en produit **25**.

Le premier réflexe serait de dire que ces versions sont vides. **C'est faux, et c'est important** : sur
les 120 dernières, **76 `feat` et 38 `fix`** - 95 % portent un changement réel.

Le défaut n'est donc pas le bruit, c'est l'**atomisation** : une version = un changement. Il en découle
trois choses.

1. **Aucune version n'est validable.** La recette finale (#1363) est un travail humain ; elle ne peut
   pas passer sur 25 versions par jour. Une version qu'on ne peut pas valider n'est pas une version,
   c'est un instantané.
2. **Aucune version n'est descriptible.** Ses notes tiennent en une ligne. L'utilisateur qui suit les
   Releases voit défiler des numéros, pas des états.
3. **La cadence a déjà pesé sur une décision, en aval.** Les soumissions **winget** et **Flathub**
   sont en déclenchement **manuel**, et `flatpak.yml` compte la cadence parmi ses raisons : « ce dépôt
   publie entre 3 et 37 fois par jour, soit autant de constructions complètes du SDK Flathub et autant
   de commits de mise à jour sur `main` ».

   ⚠️ **Cette raison-là n'est pas la principale, et il faut le dire pour ne pas se tromper de
   conclusion** : le paquet n'est **pas encore accepté** sur Flathub (#2191), et le même fichier note
   qu'un déclenchement sur `release: released` ne partirait de toute façon **jamais** (l'événement est
   produit avec le `GITHUB_TOKEN`, qui ne déclenche aucun workflow). Les canaux ne sont donc pas « en
   retard » : ils n'ont **rien à distribuer** pour l'instant.

## Décision

**Un train de publication hebdomadaire**, le **mercredi à 6 h UTC**, plus le déclenchement manuel.

```yaml
on:
  schedule:
    - cron: "0 6 * * 3"
  workflow_dispatch: {}
```

Le déclenchement `push` sur `main` est retiré.

### Pourquoi mercredi

Ni lundi - la semaine n'a encore rien produit - ni vendredi : un défaut découvert le vendredi soir
attend le lundi, et l'installeur est chez les gens entre-temps. Un train du mercredi a **deux jours
ouvrés** devant lui.

### Pourquoi `workflow_dispatch` reste

Un correctif urgent n'attend pas le train. Le garder ne rouvre pas la porte à l'atomisation : c'est un
geste **délibéré**, pas un effet de bord d'une fusion.

### Ce que la décision ne change pas

Rien à l'outillage : `semantic-release` calcule la version et les notes comme avant, à partir des
mêmes commits, avec les mêmes `parserOpts`. Un train qui accumule sept `feat` publie une `minor` dont
les notes en listent sept - c'est le comportement normal de l'outil, il n'était simplement jamais
exercé.

## Conséquences

- **Un argument tombe pour les canaux de packaging, mais rien n'y change aujourd'hui.** La cadence
  était l'une des raisons du déclenchement manuel de `winget.yml` et `flatpak.yml` ; à une version par
  semaine, elle ne tient plus. Les autres raisons, elles, demeurent : le paquet n'est **pas accepté**
  sur Flathub (#2191), et après acceptation ce sont les **robots de Flathub** qui prendront le relais
  des montées de version, toutes les deux heures - notre workflow restera utile pour ce qu'ils ne font
  pas, attester que le paquet **démarre**. Il n'y a donc rien à rebrancher, ni maintenant ni
  probablement plus tard.
- **La recette (#1363) devient jouable** : un train par semaine, c'est une version à valider par
  semaine.
- **Les numéros de version ralentissent.** `2.181.0` aujourd'hui ; le rythme passera de ~20 mineures
  par semaine à une ou deux. Aucun outil n'en dépend - la montée de version de l'EPIC #2104 compare des
  numéros, elle ne suppose rien de leur fréquence.
- **Un mercredi sans rien à publier ne publie rien** : `semantic-release` ne crée pas de version sans
  commit qui la justifie, et le job des installeurs est déjà conditionné au tag (`if: tag != ''`).
- ⚠️ **Le délai entre fusion et disponibilité passe de quelques minutes à sept jours au pire.** C'est
  le prix assumé, et c'est exactement ce que `workflow_dispatch` rachète quand il le faut.

## Alternatives écartées

- **Deux canaux** (`main` en continu, `stable` validée) : le plus riche, mais il demande de tenir deux
  branches et deux fils de version pour un produit qui a un mainteneur.
- **Déclenchement manuel seul** : le contrôle est total, mais rien ne sort si personne n'y pense -
  et la dérive silencieuse est justement ce qu'on corrige.
- **Statu quo** : il a déjà coûté le débranchement de deux canaux de distribution.

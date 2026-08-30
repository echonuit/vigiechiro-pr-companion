---
type: adr
title: "Un cas rouge pour la mauvaise raison ne prouve rien, et un auto-test doit dire lequel de ses contrôles a rougi"
status: stable
article: A2
chantier: "#4918 (chantier #4946, sous #4828)"
decided_at: 2026-08-31
verification: humaine
loupe: "aucun motif ne lit si un cas rougit pour la bonne raison : la question se pose à l'écriture de chaque auto-test portant plus d'un contrôle"
verified:
  - by: humain
    at: 2026-08-31
---

# Un cas rouge pour la mauvaise raison ne prouve rien, et un auto-test doit dire lequel de ses contrôles a rougi

## Contexte

Le dépôt sait qu'un test peut être **vert** pour la mauvaise raison : l'ADR 3222 le mesure sur un
corps vide, l'ADR 3651 sur des requêtes qui partent de la mauvaise racine.

Le cas **miroir** n'était écrit nulle part, et il a mordu deux fois le 30 août 2026.

## Les deux incidents, le même jour

**`verifie-chantier-de-l-issue.sh`.** Cinq mutations, cinq « tuées ». Deux d'entre elles cassaient la
syntaxe du `case` : le script mourait **avant de juger**, et l'auto-test rendait « échec » sans avoir
rien détecté. Deux mutations sur cinq ne prouvaient rien, et cela ressemblait à un succès.

**`concordances-du-cycle.py`.** Six mutations, **quatre survivantes**. En déboguant, la cause :
l'auto-test ne lisait que « rouge ou non ». Le cas qui devait éprouver la première concordance
détournait une commande, ce qui rendait une compétence orpheline dans le bac : il rougissait bien,
**par la quatrième concordance**, et ne prouvait rien de la première.

## Le défaut, et pourquoi il se déguise en succès

Un auto-test qui n'observe que le **code de sortie** confond « ce contrôle a vu le défaut » et
« quelque chose est allé de travers ». Les deux rendent la même couleur.

C'est le pire cas d'un dispositif qui atteste son propre nom : il rougit, on le croit, et la
mutation qu'on lui a soumise n'a été vue par personne.

## Décision

**Un cas d'auto-test déclare ce qu'il doit voir, et l'assertion porte sur ce que le garde écrit**,
non sur son seul code de sortie.

Deux conséquences, tirées des deux incidents :

**Une mutation qui casse la syntaxe est rejetée, pas comptée.** Un banc de mutation passe le candidat
à un contrôle syntaxique avant de le lancer : `bash -n` pour du shell, `ast.parse` pour du Python. Un
script qui meurt avant de juger n'a rien jugé.

**Un auto-test qui porte plus d'un contrôle dit lequel a rougi.** Chaque cas nomme le message
attendu, et un cas qui en déclenche deux est un cas mal isolé, à corriger et non à accepter.

## Conséquences

**La mutation devient discriminante.** Sur `concordances-du-cycle.py`, l'assertion sur le message a
d'abord fait échouer deux cas qui passaient : l'un rougissait par une seconde concordance, l'autre
annonçait la première de deux passes et ne distinguait donc pas « appartient à la liste » d'« égale
le premier élément ». Les deux étaient de vraies lacunes, invisibles au verdict seul.

**Le coût est faible et se paie une fois.** Nommer le message attendu par cas coûte une chaîne ; le
découvrir plus tard coûte la confiance qu'on avait placée dans le garde.

**Cela ne s'applique qu'aux auto-tests portant plus d'un contrôle.** Un garde à contrôle unique ne
peut pas rougir pour la mauvaise raison de cette manière-là.

## Alternatives écartées

- **Compter les cas rouges plutôt que lire leurs messages.** C'est ce que faisait la première version
  de `concordances-du-cycle.py` : le compte était juste, et quatre mutations y survivaient.
- **Se fier à la mutation seule pour révéler le défaut.** Elle l'a révélé, mais seulement parce que
  quelqu'un a débogué un survivant au lieu de le classer équivalent. Une mutation survivante se lit,
  et c'est justement ce qu'on ne fait pas quand on est pressé.

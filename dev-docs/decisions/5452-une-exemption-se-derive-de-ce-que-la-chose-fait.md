---
type: adr
title: "Une exemption se dérive de ce que la chose fait, jamais de la forme de son écriture"
status: stable
article: A2
chantier: "#5452 (un dispositif déclare une portée plus large que celle qu'il tient)"
decided_at: 2026-09-28
verification: certaine
enforced_by:
  - "scripts/adr/verifie_temoins_non_decoratifs.py"
  - "scripts/methode/releve-les-harnais-muets.py"
verified:
  - by: machine:ci
    at: 2026-09-28
relations:
  complete: ["5398-une-exemption-se-declare-elle-ne-s-infere-pas", "4490-un-temoin-se-prouve-par-mutation-mecaniquement"]
generated:
  by: "process:assistance-par-agents"
---

# Une exemption se dérive de ce que la chose fait, jamais de la forme de son écriture

## Le contexte

Un dispositif qui juge une population doit en écarter une part : un banc de mutation épargne la
machinerie de l'auto-test, un lot de conversion ne reprend que les harnais fragiles. Ce chantier a
trouvé **quatre** exemptions et populations reconnues à un **idiome d'écriture**, et les quatre
laissaient passer ce qu'elles prétendaient couvrir.

| dispositif | reconnaissait à | ce qui échappait |
|---|---|---|
| banc ADR | un souligné initial | une détection nommée `_completude`, `_forge`, `_decoupe` |
| bancs méthode et CI | un nom portant « auto » et « test » | `porte_son_auto_test`, `auto_test_rougit`, `porte_un_auto_test`, `autotestes` |
| lot #5460 | une définition locale de `verifie` | `scripts/batterie.py` |
| lot #5461 | `cas.append((libellé, expression))` | la même |

## La mesure qui décide

Dix-neuf détections étaient nommées avec un souligné, donc hors d'atteinte de la mutation. Mutées une
à une : **sept** faisaient rougir leur auto-test, sept couvertures réelles que le préfixe cachait, et
**six** survivaient, révélant des auto-tests qui n'éprouvent pas ce qu'ils semblent éprouver.

Une fois l'exemption dérivée du graphe d'appel, **119 fonctions** sont entrées dans la portée de la
mutation, pour **un** garde reclassé. Et `scripts/batterie.py`, que deux populations manquaient par
construction, sort **en tête** d'une dérivation qui ne connaît aucun idiome : dix-sept sites.

## La décision

**Une exemption se dérive de ce que la chose FAIT.** Est machinerie ce que seul le point d'entrée
d'auto-test atteint ; est détection ce que la racine du verdict atteint aussi. Est muet un harnais qui
marque son échec sans qu'aucune aide ne détienne le libellé.

Le nom n'est conservé que comme **filtre**, pour que la dérivation ne puisse qu'épargner **moins**
qu'avant. Un changement monotone ne peut pas rendre décoratif un témoin qui tenait.

## Ce que cela ne dit pas, et la frontière avec l'ADR 5398

L'ADR 5398 décide qu'**une exemption se déclare, elle ne s'infère pas**, et elle a raison de son cas :
la porte reconnaissait un garde exigeant des arguments au **premier mot de sa sortie**, surface
incidente qu'une tournure de prose suffisait à imiter.

Les deux décisions ne s'opposent pas, et la frontière est nette :

- **inférer d'une surface incidente** - un préfixe, un motif de nom, le premier mot d'un message -
  reste refusé. C'est ce que 5398 nomme, et ce que ce chantier a trouvé quatre fois ;
- **dériver de la chose elle-même** - le graphe d'appel, ce que le harnais fait de son échec - est
  ce que cette décision prescrit. Ce n'est pas une inférence sur un proxy, c'est la lecture de la
  propriété.

**Et quand aucune capacité n'est lisible, on déclare.** `HORS_PORTEE` reste une table de noms avec
leur raison, parce qu'« ce témoin n'éprouve aucune fonction de module » ne se dérive d'aucun arbre.
Une dérivation impossible n'autorise pas à deviner.

## Les alternatives écartées

- **Renommer les détections pour qu'aucune ne porte de souligné.** Mesuré : 47 fichiers à reprendre,
  et la règle aurait tenu jusqu'au premier garde écrit sans la connaître.
- **Épargner la machinerie par une liste de noms exacts.** Elle couvre le dépôt d'aujourd'hui -
  mesuré, 104 gardes sur 104 exposent leur auto-test sous l'un de trois noms - mais une liste est un
  instantané. Elle est conservée pour les **points d'entrée** seulement, où son vieillissement est
  borné : un point d'entrée muté rend un rouge muet, que le contrôle de #5499 classe « non
  concluant » et jamais « tient ». Le défaut penche du côté bruyant.
- **Refuser sur les harnais muets.** Écarté : convertir un harnais est un travail par garde, et
  refuser sur 74 d'entre eux bloquerait le dépôt sur une dette que le relevé sert à rendre visible.

## Comment on le sait

`scripts/adr/verifie_temoins_non_decoratifs.py` porte un garde fabriqué dont la **seule** détection
s'appelle `_detecte` : il rend « tient » sous la règle dérivée, et « décoratif » sous l'ancienne,
vérifié par simulation avant livraison.

`scripts/methode/releve-les-harnais-muets.py` porte son **témoin négatif**, qui est ce qui le tient :
un harnais entièrement différé ne figure dans aucune des deux formes. Sans lui, un relevé qui
compterait tout le monde passerait tous les autres cas - et c'est exactement ce que deux dérivations
écartées faisaient, l'une en ne lisant que les appels à `verifie` et manquant la porte, l'autre en
désignant 104 harnais sur 104.

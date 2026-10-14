---
type: adr
title: "Un test qui ne filme rien ne cite pas un cas de recette"
status: stable
article: A3
chantier: "#6219, sous-chantier #5645"
decided_at: 2026-10-13
verification: certaine
enforced_by:
  - "PageDesClipsTest#tout_test_qui_cite_un_cas_est_sur_une_page"
  - "RespirationDesClipsTest#tout_cas_filme_s_arrete_au_moins_une_fois"
verification_note: "le premier refuse un test qui cite un cas sans que son clip figure sur une page de clips, le second un test qui cite un cas sans jamais s'arrêter ; tous deux ont rougi le 13 octobre 2026 sur la demande #6224, qui faisait citer S4-70 et S4-71 par un test sans scène"
relations:
  prolonge: ["4142-un-cas-dit-ou-se-lit-son-verdict"]
verified:
  - by: machine:ci
    at: 2026-10-13
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-14
---

# Un test qui ne filme rien ne cite pas un cas de recette

## Contexte

Le sous-chantier #5645 a écrit des tests qui lisent pour de bon, dans un navigateur, ce que
Companion écrit sur la fiche web de la plateforme de test. Ils vérifient exactement ce que demandent
les cas S4-70, S4-71, S4-72 et S4-91 de la recette, que leur marqueur « hors-portée » déclarait sans
clip possible.

Il a paru naturel de les citer par `@CasDeRecette`. Une valeur de portée a été ajoutée pour le permettre
(#6219, livrée par #6222), puis la première demande à s'en servir a rougi sur quatre jobs, et la valeur
a été retirée le même jour (#6226).

Dans ce dépôt, citer un cas veut dire que le test est **filmé** :

- `CorrespondanceRecetteTest` dépose la liste des classes à filmer à partir de toutes celles qui
  citent un cas, et le tournage compare le nombre de ses clips au nombre de cas cités ;
- `PageDesClipsTest` exige que tout test qui cite un cas ait son clip sur une page de clips ;
- `RespirationDesClipsTest` exige qu'il s'arrête au moins une fois, pour que son clip se regarde.

Cette règle n'était écrite que dans le message d'échec du dernier garde. La valeur de #6219 avait été
cartographiée sur les lecteurs de la portée, et non sur ceux de l'annotation.

## Décision

**Un test qui n'affiche aucune scène de l'application ne cite pas de cas par `@CasDeRecette`**, même
s'il vérifie ce que le cas demande de constater.

**Le cas garde son marqueur « hors-portée »**, qui dit ce qui est vrai : il n'y aura jamais de clip. Son
motif nomme le test qui le vérifie (« un test la lit sur la plateforme de test, <classe> »), pour que le
lecteur de la session le trouve.

La javadoc de `@CasDeRecette` le dit depuis #6226, là où celui qui s'apprête à citer la lit.

## Ce qui a été écarté

- **Une portée qui dispense du clip** (#6219). Elle ne pouvait être portée par aucun test sans casser
  le tournage, qui attend un clip par cas cité.
- **Faire produire un clip vide au tournage.** Un lecteur devant lequel il n'y a rien est ce que
  `RespirationDesClipsTest` existe pour empêcher.
- **Apprendre à la chaîne de tournage qu'une citation peut ne pas filmer.** C'est possible : la liste du
  tournage, le compte des clips attendus et trois gardes seraient à reprendre. Le porteur l'a écarté
  pour #5645, et la question est partie en dette dans l'EPIC #4416.

## Conséquences

Le compte des cas couverts de la correspondance ignore les cas vérifiés par un test non filmé. Le
14 octobre 2026, ils étaient 4 sur les 93 marqués « hors-portée ». C'est la dette que porte l'EPIC
#4416, avec sa commande de mesure.

Les annotations existantes ne changent pas : toutes sont portées par des tests filmés.

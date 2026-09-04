---
type: adr
title: "Fusionner en rouge est un choix, fusionner sans couleur est un accident"
status: stable
article: A1
chantier: "#4571 (sas des suites #4562)"
decided_at: 2026-08-26
verification: humaine
loupe:
  - ".github/scripts/verifie_verdict_avant_fusion.py"
verification_note: "le script rend l'état des runs d'un commit de tête et refuse l'absence de verdict, mais rien ne le déclenche : en faire un check requis repaierait le coût mesuré par l'ADR 0041. Il aide à regarder, il ne tient rien."
verified:
  - by: human:nedseb
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
relations:
  complète: ["0041"]
---

# Fusionner en rouge est un choix, fusionner sans couleur est un accident

## Contexte

L'[ADR 0041](0041-un-check-requis-gouverne-la-branche.md) a décidé que les contrôles restent
**informatifs**. La raison était mesurée : rendus bloquants, ils ont cassé en une heure les deux
chemins par lesquels ce dépôt écrit sur `main` - la PR d'aperçus et la chaîne de publication, trois
releases d'affilée. La cause n'a pas bougé : aucun workflow n'est déclenché par un événement produit
avec le `GITHUB_TOKEN`, donc un check requis reste muet sur ce que produit l'automatisation, et un
check requis muet bloque pour toujours.

Cette décision énonce sa contrepartie : « l'information est visible, et l'ignorer est un choix, non
un accident silencieux ».

**Le 26 août, la prémisse a manqué.** #4560 a été fusionnée à 17:13:02Z. Son commit de tête
`909aeafa8`, poussé à 17:01:25Z, ne portait alors **aucun run** : les sept qu'il a fini par avoir
sont nés à 17:15:05-06Z, deux minutes après la fusion, libérés par la fin de la panne Actions du
jour. `main` en est resté rouge sur un garde bloquant, réparé par #4569. Le garde n'avait pas manqué
son travail : personne ne lui avait demandé son avis.

Sur les 40 dernières PR fusionnées, jugées chacune dans l'état où elle était **à l'instant de sa
fusion** : 28 portaient un verdict vert conclu, 11 sont des PR d'aperçus en `[skip ci]` sans run par
construction, et **une seule** a été fusionnée sans rien - #4560. La discipline tient à 28 sur 28 ;
elle n'a cédé qu'où elle n'a aucun appui, le jour où le vert ne pouvait pas arriver.

## Décision

**Une fusion sans verdict complet se refuse ; une fusion en rouge reste un choix.**

Sans verdict **complet** : un seul run conclu ne suffit pas tant que d'autres courent, parce que ce
sont les workflows lents qui portent les gardes bloquants. Exiger le tout ne refuse aucune des 40
demandes mesurées de plus qu'exiger un seul : la pratique du dépôt est déjà celle-là.

Les deux se ressemblent dans leur résultat et ne se corrigent pas pareil. Passer outre un rouge
engage celui qui le fait : il a lu, il a tranché. Fusionner quand rien n'a conclu n'engage personne,
puisqu'il n'y avait rien à lire. L'ADR 0041 a assumé le premier cas ; elle n'avait pas rencontré le
second.

**Le refus vit du côté du contributeur, pas de la forge.** Trois raisons de ne pas en faire un check
requis : le coût est déjà mesuré et le mécanisme de GitHub n'a pas changé ; le blocage ne bloquerait
personne, le mainteneur unique disposant du contournement administrateur ; et ce qui manquait n'était
pas un verrou mais une lecture - le défaut n'est pas qu'on ait pu fusionner, c'est qu'on n'ait pas vu
qu'il n'y avait rien à voir.

**Le garde ne juge pas la couleur**, et le dit à chaque passage vert. Déborder de ce mandat rouvrirait
l'ADR 0041 par la bande.

**Il refuse quand il ne sait pas lire.** Une réponse tronquée ou une erreur d'API le font sortir en
code 2, jamais en vert : la panne qui laisse fusionner sans verdict est celle qui fait répondre la
forge de travers, et un garde qui tombe en marche passante est vert précisément quand il sert. C'est
le défaut de #4544 sous une autre forme.

## Conséquences

**Ce qu'on gagne.** L'écart entre « j'ai vu rouge et je passe outre » et « je n'ai rien vu » devient
visible au moment où il compte, sans toucher à la forge.

**Ce qu'on ne gagne pas.** Rien n'oblige à lancer ce garde : c'est une **loupe**, pas un applicateur,
et sa vérification est déclarée humaine. Même assomption que l'ADR 0041, au même endroit et pour la
même raison.

**Ce qui reste ouvert.** Si le blocage redevient souhaitable, la voie est celle que l'ADR 0041 avait
désignée : un ruleset **d'organisation** restreint à ce dépôt, avec l'application GitHub Actions en
contournement.

**Comment on le voit rouge.** Huit cas, dont quatre contrôles négatifs, tournent dans `lint.yml`.
Cinq mutations le font rougir. Et il a été confronté au cas d'origine : reconstitué depuis l'API à
la minute de la fusion, `909aeafa8` le fait sortir en code 1.

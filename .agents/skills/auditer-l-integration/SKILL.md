---
name: auditer-l-integration
description: Use at closure pass 1, once the ADR re-reading is done, to judge the chantier next to everything merged while it ran. The delta is never filtered on the chantier's own commits, and a guard's exit code is not its verdict.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Auditer l'intégration

## Loi d'airain

```
LE DÉFAUT N'EST PAS DANS VOS COMMITS NI DANS LES LEURS, IL EST DANS LEUR RENCONTRE
```

Filtrer le delta sur les commits du chantier donne un résultat qui a l'air juste, plus petit, et plus
rapide à relire. Il juge un état qui n'a jamais existé : ce qui part en production est le chantier
**à côté** de ce qui a été fusionné pendant qu'il courait.

## Annoncer

« J'utilise la compétence auditer-l-integration sur le delta de <le chantier>. »

## Fonction de garde

```
1. LIRE      le delta ENTIER : <sha d ouverture>..origin/main. Jamais filtre.
2. REBASER   la ou les branches restantes, et resoudre les divergences.
3. CHERCHER  les points d accroche apparus entre-temps qu il faudrait cabler.
4. TRAQUER   les regressions, et les CONVENTIONS apparues depuis l ouverture.
5. LIRE      ce que les gardes ECRIVENT, jamais leur seul code de sortie.
6. CONSIGNER en issue ce qui deborde, au moment ou on le trouve.
```

## Le delta, et pourquoi il ne se filtre pas

```bash
git log --oneline <sha-d-ouverture>..origin/main   # TOUS les commits, toutes sessions
git diff --stat <sha-d-ouverture>..origin/main     # et tout ce qu'ils touchent
```

Le SHA d'ouverture se déduit de la date de l'EPIC quand il n'y est pas tracé :

```bash
git log -1 --format=%H --before="$(gh issue view <EPIC> --json createdAt -q .createdAt)" origin/main
```

**Ce que le filtre cache, mesuré.** À la clôture de #4671, cette passe a trouvé deux planchers
périmés, dont celui de l'arbre de test **posé par une autre session** pendant que le chantier
courait. Un delta filtré n'aurait montré ni le plancher, ni le fait que le travail du chantier
l'avait fait monter sans le verrouiller.

Un delta entier est souvent gros : à la clôture de #4643, 105 commits pour trois du chantier. Ce
n'est pas une raison de le réduire, c'est la raison pour laquelle la passe existe.

## Les conventions apparues sont ce qu'on cherche le moins

Une régression se voit ; une convention nouvelle ne se voit pas, parce qu'elle n'a rien cassé. C'est
elle qui rend un travail non conforme sans le faire rougir.

La question se pose dans les deux sens :

- une convention apparue pendant le chantier **couvre-t-elle** ce qu'il livre ?
- ce qu'il livre entre-t-il dans le **périmètre** de cette convention, ou est-il légitimement dehors ?

Vécu à la clôture de #4643. La PR #4791 avait posé pendant le chantier qu'un auto-test de garde se
prouve par mutation. Le garde livré portait bien un auto-test. Il a fallu ouvrir le banc pour voir
qu'il dérive son corpus de `lint.yml` puis le filtre sur `scripts/methode/*.py` : un garde bash n'y
entre pas. Hors périmètre, donc, et c'est cette lecture qui l'établit, pas la ressemblance.

## Un code de sortie n'est pas un verdict

> `a-relever` rend **1**, comme `perte`.

Un plancher périmé **refuse** (#4683) : le dépôt en porte plus que ce qu'il a verrouillé, et ne pas
relever fait rougir. Ce n'est pas un oubli silencieux.

L'inverse existe aussi. Les cinq loupes du dépôt rendent **0** en signalant, parce qu'elles observent
sans juger, et `rapport.py` nomme séparément les scripts dont il n'a pas su lire le verdict. On lit
donc ce que les gardes écrivent, pour ce qu'un code ne dit pas.

## Ce qui déborde se consigne, au moment où on le trouve

Cette passe est la première à lire le travail des autres, donc la première à trouver ce qui ne lui
appartient pas. Une trouvaille s'ouvre en issue tout de suite, rattachée par `--parent` au chantier
qui traite sa cause, ou au sas des suites. La passe 9 consolide ; elle ne découvre pas.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Je relis le diff de mon chantier » | Le delta est `<ouverture>..origin/main` **entier**. Filtrer cache ce que la rencontre a produit |
| « Le delta est énorme, je filtre » | Sa taille est la mesure de ce qui a bougé, pas une raison de moins regarder |
| « Le garde est sorti en 0, tout va bien » | Les loupes signalent en rendant `0`. On lit ce qu'elles écrivent |
| « Le garde a rougi, j'ai cassé quelque chose » | Un plancher à relever rougit sur un **gain** non verrouillé |
| « Rien n'a cassé, donc rien n'a changé » | Une convention apparue ne casse rien. C'est ce qui la rend invisible |
| « Cette convention ressemble à mon travail » | Ouvrir son périmètre et le lire. La ressemblance n'établit rien |
| « Je noterai cette trouvaille pour la passe 9 » | La 9 consolide, elle ne découvre pas. L'issue s'ouvre maintenant |

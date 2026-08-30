---
name: vider-le-sas
description: Use at closure pass 9, once the visual review is done, to empty the suites EPIC of everything that belongs to the chantier being closed. The pass does not discover findings, it houses them, and it has an exit condition that can send the closure back to earlier passes.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Vider le sas

## Loi d'airain

```
À LA FIN DE LA PASSE, RIEN DU CHANTIER CLOS NE RESTE AU SAS
```

Le sas est une salle d'attente pour ce qui n'a pas encore de maison. Tout ce qui relève du chantier
qu'on clôt en trouve une, et il n'y en a que deux : **livré avant que la clôture conclue** si c'est
dans son périmètre, ou **rattaché à un chantier de suite** si ce n'en est pas.

## Annoncer

« J'utilise la compétence vider-le-sas sur les suites de <le chantier>. »

## Ce qui la distingue du triage d'ouverture

`trier-les-issues` décide **s'il y a lieu d'ouvrir**. Elle balaie ce qui existe pour éviter d'ouvrir
un doublon, et son verdict est un jugement d'opportunité.

Cette passe-ci **vide**, et son verdict est binaire : ou bien plus rien du chantier ne pend au sas,
ou bien la passe n'est pas finie. Les deux compétences trient par **cause** et ne s'arrêtent pas au
même endroit.

## Fonction de garde

```
1. RELIRE     l EPIC des suites ENTIER, pas seulement ce que ce chantier y a depose.
2. JUGER      chaque suite qui releve du chantier : DANS son perimetre, ou pas ?
3. LIVRER     ce qui est dans le perimetre, AVANT que la cloture conclue.
4. RATTACHER  le reste au chantier qui traite sa CAUSE, en ouvrant l EPIC s il
              n existe pas : gh issue edit <n> --parent <EPIC>
5. RECADRER   titre et corps de ce qui bouge.
6. AJOUTER    ce que la cloture elle-meme a revele, en issue tout de suite.
7. VERIFIER   la condition de sortie avant de passer a la 10.
```

## La passe ne découvre pas, elle héberge

Chaque trouvaille a **déjà** son issue, ouverte au moment où elle a été faite. Arriver ici avec une
page blanche est le signe que cette règle n'a pas été tenue, pas que le chantier n'a rien trouvé.

**Relire l'EPIC des suites entier**, et pas seulement ce que ce chantier-ci y a déposé : une
trouvaille d'un autre chantier peut avoir la même cause, et c'est ici qu'on s'en aperçoit. Vécu à la
clôture de #4874, où la trouvaille de la passe 7 a rejoint #4890 au lieu d'ouvrir un doublon.

**Un rattachement trop large posé dans l'urgence se corrige ici**, et c'est le moment prévu pour
cela. Une issue appartient au chantier qui traite sa **cause**, pas à celui qui a remarqué son
symptôme.

**Recadrer ce qui bouge.** Une issue écrite en trois lignes sur le coup a rempli son office, qui
était de ne rien perdre ; elle mérite maintenant sa forme complète.

## La condition de sortie peut renvoyer la clôture en arrière

C'est ce qui distingue cette passe des douze autres : elle peut décider qu'une suite est **dans** le
périmètre du chantier. Cette suite se livre alors avant que la clôture conclue, et les passes qui
l'auraient balayée se rejouent.

Une clôture qui « consolide » et passe à la suivante quoi qu'elle laisse derrière n'a pas tenu cette
passe. Le verdict n'est pas « ai-je rangé quelque chose », c'est **« reste-t-il quelque chose »**.

## Le sas ne se ferme pas, il se vide, et sa taille se mesure

Son corps revendique depuis son ouverture que sa taille est un signal : s'il enfle sans que rien n'en
sorte, ce ne sont pas les trouvailles qui sont trop nombreuses, ce sont les passes 9 qui ne le lisent
pas.

Cette taille était invérifiable tant que le sas portait une liste tenue à la main. Depuis l'ADR 4829,
le rattachement est une donnée :

```bash
gh issue view 4562 --json subIssuesSummary
gh issue list --state open --json number,title,parent \
  -q '.[] | select(.parent.number == 4562) | "\(.number) \(.title)"'
```

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « J'ai rangé les suites, la passe est faite » | Le verdict est « reste-t-il quelque chose », pas « ai-je rangé quelque chose » |
| « Cette suite est dans le périmètre, je la note » | Elle se livre **avant** que la clôture conclue, et les passes balayées se rejouent |
| « Je relis ce que mon chantier a déposé » | L'EPIC des suites se relit entier. Une trouvaille d'un autre peut avoir la même cause |
| « Page blanche, ce chantier n'a rien trouvé » | C'est le signe qu'on n'a pas consigné en chemin, pas qu'il n'y avait rien |
| « Je noterai cette trouvaille de clôture pour plus tard » | Une issue tout de suite. Les passes 0 à 8 trouvent, elles aussi |
| « Le rattachement était approximatif, tant pis » | C'est ici qu'il se corrige, et c'est le seul moment prévu |
| « Le sas grossit, il y a trop de trouvailles » | Ce sont les passes 9 qui ne le lisent pas |

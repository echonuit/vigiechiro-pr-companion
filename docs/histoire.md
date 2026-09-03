# D'où vient cette application

VigieChiro Companion est né d'un besoin de terrain, dans une salle de cours, avec un client qui
attendait vraiment quelque chose. Cette page raconte ce parcours, parce qu'il explique une partie
de ce que vous trouvez dans l'application.

## Le besoin

[Vigie-Chiro](https://www.vigienature.fr/fr/chauves-souris) est un programme de science
participative du Muséum national d'histoire naturelle. Des bénévoles posent des enregistreurs
autonomes sur des carrés de suivi, une nuit durant, et rapportent les ultrasons des chauves-souris
qui sont passées. L'équipe [Team Chiro](https://croemer3.wixsite.com/teamchiro/vigie-chiro?lang=fr)
en donne une présentation côté terrain.

Le lendemain matin, il reste une carte SD pleine. Il faut la copier, renommer les fichiers selon la
convention du protocole, écouter des séquences pour vérifier que la nuit vaut quelque chose,
constituer un lot, le déposer, attendre l'analyse automatique, puis relire les espèces proposées.
Ce travail se faisait en jonglant entre l'explorateur de fichiers, un tableur, un lecteur audio et
le site de la plateforme, avec des scripts que chacun se transmettait.

Samuel Busson, doctorant au CEREMA, a posé la commande : un outil qui tienne ce parcours de bout en
bout, sur le poste de l'observateur, sans rien lui demander de plus qu'une carte SD et un compte
Vigie-Chiro.

## Un énoncé avant du code

Le contexte du protocole, les personas, les parcours utilisateurs un par un, la carte des récits,
les règles métier : tout cela a été écrit avant la première ligne de code, puis découpé en tâches,
puis accompagné des tests qui diraient si le code faisait ce qu'on attendait de lui.

Ce document existe toujours et il est public : c'est le [brief](https://brief.echonuit.fr/). Si
vous vous demandez pourquoi un écran est fait comme il est, la réponse s'y trouve souvent, écrite
avant que l'écran n'existe.

## Quatre semaines, vingt et une équipes

Le développement s'est fait pendant la SAÉ 2.01 du BUT Informatique de l'IUT d'Aix-Marseille.
Vingt et une équipes d'étudiantes et d'étudiants de première année,
<!--inv:contributeurs-->90<!--/inv--> personnes, ont travaillé quatre semaines sur le même produit,
chacune dans son dépôt, chacune sur sa part du parcours. La première version utilisable est sortie de là. Les noms sont dans
[REMERCIEMENTS.md](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/REMERCIEMENTS.md).

## Deux consolidations

Un produit livré en quatre semaines par une promotion entière tient debout par endroits, et pas
partout. Deux passes ont suivi.

La première a repris les fonctionnalités une par une, en confrontant plusieurs modèles de langue au
même code.

La seconde a visé l'usage réel, celui d'un observateur qui traite sa saison entière du premier site
au dernier dépôt. Le brief a suivi le mouvement : plusieurs de ses parcours ont été écrits après la
SAÉ, à partir de ce qui manquait une fois l'application entre les mains de quelqu'un.

## Trois projets dans le même dépôt

Le même code sert trois choses à la fois.

C'est **un outil libre**, sous licence GPLv3, fait pour les gens qui tiennent le protocole
Vigie-Chiro. Vous pouvez l'installer, le modifier, le redistribuer, et regarder comment il est
fait.

C'est **un support d'apprentissage** : des étudiants de première année y apprennent le
développement logiciel sur une commande réelle, avec les revues de code, les tests et l'intégration
continue qui vont avec.

C'est enfin **un terrain de recherche** sur l'ingénierie du développement assisté par agents. La
méthode de travail, les règles que le dépôt s'impose et les décisions d'architecture sont publiques
et vérifiées automatiquement. Le
[dépôt GitHub](https://github.com/echonuit/vigiechiro-pr-companion) en donne le détail et les
travaux de recherche auxquels elles se rattachent.

## Ce que ça change pour vous

L'usage est gratuit, et le code est sous licence GPLv3 : personne ne peut vous le reprendre, et
n'importe qui peut le reprendre pour le faire évoluer.

L'application continue de changer. Si un écran vous bloque, si une convention de nommage ne
correspond pas à votre pratique, ou s'il manque quelque chose à votre parcours, dites-le sur la
[page des tickets](https://github.com/echonuit/vigiechiro-pr-companion/issues) : c'est de là que
viennent les versions suivantes. La [FAQ](faq.md) répond d'abord aux questions les plus
courantes.

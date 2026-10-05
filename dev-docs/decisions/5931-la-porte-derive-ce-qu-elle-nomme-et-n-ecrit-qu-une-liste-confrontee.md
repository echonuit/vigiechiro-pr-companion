---
type: adr
title: "La porte dérive ce qu'elle nomme, et n'écrit qu'une liste qu'un garde confronte"
status: stable
article: A3
chantier: "#5915 (ce que la porte dit couvrir, et ce qu'elle couvre), lots #5884 et #5931"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/batterie.py"
  - "scripts/methode/gardes-java-declares.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5481-la-porte-joue-ce-que-les-ateliers-jouent", "5373-une-liste-qu-un-garde-confronte-est-un-inventaire"]
generated:
  by: "process:assistance-par-agents"
---

# La porte dérive ce qu'elle nomme, et n'écrit qu'une liste qu'un garde confronte

## Le contexte

L'ADR 5481 a posé que la porte dit sous sa ligne de verdict ce qu'elle ne joue pas, et que ses
arguments se dérivent des ateliers, « jamais d'une liste écrite dans la porte ». L'ADR 5373 a posé
l'inverse apparent : une liste qu'un garde confronte n'est plus une liste, c'est un inventaire.

**Les deux ne se citaient pas.** Mesuré à la clôture de #5915 : zéro mention de 5373 dans 5481, et
zéro dans l'autre sens. Le chantier a pourtant écrit `GARDES_JAVA` dans la porte, donc il tenait
debout par 5373 en contredisant 5481 au mot près, sans que rien ne le dise. Qui ouvre 5481 d'abord
lit une interdiction que le code viole.

Et ce que la porte nommait était écrit à la main. Sa ligne annonçait deux contrôles sur le texte
d'une demande quand les ateliers en jouent trois : `verifie_chantier_de_l_issue.py` manquait, chez
elle comme dans la compétence `ouvrir-une-pr`, et il a rendu un check rouge sur une demande dont
tout le reste était vert, après une porte à zéro refus.

## La décision

**Ce que la porte nomme se dérive, comme ce qu'elle joue.** La ligne des contrôles de la prose d'une
demande vient des ateliers qui lient une variable à `github.event.pull_request.title` ou `.body` et
la passent en argument à un script. Un quatrième contrôle ajouté demain entrera sans qu'on y pense.

**Et le critère est la NATURE de l'argument, non la ressemblance avec la CI.** Dériver « tout script
que la CI lance avec un argument » rend dix-sept scripts, dont `installer_paquets.py` et
`construit_appimage.py` : c'est le piège que 5481 nomme en refusant d'indexer la porte sur la CI, son
exemple étant `revoque_jeton.py`. Ce qui fait la famille est qu'elle juge un texte que la demande
porte et dont aucune copie locale ne dispose. Pour la même raison, ces contrôles se **nomment** sans
se jouer : celui du chantier de l'issue lit la forge.

**Une liste écrite dans la porte reste admissible, et à une seule condition** : qu'un garde dérive la
même population de l'arbre et refuse quand les deux divergent. `GARDES_JAVA` la remplit, et c'est
`gardes-java-declares.py`, cliquet à zéro, qui la rend vraie. Retirer une entrée fait rendre 1 au
cliquet, mesuré par mutation. L'interdiction de 5481 porte donc sur la liste **non confrontée**, et
c'est la lecture que les deux ADR portent désormais l'une vers l'autre.

## Ce que la population déclarée exige en plus

Un verdict dit ce qu'il a lu. L'expression qui comptait la population vivait **en ligne** dans
l'appel au verdict, donc aucun cas ne l'atteignait : la ramener à une seule des deux familles faisait
tomber le compte de dix-sept à treize sans faire rougir personne. Une population déclarée est donc
**nommée**, et un cas relance le garde dans son mode de verdict pour confronter le compte **imprimé**
à elle. Éprouver la fonction ne suffit pas : c'est ce que le verdict en fait qui mentait.

**La limite est nommée.** Un compte littéral égal à la vérité du jour survit à ce cas. La tuer
demanderait que le mode de verdict accepte une racine, pour le jouer sur l'arbre témoin où la
population vaut quatre.

## Ce qu'on a écarté

**Faire jouer le contrôle du chantier de l'issue par la porte.** Il importe `subprocess`, appelle
`gh`, et son propre auto-test vérifie qu'il refuse sans lui. Les deux autres contrôles ne touchent
pas la forge et ne déclarent aucun `CONTRAT`. Un garde qui interroge la forge depuis un poste de
travail n'entre pas dans ce que la porte joue.

**Généraliser la confrontation à toutes les listes des gardes.** Quatorze constantes du corpus
énumèrent des artefacts du dépôt, et une seule est une population : les treize autres sont des
portées ou des exemptions déclarées, que l'ADR 5398 protège. `DOSSIERS`, dans
`releve-les-harnais-muets.py`, dit même avoir écarté la dérivation exprès, parce qu'elle donnerait la
population d'un seul flux.

## Conséquences

- la ligne de la porte suit les ateliers, et `AGENTS.md` la **cite** au lieu de recompter ses
  exceptions : son compte de « deux choses » était devenu faux par l'élargissement de la porte
  elle-même ;
- la compétence `ouvrir-une-pr` prescrit les trois contrôles, et dit lequel demande `gh` ;
- une liste écrite dans la porte se lit comme une dette tant qu'aucun garde ne la confronte.

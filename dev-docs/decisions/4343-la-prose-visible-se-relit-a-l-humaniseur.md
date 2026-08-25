---
type: adr
title: "La prose visible se relit à l'humaniseur avant d'être commise"
status: stable
article: A31
chantier: "#4343 (l'article du registre, chantier #4334)"
decided_at: 2026-08-24
verification: humaine
relations:
  amendee_par: ["4453-la-prose-publiee-sur-la-forge-releve-de-la-meme-grille"]
loupe:
  - "scripts/adr/2843-tiret-cadratin.py"
  - "scripts/adr/4359-javadoc-narratif.py"
  - "scripts/adr/0035-pictogramme-caractere.py"
verification_note: "aucun motif textuel ne décide si une emphase informe, ni si une javadoc paraphrase la signature qu'elle surmonte ; trois loupes relèvent ce qui se compte, la relecture tient le reste"
verified:
  - by: human:nedseb
    at: 2026-08-24
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-24
---

# La prose visible se relit à l'humaniseur avant d'être commise

!!! warning "Ce qui fait foi aujourd'hui"
    **Amendée le 2026-08-25** par [ADR 4453](4453-la-prose-publiee-sur-la-forge-releve-de-la-meme-grille.md) :
    l'énumération gagne le corps d'issue et le corps de pull request, et le déclencheur devient la
    publication plutôt que le commit.

## Contexte

Une grande part de la prose du dépôt a été écrite avec l'assistance d'un LLM : 37 511 lignes de
commentaire dans le code de production, 36 868 lignes de documentation. Le registre attendu était
écrit dans `CONTRIBUTING.md`, sous forme de sept tics, et tenu par la seule relecture.

Cette relecture ne s'appuyait sur rien : les sept tics avaient été relevés à la main, au fil des
reprises, sans inventaire ni source. Rien ne disait ce qui n'y figurait pas, ni pourquoi.

## Le défaut

Un registre qui n'existe qu'en intention se perd. Il s'est perdu quatre fois pendant le chantier de
mise au net : la même remarque de style a dû être refaite, en montant d'un cran chaque fois, jusqu'à
ce qu'elle soit écrite.

Une liste de sept tics sans provenance ne se conteste pas non plus. Un lecteur ne peut ni vérifier
qu'elle est complète, ni savoir ce qu'elle a écarté, ni proposer d'y ajouter quelque chose : elle
n'est pas une règle, c'est un goût.

## Décision

**Toute prose visible passe la grille de la compétence `humaniseur` avant d'être commise.** Elle
porte quarante et un motifs avec leurs exemples et leurs faux positifs, adossés à
« Signs of AI writing » et à son équivalent français.

Est **visible** ce qu'un humain lira hors de l'échange qui l'a produit :

- la javadoc et les commentaires du code ;
- la documentation, les ADR, les compétences ;
- les libellés montrés à l'utilisateur, dans l'interface comme en ligne de commande ;
- les messages de commit et les titres de demande de fusion.

N'est pas visible ce qui ne quitte pas l'échange : une réponse d'agent dans un fil, un fichier de
travail du bloc-notes.

**Les sept tics de `CONTRIBUTING.md` restent le sous-ensemble opposable.** La grille sert à relire ;
les sept servent à refuser une relecture. Un contributeur qui ne lit que `CONTRIBUTING.md` a ce
qu'il lui faut.

## Conséquences

**Le niveau est `humaine`, et le motif est mesuré.** Aucune expression régulière ne décide si une
emphase informe : sur quarante emplois de gras lus dans le dépôt jumeau (#4334), onze codent quelque
chose et vingt-neuf portent sur un mot ordinaire, et la syntaxe ne distingue pas les deux. Le défaut
le plus coûteux échappe de la même façon : une javadoc qui paraphrase la signature sans rien
ajouter, une garantie annoncée que le code ne tient pas, une unité qui ne correspond pas. Il est
dans l'écart entre le texte et ce qu'il décrit, et il faut les lire ensemble.

**La loupe existe et couvre le sous-ensemble mécanique** : le tiret cadratin en tolérance zéro, le
pictogramme employé comme caractère, et la javadoc qui raconte au lieu de contracter. Ces trois
gardes tiennent ce qu'un motif textuel peut tenir ; le reste est la relecture, et cette ADR le
déclare plutôt que de le taire. Un quatrième reste à écrire, sur la ligne de javadoc répétée.

**La grille garde les motifs que ce dépôt ne porte pas.** Les connecteurs lourds en ouverture de
phrase rendent zéro sur 47 564 lignes, et ils restent dans la grille : une règle ne se juge pas à sa
fréquence du jour, et le corpus qui la rend inutile aujourd'hui peut la rendre nécessaire demain.
`dev-docs/registre-editorial.md` rend compte de ce que ce dépôt a mesuré, un motif à la fois.

**Ce que la décision ne fait pas.** Elle n'impose pas de réécrire le corpus existant d'un coup. La
dette de javadoc narrative se résorbe par tranches sous le cliquet de l'article A30 ; le reste se
corrige au passage, quand on touche à un fichier.

## Alternatives écartées

- **Laisser le registre à `CONTRIBUTING.md` seul.** C'était l'état d'avant. Sept tics sans
  provenance, qu'un lecteur ne peut ni compléter ni contester.
- **Faire de la grille entière la règle opposable.** Quarante et un motifs à opposer en revue
  rendraient la revue impraticable, et la plupart ne se réalisent pas ici. Les sept mesurés suffisent
  à refuser.
- **Mécaniser la grille.** Trois motifs ont rendu des dizaines de lignes qu'une lecture a démenties :
  « richesse » est un terme du domaine, « est le nombre de » est du français ordinaire, « honnêtement »
  y est adverbial. Un motif textuel rend des suspects, pas des fautes.
- **Une passe de clôture dédiée.** Le cycle en compte déjà douze, et une passe de plus arrive trop
  tard : la prose se corrige quand on l'écrit, pas trois semaines après.

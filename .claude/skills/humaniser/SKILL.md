---
name: humaniser
description: Use when writing or reviewing French prose that may carry LLM writing patterns - inflated claims, sales language, vague sources, heavy connectors, anglicisms, stock words, filler, chatbot residue, reflexive bold. French counterpart of the English humanizer skill, based on Wikipedia's "Signs of AI writing" and its French equivalent "Aide:Identifier l'usage d'une IA generative".
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: CONTRIBUTING.md, section « Le registre »
---

# Humaniseur

## Loi d'airain

```
ON RETIRE L EMBALLAGE, JAMAIS LE FAIT
```

Réécrire un texte pour qu'il sonne humain ne change pas ce qu'il dit. Chaque affirmation, chiffre,
nom, date, citation et source survit à la réécriture. Rien ne s'invente pour combler un trou.

## Annoncer

« J'utilise la compétence humaniser pour <le texte>. »

## Quand elle s'ouvre, dans ce dépôt

L'article A31 la rend obligatoire pour toute **prose visible** : ce qu'un humain lira hors de
l'échange qui l'a produite. La javadoc et les commentaires, la documentation, les ADR, les
compétences, les libellés montrés à l'utilisateur dans l'interface comme en ligne de commande, les
messages de commit, les titres de demande de fusion et les **corps** d'issue et de pull request.
Ces deux corps-là sont ce qui se relit dans six mois sans le fil, et l'article les couvre depuis
qu'il déclenche sur la publication plutôt que sur le commit.

Une réponse d'agent dans un fil et un fichier de bloc-notes n'en sont pas. Un **commentaire** d'issue
ou de pull request non plus : il porte le journal, que le corps résume, et `clore-une-issue` dit
pourquoi le fil ne fait pas foi.

Les sept tics de `CONTRIBUTING.md` sont le sous-ensemble **opposable** de cette grille : ils servent
à refuser une relecture, quand la grille sert à relire.

## Ce qu'elle efface sans le viser

Réécrire un texte détruit les corrélations statistiques qu'il portait, y compris les marques de
provenance que plusieurs fournisseurs de modèles y placent délibérément. Cette compétence en efface
donc, incidemment, et c'est inévitable dès lors qu'on reformule.

**Ce dépôt déclare sa provenance ailleurs**, à la main, et cette déclaration ne tient à aucune
propriété du texte : l'article A26 l'exige, et 47 des 50 ADR décidées depuis lui portent
`generated.by` en en-tête. La compétence retire donc le **style**, pas la **provenance**.

S'en servir pour faire passer un texte pour non assisté contredirait A26. La loi d'airain dit déjà
« on retire l'emballage, jamais le fait », et la provenance est un fait.

Détecter une telle marque est hors de portée et n'est pas visé : ces schémas sont à clé, et un tiers
ne les lit pas.

## Fonction de garde

```
1. REPERER   les motifs de la grille ci-dessous, en OUVRANT les lignes qu un motif designe.
2. CONSERVER chaque affirmation. Raccourcir, fusionner, deplacer : oui. Retirer un fait : non.
3. N INVENTER RIEN. Pas un nom, pas un chiffre, pas une source. S il manque un detail,
             le demander ou ecrire une phrase plus simple.
4. GARDER    la voix de l auteur. Un echantillon de son ecriture prime sur cette grille.
5. RELIRE    le resultat contre la grille : une reecriture ecrit souvent ses propres tics.
```

## L'échantillon prime sur la grille

Si l'auteur fournit un texte de lui, le lire d'abord et relever ses habitudes : longueur des
phrases, ouvertures de paragraphe, ponctuation, formules qui reviennent. Les respecter ensuite,
même quand elles figurent dans la grille. Un auteur qui emploie le tiret cadratin en garde le même
taux ; le motif 14 ne devient pas une interdiction.

## La personnalité s'ajoute où elle a sa place

Un billet, un essai, un texte personnel gardent les opinions, les hésitations, l'humour et les
apartés de leur auteur. Un texte de référence, technique, juridique ou factuel reste neutre : on
n'y ajoute ni avis ni première personne. Dans les deux cas, aucun fait ne s'invente pour faire plus
vivant.

## Trancher un motif qui demande un jugement

Quatre motifs ne se décident pas sur la phrase : le 9, le 25, le 32 et le 34. Leur critère demande
de savoir ce qu'un lecteur aurait pu croire, ce que la phrase remplace, ou si quelqu'un avait
vraiment soulevé l'objection. Deviner cela varie d'un relecteur à l'autre, et c'est mesuré : sur
douze passages soumis à quatre lecteurs, dix ont reçu des verdicts différents.

**Le test de suppression les tranche, en deux gestes.**

Retirer la phrase, et regarder ce qui part. Si aucune information opérationnelle ne disparaît, la
phrase était l'emballage. Si le lecteur perd un fait, une contrainte ou une conséquence, elle
portait quelque chose.

Devant un « ce n'est pas X », citer le passage où X était en jeu. Sans ce passage, la négation ne
répond à personne et se retire.

**Où ce passage peut se trouver diffère selon le motif**, et c'est ce qui sépare le 9 du 34.
L'antithèse du motif 9 demande qu'un lecteur ait pu croire X **à cet endroit** : le passage doit
donc précéder. L'objection du motif 34 demande seulement que le texte nomme qui la porte, avant ou
après. Éprouvé sur un cas réel : « cent cinquante carrés n'est pas un chiffre choisi pour faire
durer » est légitime, et c'est la phrase **suivante** qui le montre en nommant qui tenait cette
position.

**Ces quatre motifs se décident sur ce qui entoure la phrase**, jamais sur elle seule. Un extrait
les rend indécidables, et sa frontière tranche alors à la place du lecteur : trois lecteurs à qui
l'on avait soumis des extraits ont classé « objection inventée » une phrase dont la suivante
nommait qui portait l'objection.

---

# La grille

Cinquante-quatre motifs, en trois séries. Les motifs **numérotés**, 1 à 42, rangés par famille.
Les six motifs **`FR`**, propres au français. Les six **traces d'outil**, notées `T`, qui ont leur
section et leur règle de lecture, une seule occurrence y suffisant à conclure.

Les trente-cinq premiers viennent de
[« Signs of AI writing »](https://en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing) (WikiProject
AI Cleanup) et sont adaptés au français. Les six `FR` viennent de sources françaises sans équivalent
dans la liste anglaise :
[« Aide:Identifier l'usage d'une IA générative »](https://fr.wikipedia.org/wiki/Aide:Identifier_l%27usage_d%27une_IA_g%C3%A9n%C3%A9rative)
et trois relevés de tics de ChatGPT en français. Les treize derniers, 36 à 42 et T1 à T6, viennent
de l'[humaniseur anglais d'Aboudjem](https://github.com/Aboudjem/humanizer-skill), sous licence MIT,
et leurs exemples sont refaits ici.

**Un numéro est un identifiant, pas une position.** Les motifs 36 à 42 ferment la famille où ils
tombent, si bien que la numérotation n'y est pas continue. C'est voulu : l'ADR 4476 cite « le motif
30 de la grille », et un numéro qui glisse à chaque ajout fait mentir ce qui le cite.

Un motif absent de votre corpus se garde quand même dans la grille : une règle ne se juge pas à sa
fréquence du jour.

## Contenu

### 1. L'importance gonflée

**Mots à surveiller** : marque un tournant, joue un rôle clé / central / majeur, témoigne de,
souligne l'importance de, s'inscrit dans une démarche / dynamique plus large, constitue une étape,
ouvre la voie à, marque un jalon, profondément ancré, laisse une empreinte durable.

Un détail ordinaire est présenté comme un basculement historique.

> L'Institut de statistique de Catalogne a été officiellement créé en 1989, marquant un tournant
> décisif dans l'évolution des statistiques régionales en Espagne, et s'inscrivant dans un mouvement
> plus large de décentralisation.

> L'Institut de statistique de Catalogne a été créé en 1989, dans le cadre de la décentralisation
> administrative espagnole.

**Réécrire** : Retirer la tournure d'importance et lire ce qui reste. S'il reste un fait (une date,
un effet), l'écrire avec un verbe simple : « le préfixe de fichier joue un rôle essentiel dans le
rattachement des séquences à leur passage » devient « le préfixe de fichier aide à rattacher les
séquences à leur passage ». S'il ne reste rien, le test de suppression retire la phrase. « S'inscrit
dans » se garde quand le cadre est nommé et détaillé ensuite, par exemple un EPIC et ses lots ou une
ADR citée, et part quand ce cadre reste « une démarche plus large ». Ne pas remplacer l'emphase par
un usage ou un chiffre absent de la source : si la page, l'ADR ou la javadoc ne dit pas à quoi sert
la chose, le demander à l'auteur.

### 2. Les noms cités pour prouver l'importance

**Mots à surveiller** : reprise dans les médias nationaux, couverture indépendante, ouvrage rédigé
par un expert reconnu, forte présence sur les réseaux sociaux, suivi par des milliers de.

Une liste de titres de presse ou un nombre d'abonnés remplace ce que la personne a dit.

**Réécrire** : garder la citation quand la source dit **quoi** et **où** ; ne jamais fabriquer ce
contexte pour raccourcir.

### 3. L'analyse creuse en participe présent

**Mots à surveiller** : soulignant, mettant en évidence, permettant ainsi, garantissant, reflétant,
témoignant de, illustrant, favorisant, contribuant à, englobant, assurant ainsi.

Le participe présent ajoute une profondeur que le fait n'a pas.

> La palette bleu, vert et or du temple résonne avec la beauté naturelle de la région, symbolisant
> les lupins du Texas et le golfe du Mexique, reflétant l'attachement de la communauté à sa terre.

> Le temple est peint en bleu, vert et or, couleurs choisies pour évoquer les lupins du Texas et le
> golfe du Mexique.

**Réécrire** : Prendre chaque participe et se demander s'il porte un fait que la source établit.
S'il en porte un, lui rendre un sujet et un verbe conjugué, dans une phrase à lui : « L'import copie
chaque fichier de la carte SD, permettant ainsi de vérifier votre nuit avant le dépôt » devient «
L'import copie chaque fichier de la carte SD. Vous pouvez alors vérifier votre nuit avant le dépôt.
» S'il ne fait qu'interpréter (reflétant, symbolisant, témoignant de), le test de suppression le
retire avec sa proposition. Une phrase qui aligne deux participes ou plus se coupe, une phrase par
fait gardé. Le gérondif d'une étape de la documentation utilisateur ou d'un pas de recette (« en
appuyant sur Entrée ») n'est pas ce motif et reste.

### 4. La langue de la brochure

**Mots à surveiller** : niché au cœur de, véritable, riche (au figuré), authentique, incontournable,
emblématique, à couper le souffle, cadre exceptionnel, savoir-faire, engagement, fleuron, joyau.

Le texte décrit un lieu, un produit ou une organisation comme une plaquette publicitaire.

> Niché au cœur de la magnifique région du Gondar en Éthiopie, Alamata Raya Kobo est une ville
> vibrante au riche patrimoine culturel et à la beauté naturelle saisissante.

> Alamata Raya Kobo est une ville de la région du Gondar, en Éthiopie.

**Réécrire** : Retirer les adjectifs d'éloge et les images (niché au cœur de, véritable, joyau),
puis écrire ce que la chose est et où elle se trouve, avec les mots de la source. Quand l'éloge
tient lieu d'une caractéristique, chercher celle-ci dans la source : « une interface exceptionnelle
» devient l'écran ou le raccourci clavier si le texte les donne, et disparaît sinon. « Véritable »
gonfle souvent le résultat d'un lot ou d'une fonction, comme dans « un véritable gain de temps » :
retirer l'adjectif, le nom suffit. Il reste quand il oppose le réel à un modèle, comme la « console
Windows véritable » d'une session de recette sur une machine réelle. La situation de terrain qui
ouvre une page de la documentation (« Le lendemain matin, il reste une carte SD pleine ») n'est pas
ce motif : elle se garde, seuls ses adjectifs d'éloge partent.

### 5. La source vague

**Mots à surveiller** : les experts estiment, certains observateurs, plusieurs sources, des études
montrent, il est admis que, des rapports du secteur, la communauté scientifique s'accorde.

Une affirmation est attribuée à des experts, des critiques ou des rapports que personne ne nomme.

> Il est admis que les résultats Tadarida arrivent 24 à 48 h après le dépôt (voir le parcours P7).

> D'après le parcours P7, les résultats Tadarida arrivent 24 à 48 h après le dépôt.

**Réécrire** : nommer la source réelle quand le texte source en donne une ; sinon retirer
l'affirmation. Ne jamais inventer de source.

### 6. La section « défis et perspectives »

**Mots à surveiller** : malgré ces défis, n'est pas exempt de difficultés, Défis et héritage,
Perspectives d'avenir, continue de se développer, l'avenir s'annonce.

Une section finale répète des généralités au lieu d'ajouter des faits. Elle se retire, sauf si elle
porte des dates ou des actes que la source établit.

> Deux écrans livrés (EPIC #2348) prolongent le parcours après le dépôt : la synthèse de la nuit
> situe chaque comptage d'espèce dans un référentiel de saison, de région et de milieu, et la courbe
> d'activité donne les contacts par tranche horaire et par espèce.
>
> Le parcours continue ainsi d'évoluer après le dépôt.

> Deux écrans livrés (EPIC #2348) prolongent le parcours après le dépôt : la synthèse de la nuit
> situe chaque comptage d'espèce dans un référentiel de saison, de région et de milieu, et la courbe
> d'activité donne les contacts par tranche horaire et par espèce.

**Réécrire** : Relever dans la section les dates, les actes et les limites précises : ils remontent
dans la section qui traite du même sujet, ou restent sous un titre qui dit ce qu'ils contiennent («
Limites de la mesure » plutôt que « Défis et perspectives »). Le reste part, titre compris. Une
phrase de bilan (« continue d'évoluer », « s'adapte aux nouveaux besoins ») se retire quand les
faits qu'elle survole sont écrits plus haut ; si elle est seule à les évoquer, demander à l'auteur
lesquels au lieu de les deviner. Une section de limites qui porte des contraintes réelles, comme «
Les limites, déclarées » de l'ADR 5707 (une aide d'un autre fichier échappe au garde, router puis
lire hors du fil passe encore), se garde, et seul son titre peut changer.

---

## Langue

### 7. Les mots surchargés

**En anglais** : delve, crucial, pivotal, tapestry, testament, landscape, showcase, underscore,
foster, garner, intricate, vibrant.

Le tic est le **groupe**, pas le mot isolé. Encore faut-il savoir ce que chaque mot pèse dans le
groupe, et les dix-neuf mots français ne pèsent pas pareil. Ils se rangent sur un seul axe :
**combien d'emplois le mot a-t-il dans le registre de ce dépôt ?** Un mot qui n'y en a aucun n'est
là que pour emballer ; un mot qui est aussi un terme du domaine ne prouvera jamais rien seul.

**L'axe est relatif à ce registre, et le dire évite une erreur.** « Incontournable » n'a aucun
emploi ici, et il en a un très vivant ailleurs : sur les 4 454 pages de foire aux questions
françaises du jeu `almanach/hc3_french_ood`, ses huit occurrences sont toutes commerciales, « un
véritable incontournable de la région », et toutes écrites par des humains. Le mot ne dit donc rien
de l'auteur. Il dit un registre, que ce dépôt n'a pas.

**Niveau 1, le mot sans emploi dans ce registre** : paysage (au figuré), tapisserie (au figuré),
fascinant, captivant, passionnant, révolutionnaire, transformateur, disruptif, incontournable.
Une occurrence suffit à ouvrir la ligne, et la ligne est presque toujours fautive.

**Niveau 2, le mot marqué qui garde un emploi** : crucial, primordial, fondamental, notable,
approfondi.
Il existe en français ordinaire, et l'emphase se voit quand il revient. Deux dans un paragraphe se
lisent ; un seul s'ouvre avant de se retenir.

**Niveau 3, le mot à deux emplois** : riche, essentiel, majeur, significatif, dynamique.
Il est aussi un terme du domaine, un nom commun ou un adjectif technique. **Il ne prouve jamais rien
seul**, quel que soit le nombre d'occurrences : la ligne s'ouvre d'abord.

**Ce qui a décidé ce rangement, et ce qui ne l'a pas décidé.** Le niveau 3 vient d'une mesure. Les
dix-neuf mots comptés le 2026-08-29 sur `dev-docs`, `docs`, `brief`, `openspec` et les commentaires
de `src` rendent 144 occurrences, dont **une seule est un tic**. Les 143 autres sont « richesse »,
qui est un nombre d'espèces (93), le nom commun « l'essentiel » (18), la majeure de version (13),
la dynamique audio (11), le sens statistique de « significatif » (7) et « la version approfondie »
(1).

**Ces trois niveaux pèsent la liste de ce motif, et rien d'autre.** Ils trient des mots isolés, dont
l'ambiguïté est le problème. Les autres motifs listent des tournures, bien moins ambiguës, et chacun
porte ses faux positifs sur place plutôt qu'une échelle.

Les niveaux 1 et 2 ne viennent pas de là. Douze de leurs quatorze mots rendent zéro, et un zéro ne
dit pas si un mot est absent parce qu'il est étranger à ce français ou parce qu'une relecture l'a
retiré. Les deux qui restent tiennent en une occurrence chacun : « crucial », qui est le seul tic du
corpus, et « approfondi », qui est du français ordinaire. Ces quatorze-là sont donc rangés à la
lecture, et la mesure dit seulement qu'ils ne servent presque à rien ici.

> Les captures sont une documentation vivante de l'état réel de l'application. Il est donc crucial
> qu'elles reflètent toutes les fonctionnalités visuelles du chantier. Une conséquence visible qui
> n'a pas de capture n'est pas documentée : elle dérivera en silence, et le prochain qui lira la doc
> verra un produit qui n'existe plus.

> Les captures sont une documentation vivante de l'état réel de l'application ; elles doivent donc
> refléter toutes les fonctionnalités visuelles du chantier. Une conséquence visible qui n'a pas de
> capture n'est pas documentée : elle dérivera en silence, et le prochain qui lira la doc verra un
> produit qui n'existe plus.

**Réécrire** : 1. Retirer le mot et relire la phrase sans lui. Si aucune information ne part avec
lui, il ne faisait qu'emballer. 2. Si la phrase ou la suivante donne la raison (un « car », une
conséquence), cette raison devient le verbe principal et le mot part. « Joue un rôle crucial dans »
se réécrit avec ce que la chose fait, tel que la source le décrit. 3. Si rien ne dit pourquoi, ne
pas inventer de conséquence : garder la phrase sans l'adjectif, ou demander à l'auteur ce qui
échouerait sans la chose. 4. Ne pas remplacer par un voisin de la liste : « essentiel » ou «
primordial » à la place de « crucial » rouvrent la même ligne. 5. Un mot de niveau 1 posé en
accroche (« un écran captivant ») se remplace par ce que l'écran permet de faire, tel que la page le
décrit, ou se retire. Un mot de niveau 3 pris dans son sens du domaine reste : la richesse d'un
carré, la majeure de version.

### 8. Le verbe simple évité

**Mots à surveiller** : constitue, se veut, fait office de, revêt, s'avère, représente un, demeure
un, se présente comme, dispose de, se compose de.

*Être*, *avoir* et *faire* sont remplacés par des périphrases.

> La galerie 825 se veut l'espace d'exposition de la LAAA. Elle dispose de quatre salles et fait
> office de vitrine sur plus de 280 m².

> La galerie 825 est l'espace d'exposition de la LAAA. Elle a quatre salles, sur plus de 280 m².

**Réécrire** : 1. Remplacer la périphrase par _être_, _avoir_ ou _faire_, ou par le verbe précis
qu'elle cachait : « dispose de » devient « a », « constitue » et « représente un » deviennent « est
», « demeure un » devient « reste un », « se compose de » devient « contient » ou une liste après un
deux-points. 2. Avant de remplacer, vérifier que le verbe n'est pas pris dans son sens propre. Un
spectrogramme « représente » les fréquences du son ; un double de test « fait office de » service
quand il en tient la place ; dans une ADR qui rend compte d'une mesure, « s'avère » dit qu'une
vérification a eu lieu, et se garde ou devient « on constate que ». 3. « Se veut » prête une
intention au sujet : écrire ce que la chose est, ou « sert à » si la source ne parle que d'un but.
4. Relire ensuite la phrase voisine : quand deux phrases courtes ont désormais le même sujet, une
virgule ou « et » les réunit.

### 9. L'antithèse et la chute tronquée

**Mots à surveiller** : ce n'est pas X, c'est Y ; non seulement X mais aussi Y ; bien plus qu'un X,
un véritable Y ; loin d'être un simple X.

L'antithèse ne se justifie que si un lecteur pouvait vraiment confondre les deux termes. Quand
personne n'a proposé X, elle emballe. Le **test de suppression** tranche : citer le passage
antérieur où X était en jeu, ou retirer la négation. Ici l'antériorité compte.

> Ce n'est pas qu'une chanson, c'est une déclaration. Ce n'est pas le rythme sous la voix, c'est
> l'atmosphère elle-même.

> Le rythme appuyé renforce le ton agressif.

La chute tronquée est la même faute en plus court : « les options viennent de l'élément choisi, pas
de devinette » se réécrit « les options viennent de l'élément choisi, sans que l'utilisateur ait à
deviner ».

**Réécrire** : Appliquer le test de suppression à la négation : chercher, avant la phrase, le
passage où un lecteur pouvait croire X. Sur un extrait, demander ce qui précède avant de trancher.

1. Le passage existe : l'antithèse reste telle quelle.
2. Il manque : retirer « ce n'est pas X » et affirmer Y seul, avec le détail qui le rend vrai.
3. « Non seulement X mais aussi Y » devient « X et Y » quand les deux sont des faits.
4. « Bien plus qu'un X, un véritable Y » et « loin d'être un simple X » deviennent une phrase qui
   dit ce que la chose fait en plus de X, d'après la source, ou disparaissent si la source n'en dit
   pas plus.
5. La chute tronquée garde son « pas Y » si Y était en jeu plus haut ; sinon, remplacer « pas Y »
   par une proposition complète introduite par « sans », ou le retirer.

### 10. Le tricolon forcé

Trois membres dont le troisième n'ajoute rien, pour faire complet.

> L'événement propose des conférences, des tables rondes et des occasions de réseautage. Les
> participants y trouveront innovation, inspiration et perspectives.

> L'événement propose des conférences, des tables rondes et des occasions de réseautage.

**Réécrire** : Pour chaque membre, se demander quelle affirmation de la source il porte.

1. Un membre qui redit un autre, ou que le test de suppression retire sans emporter de fait, part :
   deux membres réels restent deux.
2. Un membre qui porte une affirmation distincte, comme une contrainte que le code tient vraiment,
   reste, même abstrait.
3. Quand les trois sont des noms abstraits (« robustesse, lisibilité et pérennité »), chercher dans
   la source ce que la fonctionnalité ou le chantier produit concrètement et l'écrire. Si la source
   n'en dit pas plus, la phrase entière se retire ; rien ne s'invente à la place.
4. Une liste de trois éléments concrets reste à trois.

Relire ensuite le résultat, pour ne pas avoir remplacé un tricolon par un autre.

### 11. Le renommage et l'ouverture répétée

Le même sujet reçoit trois noms successifs, ou trois phrases s'ouvrent sur le même mot. Employer un
seul nom ; pour les ouvertures, fusionner les phrases ou commencer par l'action.

> Le protagoniste affronte des épreuves. Le personnage principal doit surmonter des obstacles. Le
> héros finit par triompher.

> Le protagoniste affronte des épreuves, puis finit par triompher.

La répétition voulue se garde : « Elle vint. Elle vit. Elle vainquit. »

**Réécrire** : Vérifier d'abord que les noms désignent bien la même chose : deux référents
différents gardent chacun leur nom, comme le sonogramme et le spectrogramme que le glossaire
distingue.

1. Choisir un seul nom, celui que le texte pose en premier, ou celui que le glossaire
   (`docs/glossaire.md`, `dev-docs/glossaire.md`) ou le code a fixé, et remplacer les autres.
2. Si un nom écarté apprenait quelque chose au lecteur, garder cette information une fois, en
   apposition.
3. Quand le sujet ne change pas d'une phrase à l'autre, un pronom suffit, ou les phrases fusionnent
   par une virgule ou « puis ».
4. Pour des phrases qui s'ouvrent sur le même mot, fusionner celles qui partagent le sujet, ou
   ouvrir sur l'action. Une suite d'étapes à l'infinitif dont plusieurs commencent par le même verbe
   garde sa forme : la répétition y aide à suivre.

### 12. La fausse gamme

**Mots à surveiller** : de X à Y, de Z à W, quand X et Y ne forment pas un intervalle.

> Notre voyage nous a menés de la singularité du Big Bang à la toile cosmique, de la naissance des
> étoiles à la danse énigmatique de la matière noire.

> Le livre traite du Big Bang, de la formation des étoiles et des théories sur la matière noire.

**Réécrire** : Vérifier que X et Y sont les deux bouts d'une même échelle qui a des intermédiaires :
une durée, une fréquence, une date, un numéro de version. Si c'est le cas, la gamme reste (« du
coucher au lever du soleil », « de la version 2 à la version 3 »). Sinon, écrire une énumération
simple, ou « notamment X et Y » pour garder l'idée que la source en vise d'autres. Retirer avec la
gamme les ornements qui l'accompagnaient (« la danse énigmatique ») sans perdre le fait qu'ils
habillaient. Quand la gamme ne sert qu'à dire « beaucoup de choses différentes », écrire celles que
la source cite, ou retirer la phrase.

### 13. Le passif et le sujet absent

**Mots à surveiller** : il est procédé à, il convient d'effectuer, aucune configuration n'est
requise, les résultats sont conservés.

Qui agit disparaît. Rendre l'acteur quand cela éclaire.

> Aucun fichier de configuration n'est requis. Les résultats sont conservés automatiquement.

> Vous n'avez pas besoin de fichier de configuration. Le système conserve les résultats.

**Réécrire** : Demander à la source qui fait l'action. Si c'est le lecteur, écrire « vous », ou
l'infinitif dans une suite d'étapes ; si c'est l'application, une classe ou un garde de la CI, en
faire le sujet ; si c'est une personne, la nommer avec le mot que la documentation emploie, comme
l'observateur. Si la source ne dit pas qui agit, garder le passif ou écrire une phrase plus simple,
sans inventer d'acteur. Le passif se garde aussi quand l'acteur n'apporte rien au lecteur (« la
grille des carrés est embarquée dans l'application ») ou quand l'objet est le sujet du paragraphe. «
Il est procédé à » et « il convient d'effectuer » se réécrivent par l'infinitif, ou par « vous »
suivi du verbe.

---

### 39. La fausse agentivité

**Mots à surveiller** : les données montrent que, les chiffres nous disent, le marché récompense,
la technologie impose, le code décide, l'architecture veut.

Une chose inerte reçoit un verbe de volonté, et l'acteur réel disparaît avec. Nommer qui agit, ou
rendre le verbe à ce qu'il décrit.

> Les données nous disent que la détection nocturne se dégrade après minuit.

> Sur les nuits mesurées, le taux de détection baisse après minuit.

Attention aux faux positifs : « la méthode rend une liste vide » est du français technique
ordinaire. Le tic est le verbe qui prête une **intention**, pas celui qui décrit un comportement.

**Réécrire** : Se demander ce que la chose fait au sens technique : calculer, comparer, renvoyer une
valeur, écrire un fichier. Remplacer le verbe de volonté par ce verbe, avec ce que la source en dit.
Si une personne a fait le choix que le verbe prête à la chose (un seuil, un ordre de tri, une valeur
par défaut), la nommer comme la source la nomme ; si la source ne dit pas qui, écrire ce qui a été
choisi sans inventer d'auteur. Un verbe qui décrit un comportement reste (« la méthode rend une
liste vide »), y compris dans une page sur l'identification automatique par Tadarida quand elle
montre tout de suite le mécanisme qu'il résume.

### 40. Le narrateur à distance

**Mots à surveiller** : on a tendance à, les gens ont l'habitude de, il y a comme un sentiment que,
d'aucuns pensent, on pourrait dire que, il est de coutume de.

Le texte survole une scène où personne n'est nommé, et le lecteur n'y est pas non plus. Mettre le
lecteur dedans, ou nommer qui fait la chose.

> On a tendance à négliger la relecture des captures.

> Une capture régénérée se referme sans être ouverte, et le défaut passe.

Ce motif est proche du FR5, et les deux se séparent nettement : FR5 vise le « nous » sans référent,
celui-ci vise le **tiers** indéfini.

**Réécrire** : Demander qui est le « on » ou « les gens ».

1. Si c'est le lecteur, écrire une étape à l'infinitif ou une phrase avec « vous », en reprenant la
   situation que la page décrit, comme l'écran ouvert ou la nuit importée.
2. Si la source désigne un groupe réel (les observateurs du protocole, l'auteur d'un retour sur une
   version), le nommer comme elle le nomme.
3. Si personne n'est désigné, écrire ce qui se passe, avec la chose pour sujet, tel que la source le
   décrit. Si la source ne dit rien de plus que l'habitude prêtée, retirer la phrase.

« On pourrait dire que » se tranche par le test de suppression : l'avis s'affirme s'il est celui de
l'auteur, ou part. « D'aucuns pensent » relève aussi du motif 34 : chercher où le texte nomme qui
pense. Aucune proportion ne s'invente pour remplacer le flou (« la plupart des observateurs »).

## Forme

### 14. Le tiret cadratin et le tiret demi-cadratin

Le texte final ne porte ni `—` ni `–`, sauf si l'échantillon de l'auteur en emploie. Les remplacer
par un point, une virgule, un deux-points ou une parenthèse. Surveiller aussi le tiret entouré
d'espaces et le double trait d'union employé comme tiret.

En français, le deux-points fait presque toujours le travail du cadratin, et il se lit mieux.

> /// Liste déroulante des heures de la journée (0 h – 23 h), affichées « 21 h ».

> /// Liste déroulante des heures de la journée (de 0 h à 23 h), affichées « 21 h ».

**Réécrire** : Lire ce que fait chaque tiret avant de choisir le signe qui le remplace.

1. Le tiret qui annonce une explication, un exemple ou une conséquence devient un deux-points.
2. Une paire de tirets autour d'une incise devient une paire de parenthèses si la phrase tient sans
   l'incise, une paire de virgules si l'incise est courte et fait partie du sens.
3. Le tiret qui ajoute une réserve après coup devient une virgule, ou un point quand la suite est
   une nouvelle affirmation.
4. Le tiret qui sépare un libellé de sa description dans une liste devient « : » (`- --definir-base
   : change l'emplacement de la base.`).
5. Le demi-cadratin entre deux nombres devient « à » dans une phrase ou une javadoc (« de 0 h à 23 h
   ») et un trait d'union dans une référence ou une cellule de tableau (« p. 22-31 »).

Quand le tiret ménageait un effet, changer le signe ne suffit pas : relire la phrase avec les motifs
9 et 31. Le code, une sortie de terminal et une citation ne se touchent pas, ni le cadratin que
l'application affiche pour une valeur absente, cité entre guillemets français ou seul dans une
cellule de tableau, que `scripts/adr/2843-tiret-cadratin.py` exempte pour cette raison.

### 15. Le gras à répétition

Le gras marque ce qui se lit de travers sans lui. Quand une ligne sur trois en porte, il ne marque
plus rien.

Distinguer deux emplois avant de couper. Le gras **code** quelque chose quand il désigne un libellé
d'interface, un nom de colonne, un terme du domaine défini, une contrainte forte, ou qu'il ouvre un
paragraphe en énonçant sa règle. Il **emballe** quand il tombe sur un mot ordinaire : « deux chemins
**distincts** », « un critère **booléen** ». Seul le second se retire.

Aucun contrôle mécanique ne fait cette différence : la syntaxe voit le gras, pas son utilité.

> Un emplacement est un **pointeur** : le changer dit à l'application **où aller lire au prochain
> démarrage**, il ne déplace rien. Si vous pointez la base vers un dossier **vide**, l'application y
> démarrera sur une base **neuve**, l'ancienne restant **intacte** à son ancien emplacement. Pour
> l'emporter, **copiez le fichier vous-même** avant de redémarrer.

> Un emplacement est un **pointeur** : le changer dit à l'application où aller lire au prochain
> démarrage, il ne déplace rien. Si vous pointez la base vers un dossier vide, l'application y
> démarrera sur une base neuve, l'ancienne restant intacte à son ancien emplacement. Pour
> l'emporter, **copiez le fichier vous-même** avant de redémarrer.

**Réécrire** : Pour chaque passage en gras, retirer les astérisques et relire la phrase. Si le
lecteur risque alors de chercher le mauvais libellé à l'écran, de manquer le terme que la phrase
définit ou d'enfreindre une contrainte (« hors fil JavaFX »), le gras revient. Sinon il reste
retiré, et rien ne le remplace : ni majuscules, ni italique, ni « attention ». Quand plusieurs
contraintes se disputent le gras dans un même paragraphe, le garder sur celle dont l'oubli fait
perdre des données (la base, l'audio d'une nuit). Seuls les astérisques changent, la phrase reste
telle quelle.

### 16. La liste à mini-titres gras

Une liste dont chaque entrée ouvre sur un mot en gras suivi d'un deux-points est un paragraphe
qu'on a découpé.

> - **Expérience utilisateur** : l'interface a été significativement améliorée.
> - **Performance** : les temps de chargement ont été optimisés.
> - **Sécurité** : le chiffrement de bout en bout a été renforcé.

> La mise à jour améliore l'interface, accélère le chargement et renforce le chiffrement de bout en
> bout.

Un cas reste légitime : l'étiquette **structurelle** d'un document de référence, répétée à
l'identique sur chaque entrée, qui sépare deux natures de contenu. Cette page en emploie une,
« Mots à surveiller », pour distinguer le lexique de l'explication. Le critère est la répétition
identique et la fonction : une étiquette qui change à chaque entrée n'en est pas une, c'est un
paragraphe découpé.

**Réécrire** : Lire la colonne des libellés seule. S'ils nomment des choses distinctes que le
lecteur viendra chercher (un écran, une colonne, une option de ligne de commande, une classe, une
ADR), la liste se garde. S'ils nomment des thèmes abstraits et que chaque description tient en une
affirmation vague, la liste est un paragraphe découpé : écrire une phrase dont le sujet est ce que
les entrées partageaient, et y reprendre les verbes et les faits des descriptions, sans ajouter un
chiffre ou un détail que la source ne donne pas. Deux entrées de ce genre tiennent presque toujours
dans une seule phrase.

### 17. La capitale à chaque mot d'un titre

Le français ne capitalise que le premier mot et les noms propres. Un titre entièrement capitalisé
est un calque de l'anglais, et il se voit d'autant plus qu'il est rare.

> ## Négociations Stratégiques Et Partenariats Mondiaux

> ## Négociations stratégiques et partenariats mondiaux

Attention aux faux positifs : un titre plein d'acronymes ou de noms de produit paraît capitalisé
sans l'être.

**Réécrire** : Passer en minuscule chaque mot après le premier, puis remettre la majuscule là où
elle appartient au nom : personnes, lieux, organismes, logiciels et outils dans la graphie de leur
source (Vigie-Chiro, Tadarida, SQLite, semantic-release), et sigles. Pour un nom douteux, regarder
comment le corps de la page, le glossaire (`docs/glossaire.md`, `dev-docs/glossaire.md`) ou le code
l'écrit. Quand le nom d'un protocole, comme « Point Fixe », revient dans le texte avec ses
capitales, le titre les garde ; si le texte l'écrit en minuscules, le titre suit.

### 18. Les pictogrammes décoratifs

Un emoji posé devant un titre ou une puce comme ornement. Le retirer. Un pictogramme qui **porte**
une information est autre chose, et il se juge sur ce qu'il porte.

> 🚀 **Lancement** : le produit sort au troisième trimestre
> 💡 **Enseignement** : les utilisateurs préfèrent la simplicité

> Le produit sort au troisième trimestre. Les utilisateurs préfèrent la simplicité.

**Réécrire** : Retirer le pictogramme et relire la ligne. Si des pictogrammes différents rangeaient
des entrées en catégories, les catégories doivent survivre : les nommer avec un mot que la source
donne, ou regrouper les entrées sous un intertitre, sans inventer de catégorie. Si le pictogramme
porte une information (un bouton d'interface reproduit comme « ☰ », un marqueur ✓ ou ✗ qu'un tableau
définit, un signe que l'application affiche vraiment ou qu'une maquette rend), il se garde ;
vérifier seulement que le texte dit ce qu'il signifie, et le signaler plutôt que deviner quand rien
ne le dit. Une ligne qui associe pictogramme, libellé gras et deux-points relève ensuite du motif
16.

### 19. Les guillemets et l'apostrophe

En anglais, le tic est le guillemet courbe là où l'auteur écrit droit. En français, la question se
pose autrement : les guillemets français `« »` sont la forme attendue, et le tic est le **mélange**
des formes dans un même texte.

L'apostrophe suit la même règle : choisir la droite `'` ou la courbe `’`, et ne jamais mêler les
deux. Un mélange se voit surtout quand le texte alimente une interface, où deux écrans affichent
alors deux apostrophes différentes.

> Pour la séquence en cours d'écoute, la section « Votre verdict sur ce fichier » propose trois
> boutons : "Bon", "Mauvais", "Inexploitable". Une séquence pas encore jugée reste en gris, « non
> jugé », dans la barre de répartition.

> Pour la séquence en cours d'écoute, la section « Votre verdict sur ce fichier » propose trois
> boutons : « Bon », « Mauvais », « Inexploitable ». Une séquence pas encore jugée reste en gris, «
> non jugé », dans la barre de répartition.

**Réécrire** : Les guillemets droits de la prose deviennent « », qui sont la forme attendue en
français. L'apostrophe ne se compte pas ici : le dépôt n'écrit que l'ASCII (`CONTRIBUTING.md`,
section « Le registre »), et `scripts/adr/4368-apostrophe-droite.py` relève chaque courbe hors de
ses trois exemptions (`CHANGELOG.md`, SVG rendus par Mocodo, signe cité). Remplacer ligne par ligne,
jamais par un remplacement global sur le fichier : les guillemets droits restent tels quels dans le
code, les commandes, les noms de fichiers, les chaînes Java, l'en-tête YAML d'une ADR, les
étiquettes d'un diagramme Mermaid et le titre d'un encart MkDocs (`!!! warning "..."`). Des
guillemets droits à l'intérieur de « » marquent une citation de second niveau (« le script affiche
"rien déposé" ») et ne forment pas un mélange.

---

### 36. Le titre en question

**Mots à surveiller** : Pourquoi X est-il important ?, Qu'est-ce que X ?, Comment fonctionne X ?,
En quoi X change-t-il la donne ?

Un titre de section posé en question, dans un texte qui n'est pas une foire aux questions. Il
annonce un développement au lieu de nommer ce que la section contient, et il oblige à lire pour
savoir si l'on est au bon endroit.

> ## Pourquoi la relecture des captures est-elle importante ?

> ## Une capture non relue dérive en silence

Une vraie foire aux questions garde ses questions : c'est sa forme, et le lecteur y arrive avec la
sienne.

> ## Pourquoi ces trois-là ?

> ## Les objectifs qualité que portent les trois personas

**Réécrire** : Chercher dans la section la phrase qui répond à la question du titre, et en tirer un
titre nominal ou déclaratif avec ses mots : « Comment fonctionne la synchronisation ? » devient « Le
principe de la synchronisation » si la section explique ce principe. Si la section ne répond pas à
sa question, le nouveau titre nomme seulement le sujet, et le manque se signale à part. Une question
que le lecteur se pose devant l'écran (« Une nuit manque dans le tableau ? ») a la forme d'une foire
aux questions, et se garde. Avant de renommer, chercher les liens vers l'ancienne ancre (`grep -rn
"#<ancre>" docs dev-docs brief`), puisque l'ancre suit le titre, que son slug retire les accents, et
qu'une ancre cassée fait échouer `mkdocs build --strict` (`validation: anchors: warn`).

### 42. Le gras là où il ne rend pas

Du `**gras**` dans un support qui n'affiche pas le Markdown : un message de commit, un libellé
d'interface ou de ligne de commande, un courriel en texte brut. Les astérisques s'y lisent
littéralement.

> git commit -m "corrige le **plafond** de la fenêtre"

> git commit -m "corrige le plafond de la fenêtre"

Distinct du motif 15, qui vise le gras **de trop** là où il s'affiche. Ici il s'affiche mal, et la
question de son utilité ne se pose même pas.

**Réécrire** : Retirer les astérisques et garder le mot. Si le gras signalait une contrainte, placer
la contrainte en tête de phrase ; dans un commit, elle peut aussi passer dans le corps du message.
Dans ce dépôt, le gras ne rend pas dans un message de commit, un titre de pull request ou d'issue,
l'en-tête YAML d'une ADR (`title`, `chantier`), une chaîne Java qu'un écran ou la commande
`vigiechiro` affiche, ni un bloc de code. Avant de retirer, vérifier que les astérisques ne sont pas
du code : l'opérateur `**` de Python dans `scripts/`, `**kwargs`, un motif de chemin `**/*.java`. Le
corps d'une pull request ou d'une issue, la documentation et la javadoc `///`, qui est du Markdown,
affichent le gras, et relèvent du motif 15.

## Restes de conversation

### 20. Le texte d'assistant laissé dans la réponse

**Mots à surveiller** : j'espère que cela vous aidera, bien sûr !, tout à fait !, vous avez
parfaitement raison, souhaitez-vous que, voulez-vous que je, dois-je continuer, n'hésitez pas,
voici un, voici la liste.

> Voici un aperçu de la Révolution française. J'espère que cela vous aidera ! N'hésitez pas si vous
> voulez que je développe une section.

> La Révolution française commence en 1789, quand la crise financière et les pénuries alimentaires
> provoquent des troubles.

> Les colonnes affichées par défaut sont décrites dans le tableau ci-dessous. N'hésitez pas à vous
> adapter, masquez les colonnes de certitude ou de référence selon votre façon de valider.

> Les colonnes affichées par défaut sont décrites dans le tableau ci-dessous. Vous pouvez masquer
> les colonnes de certitude ou de référence selon votre façon de valider.

**Réécrire** : Demander d'abord à qui parle la phrase. Si elle s'adresse à la personne qui a
sollicité l'assistant (« j'espère que cela vous aidera », « voulez-vous que je développe »), dans
une page de doc comme dans un corps d'issue, de PR ou un message de commit, la retirer entière sans
rien mettre à la place : le texte commence à la première phrase qui porte un fait, et « Voici un
aperçu de X » part de la même façon. Si rien ne reste après ce retrait, le signaler au lieu d'écrire
un contenu de remplacement. Si la phrase s'adresse au lecteur de la documentation, elle porte
souvent une indication, à garder sous une forme directe : « N'hésitez pas à masquer des colonnes »
devient « Vous pouvez masquer des colonnes », ou l'infinitif dans une suite d'étapes. Un « voici »
qui présente une liste, un tableau ou un bloc de code se garde, ou se remplace par le nom de ce qui
suit (« Les étapes : ») ; un libellé de l'application cité entre guillemets, une question de boîte
de dialogue par exemple, ne se touche pas.

### 21. L'aveu de limite et la conjecture qui le comble

**Mots à surveiller** : à ma connaissance, jusqu'à ma dernière mise à jour, les informations
disponibles sont limitées, d'après les sources accessibles, non rendu public, reste discret,
préserve sa vie privée, il semble que, vraisemblablement, on peut supposer.

Le modèle dit qu'il n'a pas trouvé, puis comble le trou par une supposition plausible. Écrire ce que
la source ne montre pas, ou retirer la phrase. Ne jamais présenter une conjecture comme un fait.

> Les informations sur ses débuts ne sont pas publiques, ce qui suggère qu'elle préserve sa vie
> privée. Elle a vraisemblablement grandi dans un milieu modeste, ce qui explique son intérêt pour
> l'éducation.

> Ses débuts ne sont pas documentés dans les sources disponibles.

**Réécrire** : 1. Couper le passage en deux : ce que la source ne dit pas (l'issue, l'ADR, le code,
la documentation de l'API VigieChiro), et la supposition qui comble le trou. 2. La supposition se
retire, avec ce qu'on en déduisait (« ce qui explique son intérêt pour l'éducation »). Si l'auteur
la tient d'une source, lui demander laquelle et écrire le fait sourcé à la place. 3. Le manque se
dit en une phrase sèche (« la documentation de l'API ne le précise pas »), ou se retire quand le
lecteur n'a pas besoin de le savoir. 4. « À ma connaissance » et « jusqu'à ma dernière mise à jour »
parlent de l'assistant et non du sujet : ils se retirent de tout texte qui n'est pas signé à la
première personne.

Une supposition que l'auteur veut garder s'écrit comme telle, et aucune phrase suivante ne s'appuie
sur elle comme sur un fait.

### 22. Le ton complaisant

**Mots à surveiller** : excellente question, vous avez tout à fait raison, c'est un point très
pertinent, quelle bonne idée.

> Excellente question ! Vous avez tout à fait raison, c'est un sujet complexe. Votre remarque sur
> les facteurs économiques est très pertinente.

> Les facteurs économiques que vous mentionnez comptent ici.

**Réécrire** : Retirer la phrase de compliment entière, sans lui chercher d'équivalent plus sobre.
Si le compliment désignait un contenu (« votre remarque sur les facteurs économiques »), ce contenu
devient le sujet de la phrase suivante, comme dans l'exemple ; si rien n'en reste, le texte commence
par l'information. Quand « vous avez raison » valide une correction, dans un corps de PR ou un
message de commit qui répond à une relecture, écrire le fait corrigé à la place de l'approbation. Un
compliment que l'application affiche, et que la documentation cite entre guillemets comme texte de
l'interface, n'est pas ce motif et se garde. « Une excellente occasion de » relève plutôt du motif
1.

---

## Remplissage et nuances

### 23. Les formules de remplissage

**En français** : afin de pouvoir, dans le but de, du fait que, à l'heure actuelle, dans le cas où,
il est important de noter que, il convient de souligner que, on notera que, il est à préciser que,
il importe de garder à l'esprit que, force est de constater que, a la capacité de, a la possibilité
de.

> Afin de pouvoir atteindre cet objectif → Pour atteindre cet objectif
> Du fait qu'il pleuvait → Parce qu'il pleuvait
> À l'heure actuelle → Aujourd'hui
> Il est important de noter que les données montrent → Les données montrent
> Le système a la capacité de traiter → Le système traite

**Réécrire** : 1. Une locution qui double un mot court se ramène à ce mot : « afin de pouvoir » à «
pour », « du fait que » à « parce que », « dans le cas où » à « si ». « Afin de » seul reste du
français correct et ne se chasse pas. 2. Une formule qui annonce une remarque se retire, et la
phrase commence par ce qui suivait le « que » : « Il est important de noter que `reactiver` est
idempotente » devient « `reactiver` est idempotente ». 3. Quand la formule annonce une précaution
détaillée juste après, retirer l'annonce et garder la précaution : « il est important de prendre
certaines précautions : avant de confirmer `metadonnees-passage --tout`, il est nécessaire de lancer
la commande sans `--confirmer` pour lire ce qu'elle ferait » devient « Avant de confirmer
`metadonnees-passage --tout`, lancer la commande sans `--confirmer` pour lire ce qu'elle ferait ».
4. « A la capacité de » devient le verbe seul si la chose se produit toujours, et « peut » si elle
reste seulement possible ; « a la possibilité de » devient « peut ». Ce choix est un fait : le
demander quand le texte ne le tranche pas.

Dans une procédure (une étape de la documentation, une marche à suivre d'un guide développeur), « il
convient de », « il est important de » ou « il est nécessaire de » suivi d'un verbe devient
l'infinitif seul, plus direct pour une étape.

### 24. L'empilement de nuances

**Mots à surveiller** : pour être honnête, il se pourrait que, potentiellement, on pourrait
éventuellement, dans certains cas il se peut que, cela reste une hypothèse.

Une relecture après l'autre ajoute un correctif, jusqu'à ce que plus rien ne soit affirmé. Garder la
nuance que la source appuie et que le sens réclame ; retirer celles qui ne font que rattraper une
exagération précédente.

> On pourrait éventuellement soutenir que la mesure aurait peut-être un certain effet.

> La mesure peut avoir un effet.

**Réécrire** : 1. Pour chaque affirmation, relever les marques de doute : verbe au conditionnel,
adverbe (« éventuellement », « potentiellement »), tournure (« il se peut que »). 2. N'en garder
qu'une, celle qui dit le degré de certitude que donne la source : « peut » pour un possible, «
souvent » pour une fréquence, ou mieux la condition elle-même quand la source la nomme (« si le site
est rattaché »). 3. Si la source affirme, retirer toutes les marques. Si elle doute, ne pas retirer
la dernière : changer un possible en certitude modifie le fait.

### 25. La clôture optimiste

Le texte se termine sur un souhait au lieu du dernier fait utile.

> L'avenir s'annonce prometteur pour l'entreprise. De belles perspectives se dessinent sur le chemin
> de l'excellence.

Retirer le paragraphe. Si la source donne des projets réels, écrire les projets. Le **test de
suppression** tranche : si rien d'opérationnel ne part avec la clôture, elle était l'emballage.

**Réécrire** : Remonter à la dernière phrase qui porte un fait, une étape ou un lien, et arrêter le
texte là, en fin de page de doc, d'ADR ou de corps de PR. Une clôture qui mêle un souhait et un fait
se coupe : le fait reste, déplacé au besoin dans la section qui en parle, et le souhait part. Si la
fin paraît alors abrupte, ne pas écrire de phrase de transition pour l'adoucir. Un message de
l'application que la documentation cite entre guillemets comme texte affiché (un bandeau de fin
d'opération, par exemple) ne relève pas de ce motif.

### 26. Les paires à trait d'union

**En anglais** : data-driven, cross-functional, end-to-end, real-time, long-term.

En français, le tic prend une autre forme : le calque non traduit (« une approche data-driven »,
« un suivi end-to-end », « une solution clé-en-main ») là où un mot français existe. Le remplacer.

> Aucune dépendance native binaire spécifique à un OS dans le `pom.xml` (en dehors de JavaFX, qui
> gère son propre cross-platform).

> Aucune dépendance native binaire spécifique à un OS dans le `pom.xml` (en dehors de JavaFX, qui
> assure lui-même sa portabilité).

**Réécrire** : 1. Chercher le mot français qui dit la même chose dans la phrase : « plug-and-play »
devient « prêt à l'emploi », « cross-platform » devient « portable » ou « d'un OS à l'autre », «
data-driven » devient « fondé sur les données ». 2. Si aucun mot ne convient, décrire ce que la
chose fait au lieu de forger un calque. 3. Garder le terme anglais quand il est le nom de ce que le
lecteur va rencontrer (dans l'interface, dans la sortie d'une commande, dans le code ou dans l'API),
et le gloser à sa première mention. Un nom de produit, de bibliothèque ou d'identifiant du code ne
se traduit pas.

---

### 37. L'ouverture énumérative prudente

**Mots à surveiller** : il existe plusieurs façons de, il y a quelques points à considérer, de
manière générale, en règle générale, plusieurs facteurs entrent en jeu, cela dépend de plusieurs
choses.

La phrase annonce une liste vague au lieu de donner la réponse, et la réponse arrive au paragraphe
suivant, si elle arrive. Commencer par ce qu'on sait.

> Il existe plusieurs façons de gérer la concurrence. De manière générale, cela dépend du contexte.

> La garde compare l'enregistrement à la base, et refuse si un champ écrit a bougé.

Ce motif est adossé au corpus HC3 ([arXiv:2301.07597](https://arxiv.org/abs/2301.07597)), qui
compare des réponses humaines et des réponses de modèle.

**Réécrire** : Chercher plus loin la réponse que la phrase annonce. Si elle arrive, retirer
l'annonce et ouvrir sur elle, en gardant les critères que l'annonce nommait ; devant une liste, la
liste suffit, précédée au besoin du nom de son objet (« Pour réactiver un passage : »). Si la phrase
dit que tout « dépend » sans dire de quoi, écrire les critères que donne la source ; si la source
n'en donne pas, retirer la phrase au lieu d'en inventer. « De manière générale » se retire, ou
devient la condition réelle quand le texte la donne. Deux cas se gardent : l'annonce suivie de ses
éléments dans la même phrase (« dépend de deux conditions : la connexion et le rattachement du site
»), et la question qu'un corps d'issue ou une ADR proposée laisse ouverte exprès pour que ses
lecteurs la tranchent.

### 38. Le tapis roulant

**Mots à surveiller** : autrement dit, en d'autres termes, pour faire simple, cela signifie que,
c'est-à-dire que, pour le dire autrement.

Un paragraphe redit ce que le précédent a dit, sous un autre habillage, sans rien ajouter.
L'épreuve tient en une question par phrase : **qu'est-ce qui est neuf ici ?** Ce qui ne répond pas
se retire.

> Le seuil est fixé à 50 mètres. Autrement dit, au-delà de 50 mètres, la comparaison ne se fait
> plus. Cela signifie que deux points distants de plus de 50 mètres ne sont jamais rapprochés.

> Deux points distants de plus de 50 mètres ne sont jamais rapprochés.

Une reformulation se garde quand elle traduit vraiment : une formule vers des mots, un terme du
domaine vers la langue courante.

**Réécrire** : Pour chaque phrase introduite par le connecteur, choisir l'une de ces issues.

1. Elle redit la précédente dans le même registre : garder la plus concrète des deux, celle qui
   porte le chiffre ou le comportement observable, et retirer l'autre avec son connecteur. Reporter
   dans la phrase gardée le détail que seule l'autre portait.
2. Elle tire une conséquence que la précédente ne disait pas (« Le son est ralenti dix fois. Cela
   signifie qu'une seconde enregistrée dure dix secondes à l'écoute ») : la garder, et remplacer «
   cela signifie que » par « donc » ou par l'énoncé direct.
3. Elle explique un terme ou une formule : c'est une traduction, elle se garde, de préférence en
   apposition ou entre parenthèses au premier emploi du terme.

Retirer seulement le connecteur ne corrige rien quand la phrase qui reste redit la précédente.

## Rhétorique

### 27. La fausse révélation

**Mots à surveiller** : au fond, en réalité, la vraie question est, ce qui compte vraiment,
fondamentalement, le véritable enjeu, le cœur du sujet, tout l'enjeu est là.

Une remarque ordinaire est présentée comme une vérité cachée.

> La vraie question est de savoir si les équipes peuvent s'adapter. Au fond, ce qui compte vraiment,
> c'est la maturité de l'organisation.

> La question est de savoir si les équipes peuvent s'adapter, ce qui dépend surtout de la capacité
> de l'organisation à changer ses habitudes.

**Réécrire** : Retirer le marqueur (« au fond », « la vraie question est », « fondamentalement ») et
écrire la remarque comme une affirmation simple. Quand la phrase hiérarchise (« ce qui compte
vraiment, c'est X »), chercher dans la source ce que X dépasse : si elle le dit, écrire la
comparaison avec ses deux termes ; sinon écrire seulement X, sans « vraiment ». Garder « en réalité
» quand il corrige une croyance que le texte vient d'énoncer, comme une question de la FAQ ou une
page de `dev-docs/` qui démonte une idée fausse sur la plateforme ou le protocole : le contraste y
porte l'information. Sans croyance énoncée juste avant, « en réalité » part et la phrase reste. « Au
fond » au milieu d'une phrase, ou au sens propre (« un enregistreur posé au fond d'un vallon »),
n'est pas ce motif.

### 28. L'annonce avant la chose

**Mots à surveiller** : entrons dans le vif du sujet, voyons cela de plus près, décortiquons,
voici ce qu'il faut savoir, sans plus attendre, petit point, deux choses méritent d'être dites,
et c'est instructif.

Le registre familier ne rachète pas l'annonce : « un truc qui m'a bien piégé » a le même défaut.

**T6 est son voisin**, et les deux se confondent parce qu'ils partagent « décortiquons ». Le motif
28 annonce au lecteur ce qui vient ; T6 laisse voir le procédé, qui ne lui était pas destiné.

> Voyons de plus près comment fonctionne le cache dans Next.js. Voici ce qu'il faut savoir.

> Next.js met en cache à plusieurs niveaux : mémoïsation des requêtes, cache de données, cache du
> routeur.

**Réécrire** : Supprimer l'annonce et commencer par la première information qu'elle promettait.
Quand elle introduit des étapes, la remplacer par la consigne elle-même, sans rien perdre de ce
qu'elle nommait : « voyons comment publier ces observations en déclarant leur certitude et en
rattachant la nuit à sa participation » devient « Pour publier ces observations, déclarez leur
certitude et rattachez la nuit à sa participation. » Si l'annonce donne un nombre ou un ordre (trois
étapes, d'abord l'import), garder ce nombre ou cet ordre dans la phrase qui suit. Un « voyons » cité
tel quel, dans le retour d'un observateur ou une phrase d'issue reprise entre guillemets, est une
tournure citée et ne se réécrit pas.

### 29. Le titre répété par sa première phrase

Un titre suivi d'une phrase qui ne fait que le redire. Retirer la phrase.

> ## Performance
>
> La vitesse compte.
>
> Une page lente fait partir les visiteurs.

> ## Performance
>
> Une page lente fait partir les visiteurs.

**Réécrire** : Comparer la première phrase au titre de sa section, dans une page de `docs/` comme
dans une ADR : si elle ne dit rien qu'il ne dise déjà, la retirer et faire commencer la section à la
phrase suivante. Si elle redit le titre mais ajoute un détail qu'il n'a pas (une condition, une
raison), garder ce détail et couper la redite. Quand cette phrase est la seule de la section, ne pas
laisser un titre vide : demander à l'auteur ce que la section devait contenir, ou fondre la section
dans sa voisine.

### 30. La version précédente racontée

**Mots à surveiller** : auparavant, jusqu'ici, jadis, naguère, autrefois, à l'époque, avant elle,
anciennement, désormais, dorénavant.

Une documentation et un commentaire décrivent le comportement d'aujourd'hui. Le passé a ses lieux :
l'historique de version, le journal des changements, la section des alternatives écartées d'une
décision d'architecture.

> Cette fonction a été ajoutée pour remplacer l'ancienne approche qui parcourait tous les éléments,
> ce qui coûtait O(n²). Elle passe désormais par une table de hachage, avec une recherche en O(1).

> Cette fonction passe par une table de hachage, avec une recherche en O(1).

**Réécrire** : Écrire ce que la chose fait aujourd'hui, au présent, et retirer le récit du
changement : « la modale distingue désormais les causes d'échec » devient « la modale distingue les
causes d'échec ». Si l'ancien comportement peut encore surprendre le lecteur (une version antérieure
de Companion encore installée, une capture prise ou un clip tourné avant le changement), le garder
en une phrase avec la version que la source donne ; sans version connue, la demander plutôt que de
dater au jugé. Le reste du récit va dans le message de commit, le corps de la pull request ou la
section « Alternatives écartées » de l'ADR. Le contexte d'une ADR décrit l'état d'avant la décision
parce que cet état est son sujet : il se garde.

### 31. La chute dramatique et le fragment

Chaque phrase devient une clôture. Une phrase courte appuie ; une suite de fragments courts sonne
faux.

> Puis AlphaEvolve est arrivé. Aucune préférence pour la symétrie. Aucun a priori esthétique. Aucune
> nostalgie du goût humain. Les anciennes règles n'existaient plus.

> AlphaEvolve a changé la recherche parce qu'il ne privilégiait ni la symétrie ni les formes
> familières, ce qui a rendu certaines hypothèses anciennes moins utiles.

**Réécrire** : Fusionner la série en une ou deux phrases complètes qui disent le lien entre les
fragments : une cause, une conséquence, ou une énumération après un deux-points. Ne poser que le
lien que la source donne ; sans cause connue, l'énumération suffit. La phrase de clôture qui
commente ce qui précède (« Les anciennes règles n'existaient plus. ») devient la conséquence de la
phrase fusionnée si elle porte un fait, et part si le test de suppression ne lui en trouve aucun.
Une phrase courte isolée se garde quand elle porte une consigne ou un fait (« Rien n'est envoyé. »).

### 32. La formule toute faite

**Mots à surveiller** : X est le Y de Z, X devient un piège, X n'est pas un outil mais un miroir,
le langage de, la monnaie de, l'architecture de, le nerf de la guerre.

Une affirmation ordinaire prend la forme d'un aphorisme et perd son détail.

> La symétrie est le langage de la confiance. L'efficacité devient un piège quand les équipes
> oublient la couche humaine.

> Une disposition symétrique paraît souvent plus prévisible. Les équipes peuvent optimiser un
> processus au point de perdre de vue son usage réel.

Attention aux faux positifs : « l'abscisse est le nombre de minutes depuis 18 h » est du français
ordinaire, pas une formule. Le **test de suppression** tranche : une formule qui part sans emporter
de détail était une formule.

**Réécrire** : Se demander ce que la formule affirme, et l'écrire en une phrase avec un sujet et un
verbe concrets. Chercher ensuite dans la source le détail qu'elle remplace (qui agit, dans quel cas,
avec quel effet) et l'écrire à la place. Si la source ne le donne pas, garder l'affirmation simple,
ou retirer la phrase quand le test de suppression montre qu'elle ne portait rien. Une image qui
explique un mécanisme et que le texte déroule ensuite n'est pas une formule : le cliquet de l'ADR
2867, dont la décision dit aussitôt qu'il est une liste qui ne peut que rétrécir, se garde.

### 33. La fausse candeur

**Mots à surveiller** : honnêtement ?, soyons clairs, disons-le, le fait est, voici la chose, pour
être franc, entre nous.

Le tic est l'ouverture théâtrale, pas le mot. « Dire honnêtement de quand il date » n'a rien à voir.

> Est-ce que ça vaut le prix ? Honnêtement ? Ça dépend de la fréquence d'usage.

> Que cela vaille le prix dépend de la fréquence d'usage.

**Réécrire** : Retirer la formule et la ponctuation qui la met en scène (le point d'interrogation
après « Honnêtement », le deux-points après « soyons clairs ») : la phrase qui reste porte
l'affirmation. Quand la formule suit une question posée au lecteur, fondre la question et la réponse
en une seule phrase. Garder le mot quand il qualifie un fait (« dire honnêtement de quand il date
»), et ne pas compter « entre nous » dans « chacun d'entre nous ».

### 34. L'objection que personne n'a soulevée

**Mots à surveiller** : ce n'est pas tant une question de, je ne dis pas que, qu'on ne s'y trompe
pas, loin de moi l'idée de, on pourrait croire que, certains diront que.

Le texte répond à une objection qui n'apparaît nulle part ailleurs. Retirer la défense ; si elle
porte une affirmation réelle, écrire l'affirmation.

Garder l'objection quand le texte nomme qui la porte, ou qu'il y répond entièrement. Une affirmation
directe comme « cette API n'est pas thread-safe » n'est pas ce motif. Le **test de suppression**
tranche : citer où l'objection est portée, avant ou après, ou la retirer.

> Bien que ce guide ne puisse couvrir tous les cas d'une carte SD en fin de vie, nous espérons qu'il
> vous donnera les repères nécessaires pour diagnostiquer vos nuits. L'essentiel n'est pas de
> connaître chaque code d'erreur, mais de savoir lire la complétude d'une nuit avant de la publier.

> Ce guide donne les repères nécessaires pour diagnostiquer vos nuits ; il ne peut pas couvrir tous
> les cas d'une carte SD en fin de vie. L'essentiel est de savoir lire la complétude d'une nuit
> avant de la publier, plus que de connaître chaque code d'erreur.

**Réécrire** : Retirer la négation et regarder ce qui reste : si une affirmation survit (« vous
pouvez commencer sans tout maîtriser »), l'écrire seule, à la forme positive ; sinon retirer la
phrase. Le périmètre qui accompagne souvent cette défense (ce que la page, l'ADR ou le garde ne
couvre pas) se garde, parce que le lecteur perd une information quand on le retire. Un vœu de
l'auteur (« nous espérons que ce guide vous donnera des repères ») devient ce que la page contient,
ou part.

### 35. La fausse alternative écartée

**Mots à surveiller** : une solution tentante serait, on aurait pu, il serait tentant de, une
approche évidente consisterait à, on pourrait penser que, certains suggéreraient.

Une option que personne n'envisagerait est introduite, écartée en une proposition, puis jamais
reprise. C'est souvent le reste d'une version antérieure du texte.

> Les jetons de session sont renouvelés toutes les 24 heures. Une solution tentante serait de
> redémarrer le service d'authentification par une tâche planifiée, mais cela couperait toutes les
> sessions actives. Le renouvellement se fait en place.

> Les jetons de session sont renouvelés en place toutes les 24 heures.

Une alternative réelle se garde : dans un document de conception ou un tutoriel, le lecteur peut
vraiment l'envisager. Plusieurs rejets courts et sans suite dans le même texte sont le signal.

**Réécrire** : Appliquer le test de suppression à la phrase qui écarte l'option : si le lecteur ne
perd aucune contrainte, la retirer et ne garder que la solution retenue. Si le rejet portait une
conséquence utile et que la source dit que la solution retenue l'évite, la rattacher à cette
solution : « mais cela couperait toutes les sessions actives » devient « sans couper les sessions
actives ». Garder l'alternative quand le lecteur risque vraiment de la prendre, comme une option
pesée dans la section « Alternatives écartées » d'une ADR, ou la croyance qu'un nom de localité déjà
pris sur la plateforme signifie « c'est déjà le nôtre », que `dev-docs/api-vigiechiro.md` corrige
aussitôt : la nommer alors comme l'erreur à éviter, avec ce qu'elle provoque si la source le dit.
Devant plusieurs rejets courts dans le même texte, ne garder que ceux que la source appuie.

### 41. Le crochet d'accroche

**Mots à surveiller** : Le hic ?, Le problème ?, Et c'est là que ça coince., Ça vous parle ?,
La bonne nouvelle ?, Devinez quoi., Sauf que.

Une phrase très courte tient lieu de suspense avant la phrase qui porte l'information. Elle vient
de l'écriture pour les réseaux, où elle sert à retenir quelqu'un qui fait défiler. Retirer le
crochet : la phrase suivante fait son travail seule.

> La garde bloquait tous les envois. Le hic ? Elle comparait deux lectures espacées de
> millisecondes.

> La garde bloquait tous les envois parce qu'elle comparait deux lectures espacées de seulement
> quelques millisecondes.

Distinct du motif 31 : la chute dramatique est une **suite** de fragments, celui-ci est une
question ou un fragment isolé qui sert de charnière.

**Réécrire** : Retirer le crochet et relire la phrase suivante seule. Si elle ne se comprend plus,
rattacher son contenu à la phrase d'avant avec le lien que la source donne (« parce que », « mais »)
; sans lien connu, garder deux phrases simples. Quand le crochet annonce une nouvelle (« La bonne
nouvelle ? »), écrire la nouvelle directement. Dans un passage cité tel quel, comme le retour d'un
observateur ou une phrase d'issue reprise entre guillemets, le crochet est une tournure citée et se
garde.

---

## Les six motifs propres au français

### FR1. Les connecteurs lourds en ouverture de phrase

**Mots à surveiller** : Cependant, Toutefois, Néanmoins, Par ailleurs, En outre, De surcroît, Par
conséquent, En conséquence, De ce fait, Dès lors, En effet, En somme, En résumé, En définitive, De
plus, D'une part / D'autre part, Force est de constater, À cet égard.

C'est le tic français le plus cité, et le plus mécanique : chaque phrase reçoit son connecteur, si
bien que le texte annonce en permanence sa propre logique.

Le mot n'est pas en cause. Le tic est la **position** et le **taux** : un connecteur en tête de
phrase, phrase après phrase. Employé au milieu d'une phrase, il est du français ordinaire.

> Le seuil ne tient pas. Cependant, la mesure reste utile. Par ailleurs, elle est peu coûteuse. En
> outre, elle se rejoue. Par conséquent, on la garde.

> Le seuil ne tient pas, mais la mesure reste utile : elle coûte peu et se rejoue.

Pour mesurer : compter ces mots **en tête de phrase**, pas n'importe où, et éprouver le compteur sur
une phrase témoin fabriquée avant de conclure sur un zéro.

**Réécrire** : Compter d'abord les connecteurs en tête de phrase sur tout le paragraphe : un seul se
garde, c'est l'enchaînement qui se corrige. Pour chacun, lire le lien qu'il annonce entre les deux
phrases, puis choisir un geste.

1. Le lien se lit sans lui (une suite, un ajout comme « Par ailleurs », « En outre », « De plus », «
   À cet égard ») : retirer le mot.
2. Le lien est réel et les deux phrases sont courtes : les fusionner avec « mais », « donc », « car
   » ou un deux-points. « En effet » devient presque toujours « car » ou un deux-points.
3. Le lien est réel et la phrase doit rester seule : prendre le mot le plus simple (« mais » pour «
   Néanmoins », « donc » pour « Par conséquent »), ou le placer après le verbe (« la mesure reste
   toutefois utile »).
4. « En somme », « En résumé », « En définitive » : si la phrase redit le paragraphe, elle part avec
   son connecteur (motif 38) ; sinon seul le connecteur part. « Force est de constater que » se
   retire comme une formule de remplissage (motif 23).
5. « D'une part / D'autre part » se garde quand il annonce deux arguments développés ; pour deux
   membres courts, « et » suffit.

Une cause ou une opposition que le texte affirme est un fait : retirer le connecteur ne doit pas la
faire disparaître. Relire ensuite les débuts de phrase du paragraphe entier, car un « mais » ou un «
donc » remis à chaque ligne refait le même tic.

### FR2. Les calques de l'anglais

**Mots à surveiller** : faire du sens, adresser un problème, supporter (au sens de prendre en
charge), initier (au sens de lancer), opportunité (au sens d'occasion), digital (au sens de
numérique), en termes de, basé sur (au sens de fondé sur), au final, définitivement (au sens de
certainement), éventuellement (au sens de finalement), compléter (au sens d'achever).

Le calque passe d'autant mieux que le mot existe en français avec un autre sens. « Il supporte le
format WAV » se lit comme « il le tolère ».

> Le fichier est **restreint au propriétaire** (POSIX `600`) sur les systèmes qui le supportent,
> pour qu'un autre compte de la machine ne puisse pas lire le token.

> Le fichier est **restreint au propriétaire** (POSIX `600`) sur les systèmes qui le prennent en
> charge, pour qu'un autre compte de la machine ne puisse pas lire le token.

**Réécrire** : Vérifier d'abord le sens dans la phrase : le mot n'est fautif que s'il porte le sens
anglais. « Ce travail est définitivement perdu », « une saisie à compléter » ou « éventuellement
accompagné d'un commentaire » au sens de « le cas échéant » sont du français correct et restent.
Sinon, prendre le mot qui a ce sens en français : « occasion » pour opportunité, « numérique » pour
digital, « prendre en charge » ou « accepter » pour supporter, « lancer » pour initier, « fondé sur
» pour basé sur, « finalement » pour au final et pour éventuellement, « certainement » pour
définitivement, « achever » pour compléter, « avoir du sens » pour faire du sens, « traiter » pour
adresser. « En termes de » se défait en nommant ce dont on parle : « un écart en termes d'activité
entre deux passages » devient « un écart d'activité entre deux passages ». Ne pas toucher aux noms
propres, aux URL, aux identifiants de code, aux libellés d'interface, aux citations, dont les
réponses de la plateforme (`422 « invalid field »`), ni aux termes anglais que la documentation
garde et glose à leur première mention (Passive Recorder).

### FR3. Les formules creuses françaises

**Mots à surveiller** : mettre en place, mettre en œuvre, permettre de, dans ce cadre, dans ce
contexte, il convient de, en d'autres termes, à l'ère de, à l'heure de, dans un monde, dans le monde
trépidant de, besoin urgent, en conclusion.

Elles remplacent un verbe précis par une périphrase administrative.

> La mise en place d'un dispositif permettant de procéder à la vérification des accès.

> Un dispositif vérifie les accès.

**Réécrire** : Chercher le verbe que la formule enveloppe et le remettre à sa place, avec son sujet.

1. « Mettre en place », « mettre en œuvre » : prendre le verbe exact que la source donne (créer,
   migrer, téléverser, vérifier). Si elle ne le dit pas, garder la formule plutôt que deviner.
2. « X permet de vérifier » devient « X vérifie » quand X fait l'action lui-même. La tournure reste
   quand X rend l'action possible à quelqu'un (« l'écran de validation vous permet de corriger un
   taxon »).
3. « Dans ce cadre » et « dans ce contexte » se retirent si la phrase se comprend sans eux. S'ils
   renvoient à un cadre que le texte nomme ailleurs (un chantier, une ADR, un protocole), reprendre
   ce nom, sans en inventer un.
4. « Il convient de vérifier » devient « Vérifiez », l'impératif que la documentation emploie pour
   une consigne.
5. « À l'ère de », « dans un monde où », « besoin urgent » : la phrase part si elle ne porte aucun
   fait ; sinon elle commence directement par le fait. « En conclusion » se retire, et la phrase qui
   suit passe le test du motif 38.

### FR4. La nominalisation administrative

Le verbe devient un nom, et la phrase perd son sujet.

> La réalisation de l'analyse des données a permis l'identification des anomalies.

> L'analyse des données a montré les anomalies.

Repérer les suites de noms reliés par « de » : trois « de » dans une phrase courte signalent presque
toujours une nominalisation à défaire.

**Réécrire** : Défaire la chaîne en deux temps. D'abord, retirer les noms supports qui n'ajoutent
aucune action (« la réalisation de », « la mise en œuvre de », « la procédure de ») : « la
réalisation de l'analyse des données » devient « l'analyse des données ». Ensuite, si le texte nomme
qui agit, rendre verbe le nom d'action qui reste, avec cet acteur pour sujet (« la plateforme
analyse les séquences »). Si la source ne le dit pas, ne pas inventer d'acteur : le nom d'action
peut rester sujet (« l'analyse des données a montré les anomalies »), ou la phrase passe à
l'impératif quand c'est une consigne (« Analysez les données »). Garder les « de » qui relient des
choses ou des grandeurs et non des actions : « le nombre de séquences de la nuit du point `A1` » en
a trois et reste tel quel.

### FR5. Le « nous » de commentaire

**Mots à surveiller** : nous avons choisi, nous devons, nous pouvons voir, nous allons, on notera
que, comme nous l'avons vu.

Dans un texte technique de référence, ce « nous » n'a pas de référent : ni l'auteur ni le lecteur.
Écrire ce que le code ou le dispositif fait.

> Nous avons choisi de conserver les fichiers plutôt que de les déplacer.

> Les fichiers sont conservés plutôt que déplacés.

> Nous avons envoyé un champ que le schéma distant ne connaît pas : `422 « invalid field »`.

> Le client a envoyé un champ que le schéma distant ne connaît pas : `422 « invalid field »`.

**Réécrire** : Chercher d'abord qui le « nous » désigne, puis choisir.

1. Le lecteur qui suit une procédure (« nous allons importer la nuit ») : passer au « vous » et à
   l'impératif pour une consigne, comme la documentation utilisateur, ou au « on » impersonnel pour
   décrire un déroulé.
2. Les auteurs du texte (« nous avons choisi de conserver les fichiers ») : mettre en sujet ce que
   le choix concerne, le code, l'écran ou la décision de l'ADR, sans nommer d'auteur que la source
   ne nomme pas.
3. Personne (« comme nous l'avons vu », « on notera que ») : retirer la formule, ou la remplacer par
   un lien vers la section ou l'ADR qui le montre.

Quand la phrase décrit ce que fait l'application, une classe ou la plateforme, la troisième personne
de l'exemple du motif convient.

### FR6. Le style homogène de bout en bout

Le registre ne varie pas : même longueur de phrase, même plan sur chaque section, même densité de
vocabulaire du début à la fin. Un auteur humain fatigue, s'emballe, coupe court.

Ce motif ne se corrige pas mot à mot. Il se corrige en variant réellement : une phrase courte après
trois longues, un paragraphe qui s'arrête plus tôt que prévu, un aparté.

**Et il ne se retient jamais seul.** Beaucoup d'auteurs écrivent naturellement en faible variance,
et ce motif ne les distingue pas d'une sortie de modèle. Voir « Une prose peu variée », parmi les
faux positifs qui portent sur l'auteur.

**Réécrire** : Varier sans rien ajouter : couper en deux une phrase trop longue, fusionner deux
phrases courtes qui disent la même chose, arrêter un paragraphe sur son dernier fait au lieu de le
boucler. Un aparté, une anecdote ou une situation concrète ne s'écrit que si la source en donne la
matière (une issue, une mesure datée, une nuit réelle) ; sinon, demander à l'auteur. Ne jamais
casser une régularité qu'une convention impose pour faire varier le texte, comme les sections
Contexte, Décision et Conséquences d'une ADR.

---

# Les traces d'outil

**Ces six-là ne se lisent pas comme les autres.** Partout ailleurs la grille demande un groupe : un
mot ne prouve rien, c'est l'empilement qui parle. Ici une seule occurrence conclut, parce que ces
chaînes n'existent nulle part ailleurs que dans la sortie d'un outil, ou parce qu'elles sont le
résidu d'un geste qui n'aurait pas dû aboutir.

Elles ne disent pas qu'un texte est mauvais. Elles disent qu'il a été collé sans être relu, ce qui
est une autre information et souvent plus utile.

### T1. Les marques de citation d'assistant

**À chercher** : `citeturn0search0`, `contentReference[oaicite:0]{index=0}`, `oai_citation`,
`[cite: 1]`, `[span_1](start_span)`, `grok_card`, `grok_render_citation_card_json`,
`attributableIndex`, `attached_file`, `ppl-ai-file-upload`, les crochets lenticulaires isolés.

Des jetons internes que l'interface d'un assistant rend invisibles et que le presse-papier emporte.
Aucun n'a de raison d'exister dans un texte écrit. Les retirer, et rétablir une vraie référence si
la citation comptait.

**Réécrire** : Retirer le jeton entier, avec ses crochets et l'espace qui le précède, puis relire la
ponctuation autour. Le jeton seul ne donne pas la source : il renvoie à une recherche ou à un
fichier de la session d'origine. Si la phrase s'appuyait sur cette citation, chercher la référence
dans les sources collées avec le texte, dans l'issue ou l'ADR qu'il cite, ou la demander à l'auteur,
puis ouvrir la page et vérifier qu'elle dit bien ce que la phrase affirme. Faute de référence, la
phrase reste sans source et le manque se signale dans le résumé rendu. Une référence qui paraît
plausible ne se reconstitue jamais, numéro d'issue ou d'ADR compris.

### T2. Les paramètres de suivi accrochés aux liens

**À chercher** : `utm_source=chatgpt.com`, `utm_source=openai`, `utm_source=copilot.com`,
`utm_source=perplexity`, `referrer=grok.com`.

Plusieurs assistants marquent les liens qu'ils rendent. Le paramètre suit le lien dans le
copier-coller, et il part sur la forge avec. Le retirer : il ne sert qu'à celui qui l'a posé.

**Réécrire** : Retirer le paramètre avec un seul de ses séparateurs : `?utm_source=chatgpt.com&id=4`
devient `?id=4`, et un `?` qui ne précède plus rien part aussi. Les autres paramètres restent, car
certains font marcher le lien. La cible du lien change donc, par exception à « Comment rendre le
résultat », qui la laisse d'ordinaire telle quelle. Le paramètre dit aussi que l'adresse vient d'un
assistant : ouvrir le lien, vérifier que la page existe et dit ce que la phrase lui prête, et sinon
demander la bonne adresse plutôt que d'en deviner une. Un `utm_` ajouté par un bouton de partage
n'est pas une trace d'assistant et reste.

### T3. Les caractères invisibles

**À chercher** : U+200B espace sans chasse, U+200C antiliant, U+200D liant, U+FEFF marque d'ordre,
U+00AD trait conditionnel, U+2060 sans coupure.

Ils ne s'affichent pas, cassent une recherche, coupent un mot au milieu, et se recopient sans qu'on
les voie. Ils arrivent par un collage, jamais par une frappe.

**Deux emplois légitimes existent, et ils se déclarent.** Le liant U+200D compose les séquences
d'emoji, `👨‍🔬` en portant un. Et un caractère cité plutôt qu'employé reste, comme la constante `BOM`
d'un analyseur de fichiers. Hors de ces deux cas, c'est un résidu.

**Réécrire** : Les chercher par leur code, puisque l'éditeur ne les montre pas : `grep -nP
'[\x{200B}\x{200C}\x{200D}\x{FEFF}\x{00AD}\x{2060}]' fichier`. Supprimer chaque occurrence hors des
deux emplois légitimes, y compris dans un bloc de code ou une cible de lien, que la réécriture
laisse sinon intacts. Quand le caractère séparait deux mots sans autre espace, mettre une espace
ordinaire à sa place, sinon les mots se soudent. Relancer la même recherche : elle ne doit plus
rendre que les emplois légitimes, comme la constante `BOM` de `ParserCsvTadarida` ou le liant des
pictogrammes de personas dans `brief/`. Chercher ensuite par une recherche ordinaire un mot qui
contenait le caractère : il doit se trouver. Le rendu de la page Markdown ou de la javadoc ne doit
pas changer.

### T4. Les homoglyphes

**À chercher** : une lettre cyrillique ou grecque au milieu d'un mot latin, `а е о р с х у і`,
`ο Α`.

Le mot se lit normalement et ne se trouve plus. C'est le résidu d'un collage depuis une source
mêlée, et c'est aussi la manoeuvre connue pour tromper un détecteur, ce qui rend sa présence dans un
texte de travail d'autant plus douteuse.

**Réécrire** : Les chercher par plage de codes, puisqu'à l'oeil ils ne se distinguent pas : `grep
-nP '[\x{0370}-\x{03FF}\x{0400}-\x{04FF}]' fichier`. La forme `\p{Greek}` ne convient pas, car avec
PCRE2 elle ramène aussi le point médian `·`, celui de la formule de `SonDemo` par exemple. Ouvrir
chaque ligne, remplacer la lettre par la lettre latine qu'elle imite (le `а` cyrillique par `a`, le
`ο` grec par `o`), puis vérifier qu'une recherche ordinaire retrouve le mot. Un mot grec ou russe
cité comme tel reste, et une lettre grecque employée comme symbole aussi, puisqu'elle n'imite aucune
lettre latine : le `ΔT` de la javadoc d'`EphemerideSolaire`, le `2π` de `SonDemo`. Dans un
identifiant Java ou une URL, corriger toutes les occurrences, relancer `./mvnw test` ou ouvrir le
lien, et le dire dans le résumé rendu : la correction change ce que la machine lit.

### T5. Le gabarit non rempli

**À chercher** : `[Votre nom]`, `[INSÉRER L'URL]`, `[À COMPLÉTER]`, `2026-XX-XX`, `XXXX`, toute
instruction restée entre crochets.

Personne ne publie sciemment un texte à trous. Le remplir, ou retirer la ligne : les deux valent
mieux que de la laisser, et laisser est ce qui arrive.

**Réécrire** : Regarder d'abord à qui le trou s'adresse. Un blanc que le lecteur doit remplir fait
partie du contenu et reste : le marque-place d'un secret dans une commande
(`-Dvigiechiro.token=XXXX`), un paramètre à fournir (`<id-participation>`), un `XXXXXX` qui désigne
la partie variable d'un nom (`CarXXXXXX-AAAA-PassN-YY-`). Un trou laissé par le rédacteur se traite
en trois gestes :

1. Chercher la valeur dans l'issue que le texte cite, ou dans l'ADR ou le code dont il parle.
2. Si elle s'y trouve, la mettre à la place du gabarit, crochets compris.
3. Sinon, la demander. Si l'auteur répond que le trou attend une mesure ou un lien, le garder et le
   signaler dans le résumé rendu : le manque est alors un fait. Sinon, réécrire la phrase sans le
   trou en gardant ce qu'elle affirme, ou retirer la ligne quand elle ne portait que le trou, comme
   « Relu par : [Votre nom] » au bas d'un corps de PR.

Ne jamais mettre une valeur vraisemblable, une date, une adresse ou un numéro d'issue devinés. La
même trace existe en toutes lettres, comme « (numéro de l'issue : à compléter) », et se traite de la
même façon.

### T6. Le raisonnement laissé dans le texte

**Mots à surveiller** : Étape 1 :, D'abord je vais, Décomposons, Voyons voir, Analysons cela,
Réfléchissons, une numérotation qui décrit une démarche plutôt qu'un contenu.

L'échafaudage du raisonnement survit dans le texte rendu.

**Il se distingue du motif 28**, et les deux se confondent facilement puisque « décortiquons »
figure dans les deux listes. Le motif 28 est une figure adressée au lecteur, qui annonce ce qui
vient. T6 est du texte qui ne lui était **pas destiné** : il décrit le procédé, pas le sujet.
Devant un cas douteux, la question qui tranche est de savoir à qui la phrase parle.

**Réécrire** : garder la conclusion, dans la voix de l'auteur, et retirer la marche qui y menait.

---

# Ce que cette grille ne peut pas voir

Une grille de motifs mesure sa **précision**, jamais son **rappel**. Lire les lignes qu'un motif
désigne dit combien sont fautives ; cela ne dit rien de celles qu'aucun motif ne désigne.

Ce qui échappe n'est pas lexical, c'est **relationnel** :

- une documentation qui paraphrase la signature sans rien ajouter : « Cette méthode permet
  d'effectuer le calcul de la richesse spécifique au sein d'une zone délimitée » au-dessus de
  `calculerRichesseSpecifique(Zone)` ;
- une garantie annoncée que le code ne tient pas : « garantit la cohérence des détections » pour une
  méthode qui trie une liste ;
- une unité qui ne correspond pas : `@return la fréquence en Hz` sur un calcul en kHz ;
- un comportement décrit qui n'existe pas : une gestion d'exception ou un traitement asynchrone
  absents du code.

Aucune de ces lignes ne porte un mot suspect. Le défaut est dans l'écart entre le texte et ce qu'il
décrit, et il faut les lire ensemble.

La même limite vaut pour les traces **distribuées** : un plan identique sur chaque section, des
synonymes alternés mécaniquement, un commentaire complet sur ce qui est trivial et muet sur ce qui
est difficile. Chaque occurrence isolée paraît correcte ; c'est leur répétition qui est le signe.

**Une manoeuvre attrape la seconde : permuter deux paragraphes.** Prendre le deuxième et le
quatrième d'une section, les échanger, relire. Si rien ne casse, le texte pose des blocs autonomes
au lieu de dérouler un raisonnement, et chaque paragraphe recommence au lieu de continuer. Un texte
qui tient a un ordre, et la permutation le fait voir sans qu'aucun mot ne soit suspect.

Ce n'est pas un motif, c'est un geste : rien ne le déclenche, il faut décider de l'essayer.

Un échantillon **non déclenché par les motifs**, tiré au hasard et lu contre le sujet, est le seul
moyen de voir ces deux familles.

---

# Les faux positifs

Aucun élément ci-dessous ne prouve quoi que ce soit à lui seul.

- **Une grammaire parfaite.** Beaucoup de gens écrivent bien, ou sont relus.
- **Un style mêlant familier et soutenu.** Cela tient au métier, à l'âge, aux habitudes.
- **Une prose plate.** L'écriture générée a des marques précises. La platitude sans ces marques est
  de la platitude.
- **Un mot soutenu isolé.** La règle 7 vise des groupes, pas chaque mot rare.
- **Un connecteur isolé.** Un « cependant » ne dit rien. C'est l'empilement qui parle.
- **Un cadratin isolé.** Beaucoup de rédacteurs en emploient. Il compte avec d'autres marques.
- **Une phrase courte pour appuyer.** Le fragment se signale en série.
- **Une ouverture répétée voulue.** « Elle vint. Elle vit. Elle vainquit. »
- **Un « honnêtement » ou un « au fond » au milieu d'une phrase.** Le tic est l'ouverture théâtrale.
- **Les limites et avertissements utiles.** Un périmètre, une mention légale, une correction réelle,
  une objection nommée et une réponse de foire aux questions se gardent.
- **Une alternative réelle.** Dans un document de conception, le lecteur peut vraiment l'envisager.
- **L'absence de sources.** La plupart des textes n'en portent pas.
- **Un formatage propre.** Les éditeurs visuels en produisent sans aucune IA.
- **Une tournure citée.** Ne jamais réécrire un motif à l'intérieur d'une citation, d'un titre
  d'œuvre, d'un nom propre ou d'un exemple qui sert précisément à le montrer.
- **Un terme du domaine qui ressemble à un tic.** « La richesse d'un carré » est un nombre d'espèces,
  pas de la publicité. Ouvrir les lignes avant de retenir un motif.

## Trois faux positifs qui portent sur l'auteur, et non sur le texte

Les quinze ci-dessus disent qu'un passage ne prouve rien. Les trois qui suivent disent autre chose.
Un faux positif de texte retire un motif du compte ; un faux positif d'auteur empêche le compte de
conclure.

- **Une prose peu variée.** L'alternance des longueurs de phrase est un signal usuel, et beaucoup
  d'auteurs autistes ou TDAH écrivent naturellement en faible variance, avec un plan régulier et un
  vocabulaire stable. Aucune heuristique de variance ne sépare cette voix d'une sortie de modèle.
  La faible variance ne fait donc jamais monter un verdict à elle seule : il faut les marques
  lexicales et celles du contenu, et il faut les avoir lues.

- **Un français scolaire, ou de langue seconde.** Les détecteurs entraînés sur de l'anglais natif
  sur-signalent les auteurs non natifs
  ([Liang et al., arXiv:2304.02819](https://arxiv.org/abs/2304.02819)), et la même prudence vaut
  ici. Un registre appliqué, des tournures apprises et une syntaxe prudente sont la voix honnête de
  quelqu'un qui écrit dans une langue qui n'est pas la sienne. Le relever comme un tic revient à
  refuser un texte pour la langue de qui l'a écrit.

- **Un échantillon trop court.** Sous une quarantaine de mots, il n'y a pas de quoi conclure : cette
  grille mesure des taux et des groupes, et ni l'un ni l'autre n'existe sur trois phrases. Le dire,
  plutôt que de trancher au hasard.

## Ce qui porte la voix et se garde

- **Un détail précis et inattendu** : une adresse réelle, une citation étrange, « l'avocat qui
  travaillait au-dessus de mon dentiste ».
- **Un sentiment mêlé, non résolu** : « je crois que c'est plutôt bien, mais ça me gêne, et je ne
  sais pas dire pourquoi ».
- **Une référence datée** : un mot d'argot, une allusion qui situe une année et un milieu.
- **Un choix de première personne assumé**, quand l'auteur peut dire pourquoi il est là.
- **La variété des longueurs de phrase.** L'écriture réelle alterne.
- **Un aparté ou une autocorrection** : « (je veux écrire "presque" à chaque fois, mais c'était
  certain) ».

---

# Comment rendre le résultat

**Texte collé, par défaut.** Rendre le brouillon, la liste courte des motifs qui restent, puis la
version finale.

**Fichier nommé.** Faire la réécriture complète mais n'écrire que le texte final dans le fichier. Ne
toucher qu'à la prose : les blocs de code, les en-têtes de métadonnées, les données et les cibles de
liens restent tels quels. Rendre ensuite un résumé court.

**Appel depuis une autre tâche** (message de commit, corps de demande de fusion, document) : rendre
le texte final seul.

## La relecture finale

1. Relire à voix haute. Vérifier le rythme, les verbes simples, le niveau de langue.
2. Se poser deux questions : **qu'est-ce qui sonne encore artificiel ?** et **la réécriture a-t-elle
   ajouté ou retiré un fait, un nom, un chiffre, une date, une citation ou une source ?** Toute
   addition non appuyée et toute perte sont des erreurs.
3. Chercher `—`, `–`, et les apostrophes de l'autre forme. Les retirer, sauf si l'échantillon de
   l'auteur les emploie.
4. Relire le texte produit contre la grille : une réécriture écrit souvent ses propres tics, et
   l'aphorisme de clôture est celui qui revient le plus.

## Source

Trente-cinq motifs de [« Signs of AI writing »](https://en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing),
tenu par WikiProject AI Cleanup. Six motifs français de
[« Aide:Identifier l'usage d'une IA générative »](https://fr.wikipedia.org/wiki/Aide:Identifier_l%27usage_d%27une_IA_g%C3%A9n%C3%A9rative)
et de trois relevés de tics de ChatGPT en français
([Daria décrypte l'IA](https://dariadecrypteia.substack.com/p/les-tics-de-langage-de-chatgpt),
[Flint](https://generationia.flint.media/p/dejouer-tics-langage-chatgpt),
[Projet Voltaire](https://www.projet-voltaire.fr/ressources/detecter-texte-chatgpt-ia-generative/)).

Treize motifs, 36 à 42 et T1 à T6, viennent de l'[humaniseur anglais
d'Aboudjem](https://github.com/Aboudjem/humanizer-skill), sous licence MIT. L'ouverture énumérative
prudente y est adossée au corpus HC3 ([arXiv:2301.07597](https://arxiv.org/abs/2301.07597)), et les
deux avertissements sur l'auteur à [Liang et al.](https://arxiv.org/abs/2304.02819).

Ce que ce dépôt en retient comme règles opposables, et les mesures qui les fondent, sont dans la
section « Le registre » de [CONTRIBUTING.md](../../../CONTRIBUTING.md). Sept tics y sont retenus sur
les cinquante-quatre de cette grille : ils servent à refuser une relecture, quand la grille sert à
relire.

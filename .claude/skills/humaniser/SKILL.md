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

### 2. Les noms cités pour prouver l'importance

**Mots à surveiller** : reprise dans les médias nationaux, couverture indépendante, ouvrage rédigé
par un expert reconnu, forte présence sur les réseaux sociaux, suivi par des milliers de.

Une liste de titres de presse ou un nombre d'abonnés remplace ce que la personne a dit. Garder la
citation quand la source dit **quoi** et **où** ; ne jamais fabriquer ce contexte pour raccourcir.

### 3. L'analyse creuse en participe présent

**Mots à surveiller** : soulignant, mettant en évidence, permettant ainsi, garantissant, reflétant,
témoignant de, illustrant, favorisant, contribuant à, englobant, assurant ainsi.

Le participe présent ajoute une profondeur que le fait n'a pas.

> La palette bleu, vert et or du temple résonne avec la beauté naturelle de la région, symbolisant
> les lupins du Texas et le golfe du Mexique, reflétant l'attachement de la communauté à sa terre.

> Le temple est peint en bleu, vert et or, couleurs choisies pour évoquer les lupins du Texas et le
> golfe du Mexique.

### 4. La langue de la brochure

**Mots à surveiller** : niché au cœur de, véritable, riche (au figuré), authentique, incontournable,
emblématique, à couper le souffle, cadre exceptionnel, savoir-faire, engagement, fleuron, joyau.

Le texte décrit un lieu, un produit ou une organisation comme une plaquette publicitaire.

> Niché au cœur de la magnifique région du Gondar en Éthiopie, Alamata Raya Kobo est une ville
> vibrante au riche patrimoine culturel et à la beauté naturelle saisissante.

> Alamata Raya Kobo est une ville de la région du Gondar, en Éthiopie.

### 5. La source vague

**Mots à surveiller** : les experts estiment, certains observateurs, plusieurs sources, des études
montrent, il est admis que, des rapports du secteur, la communauté scientifique s'accorde.

Une affirmation est attribuée à des experts, des critiques ou des rapports que personne ne nomme.
Nommer la source réelle quand le texte source en donne une ; sinon retirer l'affirmation. Ne jamais
inventer de source.

### 6. La section « défis et perspectives »

**Mots à surveiller** : malgré ces défis, n'est pas exempt de difficultés, Défis et héritage,
Perspectives d'avenir, continue de se développer, l'avenir s'annonce.

Une section finale répète des généralités au lieu d'ajouter des faits. Elle se retire, sauf si elle
porte des dates ou des actes que la source établit.

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

### 8. Le verbe simple évité

**Mots à surveiller** : constitue, se veut, fait office de, revêt, s'avère, représente un, demeure
un, se présente comme, dispose de, se compose de.

*Être*, *avoir* et *faire* sont remplacés par des périphrases.

> La galerie 825 se veut l'espace d'exposition de la LAAA. Elle dispose de quatre salles et fait
> office de vitrine sur plus de 280 m².

> La galerie 825 est l'espace d'exposition de la LAAA. Elle a quatre salles, soit 280 m².

### 9. L'antithèse et la chute tronquée

**Mots à surveiller** : ce n'est pas X, c'est Y ; non seulement X mais aussi Y ; bien plus qu'un X,
un véritable Y ; loin d'être un simple X.

L'antithèse ne se justifie que si un lecteur pouvait vraiment confondre les deux termes. Quand
personne n'a proposé Y, elle emballe.

> Ce n'est pas qu'une chanson, c'est une déclaration. Ce n'est pas le rythme sous la voix, c'est
> l'atmosphère elle-même.

> Le rythme appuyé renforce le ton agressif.

La chute tronquée est la même faute en plus court : « les options viennent de l'élément choisi, pas
de devinette » se réécrit « les options viennent de l'élément choisi, sans que l'utilisateur ait à
deviner ».

### 10. Le tricolon forcé

Trois membres dont le troisième n'ajoute rien, pour faire complet.

> L'événement propose des conférences, des tables rondes et des occasions de réseautage. Les
> participants y trouveront innovation, inspiration et perspectives.

> L'événement propose des conférences et des tables rondes, avec du temps libre entre les sessions.

### 11. Le renommage et l'ouverture répétée

Le même sujet reçoit trois noms successifs, ou trois phrases s'ouvrent sur le même mot. Employer un
seul nom ; pour les ouvertures, fusionner les phrases ou commencer par l'action.

> Le protagoniste affronte des épreuves. Le personnage principal doit surmonter des obstacles. Le
> héros finit par triompher.

> Le protagoniste affronte des épreuves, puis finit par triompher.

La répétition voulue se garde : « Elle vint. Elle vit. Elle vainquit. »

### 12. La fausse gamme

**Mots à surveiller** : de X à Y, de Z à W, quand X et Y ne forment pas un intervalle.

> Notre voyage nous a menés de la singularité du Big Bang à la toile cosmique, de la naissance des
> étoiles à la danse énigmatique de la matière noire.

> Le livre traite du Big Bang, de la formation des étoiles et des théories sur la matière noire.

### 13. Le passif et le sujet absent

**Mots à surveiller** : il est procédé à, il convient d'effectuer, aucune configuration n'est
requise, les résultats sont conservés.

Qui agit disparaît. Rendre l'acteur quand cela éclaire.

> Aucun fichier de configuration n'est requis. Les résultats sont conservés automatiquement.

> Vous n'avez pas besoin de fichier de configuration. Le système conserve les résultats.

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

### 40. Le narrateur à distance

**Mots à surveiller** : on a tendance à, les gens ont l'habitude de, il y a comme un sentiment que,
d'aucuns pensent, on pourrait dire que, il est de coutume de.

Le texte survole une scène où personne n'est nommé, et le lecteur n'y est pas non plus. Mettre le
lecteur dedans, ou nommer qui fait la chose.

> On a tendance à négliger la relecture des captures.

> Une capture régénérée se referme sans être ouverte, et le défaut passe.

Ce motif est proche du FR5, et les deux se séparent nettement : FR5 vise le « nous » sans référent,
celui-ci vise le **tiers** indéfini.

## Forme

### 14. Le tiret cadratin et le tiret demi-cadratin

Le texte final ne porte ni `—` ni `–`, sauf si l'échantillon de l'auteur en emploie. Les remplacer
par un point, une virgule, un deux-points ou une parenthèse. Surveiller aussi le tiret entouré
d'espaces et le double trait d'union employé comme tiret.

En français, le deux-points fait presque toujours le travail du cadratin, et il se lit mieux.

### 15. Le gras à répétition

Le gras marque ce qui se lit de travers sans lui. Quand une ligne sur trois en porte, il ne marque
plus rien.

Distinguer deux emplois avant de couper. Le gras **code** quelque chose quand il désigne un libellé
d'interface, un nom de colonne, un terme du domaine défini, une contrainte forte, ou qu'il ouvre un
paragraphe en énonçant sa règle. Il **emballe** quand il tombe sur un mot ordinaire : « deux chemins
**distincts** », « un critère **booléen** ». Seul le second se retire.

Aucun contrôle mécanique ne fait cette différence : la syntaxe voit le gras, pas son utilité.

### 16. La liste à mini-titres gras

Une liste dont chaque entrée ouvre sur un mot en gras suivi d'un deux-points est un paragraphe
qu'on a découpé.

> - **Expérience utilisateur** : l'interface a été significativement améliorée.
> - **Performance** : les temps de chargement ont été optimisés.
> - **Sécurité** : le chiffrement de bout en bout a été renforcé.

> La mise à jour revoit l'interface, accélère le chargement et ajoute le chiffrement de bout en
> bout.

Un cas reste légitime : l'étiquette **structurelle** d'un document de référence, répétée à
l'identique sur chaque entrée, qui sépare deux natures de contenu. Cette page en emploie une,
« Mots à surveiller », pour distinguer le lexique de l'explication. Le critère est la répétition
identique et la fonction : une étiquette qui change à chaque entrée n'en est pas une, c'est un
paragraphe découpé.

### 17. La capitale à chaque mot d'un titre

Le français ne capitalise que le premier mot et les noms propres. Un titre entièrement capitalisé
est un calque de l'anglais, et il se voit d'autant plus qu'il est rare.

> ## Négociations Stratégiques Et Partenariats Mondiaux

> ## Négociations stratégiques et partenariats mondiaux

Attention aux faux positifs : un titre plein d'acronymes ou de noms de produit paraît capitalisé
sans l'être.

### 18. Les pictogrammes décoratifs

Un emoji posé devant un titre ou une puce comme ornement. Le retirer. Un pictogramme qui **porte**
une information est autre chose, et il se juge sur ce qu'il porte.

> 🚀 **Lancement** : le produit sort au troisième trimestre
> 💡 **Enseignement** : les utilisateurs préfèrent la simplicité

> Le produit sort au troisième trimestre. L'étude montre une préférence pour la simplicité.

### 19. Les guillemets et l'apostrophe

En anglais, le tic est le guillemet courbe là où l'auteur écrit droit. En français, la question se
pose autrement : les guillemets français `« »` sont la forme attendue, et le tic est le **mélange**
des formes dans un même texte.

L'apostrophe suit la même règle : choisir la droite `'` ou la courbe `’`, et ne jamais mêler les
deux. Un mélange se voit surtout quand le texte alimente une interface, où deux écrans affichent
alors deux apostrophes différentes.

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

### 42. Le gras là où il ne rend pas

Du `**gras**` dans un support qui n'affiche pas le Markdown : un message de commit, un libellé
d'interface ou de ligne de commande, un courriel en texte brut. Les astérisques s'y lisent
littéralement.

> git commit -m "corrige le **plafond** de la fenêtre"

> git commit -m "corrige le plafond de la fenêtre"

Distinct du motif 15, qui vise le gras **de trop** là où il s'affiche. Ici il s'affiche mal, et la
question de son utilité ne se pose même pas.

## Restes de conversation

### 20. Le texte d'assistant laissé dans la réponse

**Mots à surveiller** : j'espère que cela vous aidera, bien sûr !, tout à fait !, vous avez
parfaitement raison, souhaitez-vous que, voulez-vous que je, dois-je continuer, n'hésitez pas,
voici un, voici la liste.

> Voici un aperçu de la Révolution française. J'espère que cela vous aidera ! N'hésitez pas si vous
> voulez que je développe une section.

> La Révolution française commence en 1789, quand la crise financière et les pénuries alimentaires
> provoquent des troubles.

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

### 22. Le ton complaisant

**Mots à surveiller** : excellente question, vous avez tout à fait raison, c'est un point très
pertinent, quelle bonne idée.

> Excellente question ! Vous avez tout à fait raison, c'est un sujet complexe. Votre remarque sur
> les facteurs économiques est très pertinente.

> Les facteurs économiques que vous mentionnez comptent ici.

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

### 24. L'empilement de nuances

**Mots à surveiller** : pour être honnête, il se pourrait que, potentiellement, on pourrait
éventuellement, dans certains cas il se peut que, cela reste une hypothèse.

Une relecture après l'autre ajoute un correctif, jusqu'à ce que plus rien ne soit affirmé. Garder la
nuance que la source appuie et que le sens réclame ; retirer celles qui ne font que rattraper une
exagération précédente.

> On pourrait éventuellement soutenir que la mesure aurait peut-être un certain effet.

> La mesure peut avoir un effet.

### 25. La clôture optimiste

Le texte se termine sur un souhait au lieu du dernier fait utile.

> L'avenir s'annonce prometteur pour l'entreprise. De belles perspectives se dessinent sur le chemin
> de l'excellence.

Retirer le paragraphe. Si la source donne des projets réels, écrire les projets.

### 26. Les paires à trait d'union

**En anglais** : data-driven, cross-functional, end-to-end, real-time, long-term.

En français, le tic prend une autre forme : le calque non traduit (« une approche data-driven »,
« un suivi end-to-end », « une solution clé-en-main ») là où un mot français existe. Le remplacer.

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

## Rhétorique

### 27. La fausse révélation

**Mots à surveiller** : au fond, en réalité, la vraie question est, ce qui compte vraiment,
fondamentalement, le véritable enjeu, le cœur du sujet, tout l'enjeu est là.

Une remarque ordinaire est présentée comme une vérité cachée.

> La vraie question est de savoir si les équipes peuvent s'adapter. Au fond, ce qui compte vraiment,
> c'est la maturité de l'organisation.

> La question est de savoir si les équipes peuvent s'adapter, ce qui dépend surtout de la capacité
> de l'organisation à changer ses habitudes.

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

### 30. La version précédente racontée

**Mots à surveiller** : auparavant, jusqu'ici, jadis, naguère, autrefois, à l'époque, avant elle,
anciennement, désormais, dorénavant.

Une documentation et un commentaire décrivent le comportement d'aujourd'hui. Le passé a ses lieux :
l'historique de version, le journal des changements, la section des alternatives écartées d'une
décision d'architecture.

> Cette fonction a été ajoutée pour remplacer l'ancienne approche qui parcourait tous les éléments,
> ce qui coûtait O(n²).

> Cette fonction emploie une table de hachage, avec une recherche en O(1).

### 31. La chute dramatique et le fragment

Chaque phrase devient une clôture. Une phrase courte appuie ; une suite de fragments courts sonne
faux.

> Puis AlphaEvolve est arrivé. Aucune préférence pour la symétrie. Aucun a priori esthétique. Aucune
> nostalgie du goût humain. Les anciennes règles n'existaient plus.

> AlphaEvolve a changé la recherche parce qu'il ne privilégiait ni la symétrie ni les formes
> familières, ce qui a rendu certaines hypothèses anciennes moins utiles.

### 32. La formule toute faite

**Mots à surveiller** : X est le Y de Z, X devient un piège, X n'est pas un outil mais un miroir,
le langage de, la monnaie de, l'architecture de, le nerf de la guerre.

Une affirmation ordinaire prend la forme d'un aphorisme et perd son détail.

> La symétrie est le langage de la confiance. L'efficacité devient un piège quand les équipes
> oublient la couche humaine.

> Une disposition symétrique paraît souvent plus prévisible. Les équipes peuvent optimiser un
> processus au point de perdre de vue son usage réel.

Attention aux faux positifs : « l'abscisse est le nombre de minutes depuis 18 h » est du français
ordinaire, pas une formule.

### 33. La fausse candeur

**Mots à surveiller** : honnêtement ?, soyons clairs, disons-le, le fait est, voici la chose, pour
être franc, entre nous.

Le tic est l'ouverture théâtrale, pas le mot. « Dire honnêtement de quand il date » n'a rien à voir.

> Est-ce que ça vaut le prix ? Honnêtement ? Ça dépend de la fréquence d'usage.

> Que cela vaille le prix dépend de la fréquence d'usage.

### 34. L'objection que personne n'a soulevée

**Mots à surveiller** : ce n'est pas tant une question de, je ne dis pas que, qu'on ne s'y trompe
pas, loin de moi l'idée de, on pourrait croire que, certains diront que.

Le texte répond à une objection qui n'apparaît nulle part ailleurs. Retirer la défense ; si elle
porte une affirmation réelle, écrire l'affirmation.

Garder l'objection quand le texte nomme qui la porte, ou qu'il y répond entièrement. Une affirmation
directe comme « cette API n'est pas thread-safe » n'est pas ce motif.

### 35. La fausse alternative écartée

**Mots à surveiller** : une solution tentante serait, on aurait pu, il serait tentant de, une
approche évidente consisterait à, on pourrait penser que, certains suggéreraient.

Une option que personne n'envisagerait est introduite, écartée en une proposition, puis jamais
reprise. C'est souvent le reste d'une version antérieure du texte.

> Les jetons de session sont renouvelés toutes les 24 heures. Une solution tentante serait de
> redémarrer le service d'authentification par une tâche planifiée, mais cela couperait toutes les
> sessions actives. Le renouvellement se fait en place.

> Les jetons de session sont renouvelés en place toutes les 24 heures, et les clients rafraîchissent
> sans interruption.

Une alternative réelle se garde : dans un document de conception ou un tutoriel, le lecteur peut
vraiment l'envisager. Plusieurs rejets courts et sans suite dans le même texte sont le signal.

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

### FR2. Les calques de l'anglais

**Mots à surveiller** : faire du sens, adresser un problème, supporter (au sens de prendre en
charge), initier (au sens de lancer), opportunité (au sens d'occasion), digital (au sens de
numérique), en termes de, basé sur (au sens de fondé sur), au final, définitivement (au sens de
certainement), éventuellement (au sens de finalement), compléter (au sens d'achever).

Le calque passe d'autant mieux que le mot existe en français avec un autre sens. « Il supporte le
format WAV » se lit comme « il le tolère ».

### FR3. Les formules creuses françaises

**Mots à surveiller** : mettre en place, mettre en œuvre, permettre de, dans ce cadre, dans ce
contexte, il convient de, en d'autres termes, à l'ère de, à l'heure de, dans un monde, dans le monde
trépidant de, besoin urgent, en conclusion.

Elles remplacent un verbe précis par une périphrase administrative.

> La mise en place d'un dispositif permettant de procéder à la vérification des accès.

> Un dispositif vérifie les accès.

### FR4. La nominalisation administrative

Le verbe devient un nom, et la phrase perd son sujet.

> La réalisation de l'analyse des données a permis l'identification des anomalies.

> L'analyse des données a montré les anomalies.

Repérer les suites de noms reliés par « de » : trois « de » dans une phrase courte signalent presque
toujours une nominalisation à défaire.

### FR5. Le « nous » de commentaire

**Mots à surveiller** : nous avons choisi, nous devons, nous pouvons voir, nous allons, on notera
que, comme nous l'avons vu.

Dans un texte technique de référence, ce « nous » n'a pas de référent : ni l'auteur ni le lecteur.
Écrire ce que le code ou le dispositif fait.

> Nous avons choisi de conserver les fichiers plutôt que de les déplacer.

> Les fichiers sont copiés, jamais déplacés.

### FR6. Le style homogène de bout en bout

Le registre ne varie pas : même longueur de phrase, même plan sur chaque section, même densité de
vocabulaire du début à la fin. Un auteur humain fatigue, s'emballe, coupe court.

Ce motif ne se corrige pas mot à mot. Il se corrige en variant réellement : une phrase courte après
trois longues, un paragraphe qui s'arrête plus tôt que prévu, un aparté.

**Et il ne se retient jamais seul.** Beaucoup d'auteurs écrivent naturellement en faible variance,
et ce motif ne les distingue pas d'une sortie de modèle. Voir « Une prose peu variée », parmi les
faux positifs qui portent sur l'auteur.

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

### T2. Les paramètres de suivi accrochés aux liens

**À chercher** : `utm_source=chatgpt.com`, `utm_source=openai`, `utm_source=copilot.com`,
`utm_source=perplexity`, `referrer=grok.com`.

Plusieurs assistants marquent les liens qu'ils rendent. Le paramètre suit le lien dans le
copier-coller, et il part sur la forge avec. Le retirer : il ne sert qu'à celui qui l'a posé.

### T3. Les caractères invisibles

**À chercher** : U+200B espace sans chasse, U+200C antiliant, U+200D liant, U+FEFF marque d'ordre,
U+00AD trait conditionnel, U+2060 sans coupure.

Ils ne s'affichent pas, cassent une recherche, coupent un mot au milieu, et se recopient sans qu'on
les voie. Ils arrivent par un collage, jamais par une frappe.

**Deux emplois légitimes existent, et ils se déclarent.** Le liant U+200D compose les séquences
d'emoji, `👨‍🔬` en portant un. Et un caractère cité plutôt qu'employé reste, comme la constante `BOM`
d'un analyseur de fichiers. Hors de ces deux cas, c'est un résidu.

### T4. Les homoglyphes

**À chercher** : une lettre cyrillique ou grecque au milieu d'un mot latin, `а е о р с х у і`,
`ο Α`.

Le mot se lit normalement et ne se trouve plus. C'est le résidu d'un collage depuis une source
mêlée, et c'est aussi la manoeuvre connue pour tromper un détecteur, ce qui rend sa présence dans un
texte de travail d'autant plus douteuse.

### T5. Le gabarit non rempli

**À chercher** : `[Votre nom]`, `[INSÉRER L'URL]`, `[À COMPLÉTER]`, `2026-XX-XX`, `XXXX`, toute
instruction restée entre crochets.

Personne ne publie sciemment un texte à trous. Le remplir, ou retirer la ligne : les deux valent
mieux que de la laisser, et laisser est ce qui arrive.

### T6. Le raisonnement laissé dans le texte

**Mots à surveiller** : Étape 1 :, D'abord je vais, Décomposons, Voyons voir, Analysons cela,
Réfléchissons, une numérotation qui décrit une démarche plutôt qu'un contenu.

L'échafaudage du raisonnement survit dans le texte rendu. Garder la conclusion, dans la voix de
l'auteur, et retirer la marche qui y menait.

**Il se distingue du motif 28**, et les deux se confondent facilement puisque « décortiquons »
figure dans les deux listes. Le motif 28 est une figure adressée au lecteur, qui annonce ce qui
vient. T6 est du texte qui ne lui était **pas destiné** : il décrit le procédé, pas le sujet.
Devant un cas douteux, la question qui tranche est de savoir à qui la phrase parle.

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

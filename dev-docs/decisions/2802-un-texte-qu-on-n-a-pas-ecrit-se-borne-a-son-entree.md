# ADR 2802 - Un texte qu'on n'a pas écrit se borne à son entrée

- **Statut** : Accepté - 2026-07-29
- **Chantier** : #2802 (suites de la clôture de l'EPIC #2350, passe 3)
- **Vérification** : certaine - `MessageExterneBorneTest#aucun_retour_ne_renvoie_un_message_nu`
  (une garde par surface : voir aussi `aucune_commande_n_affiche_un_refus_sans_son_geste` pour la CLI)

## Contexte

Dix-sept appels affichaient dans le bandeau de retour un texte que **nous n'avons pas écrit** : le
message d'une exception, venu du pilote SQLite, d'une réponse HTTP ou d'une trace réseau.

L'issue #2076 partait d'une analyse du code, sans qu'aucun débordement ait été observé, et supposait
que « la troncature de JavaFX suffit peut-être ». **La mesure dit l'inverse** : le libellé du bandeau
porte `wrapText`, donc rien n'est tronqué - un long message **enroule** et fait grandir le bandeau.

| Cas | Longueur | Hauteur du bandeau |
|---|---|---|
| refus métier écrit par nous | 58 car. | 56 px |
| échec HTTP typique | 125 car. | 56 px |
| message de pilote SQLite rappelant sa requête | 379 car. | **106 px** |
| collage arbitraire dans un champ de saisie | 625 car. | **186 px** |

> **Chiffres corrigés en 2026-07-30 (#2897).** Les premiers avaient été relevés sur une **imitation** du
> bandeau : l'outil de mesure montait un `Label` dans un `HBox` aux bonnes classes CSS sans passer par
> `BandeauRetour.installer`, donc sans l'icône de sévérité ni le bouton de fermeture. Ces deux enfants
> prennent 104 px de largeur : le libellé réel s'enroule plus tôt et le bandeau est plus haut. La
> conclusion ne change pas - c'est même pire que ce qui avait été mesuré.

En triant les dix-sept par **origine du texte**, un second défaut est apparu, plus coûteux : quatorze
passaient `refus.getMessage()` au lieu du `Throwable`, et court-circuitaient donc [`GesteAttendu`] -
l'enrichissement « où le régler » que l'[ADR 2635](2635-un-refus-dit-ce-qui-manque-la-surface-dit-quoi-faire.md)
venait de livrer. Un refus qui disait « la connexion à Vigie-Chiro est requise » perdait « depuis le
menu principal (☰) > Se connecter, puis recommencez ».

Les deux défauts ont la même cause : **on passait une chaîne là où on avait un objet**, et la chaîne
perdait en route ce que l'objet savait.

## Décision

**Le point d'entrée d'un texte venu d'ailleurs est le seul endroit où on le borne, et c'est aussi le
seul endroit où on peut l'enrichir.**

Concrètement, `RetourOperation` distingue deux portes :

| Porte | Ce qui passe | Ce qu'elle fait |
|---|---|---|
| `erreur(String)` | un texte **que nous avons écrit** | rien : sa longueur est notre responsabilité |
| `erreur(Throwable)` | un texte **venu d'ailleurs** | ajoute le geste attendu, puis **borne** à 240 caractères |
| `erreur(String, Throwable)` | notre contexte + ce que l'exception rapporte | garde le contexte entier, borne **seulement** le reste |

La borne n'est pas choisie mais **mesurée** : à la largeur d'un écran, le bandeau tient 120 caractères
par ligne ; deux lignes restent un bandeau, au-delà il pousse le contenu vers le bas.

Le détail complet n'est pas perdu : le journal le consigne (#1845), et c'est là qu'on va le chercher
pour diagnostiquer. Le bandeau dit ce qui s'est passé sans déverser.

## Conséquences

**Ce qu'on gagne.** Les deux propriétés que le type promettait sans les tenir - un retour est **borné**
(c'est ce qui le distingue d'un compte rendu, [ADR 0031](0031-un-retour-n-est-pas-un-compte-rendu.md))
et un refus **dit quoi faire** (ADR 2635) - sont désormais vraies par construction, sur les dix-sept.

**Ce qu'on accepte.** Un message externe long est coupé, et l'utilisateur doit ouvrir le journal pour
le lire en entier. C'est le bon arbitrage : personne ne diagnostique une contrainte de clé étrangère
depuis un bandeau, et personne ne devrait avoir à faire défiler un écran pour retrouver sa liste.

**Ce qui garde la porte.** `MessageExterneBorneTest` lit le **source** et refuse la forme
`erreur(x.getMessage())`. Il lit le source et non le bytecode pour la même raison que
[`IsolationFeatureSourcesTest`] : ce qu'on interdit est une **forme d'écriture**, qui ne laisse aucune
trace distinctive dans le `.class`.

Il en faut **une par forme de contournement**, et la clôture en a découvert deux de plus.

La troisième est née de l'audit d'harmonisation de la clôture et a été livrée à part, dans
**[#2841](https://github.com/echonuit/vigiechiro-pr-companion/issues/2841)** : douze appels écrivaient
`erreur -> canal.echec(erreur.getMessage())`. Le canal recevait une `String`, donc sa surcharge « texte
que nous avons écrit », et l'exception perdait les deux garanties **d'un saut d'indirection**. La
décision ci-dessus tenait donc à l'endroit exact où la garde regardait. Celle-ci suit la **forme** - une
lambda dont le paramètre est déballé par `getMessage()` - et non un nom de méthode : trois des douze
sites lui doivent leur découverte, leurs récepteurs s'appelant `signalerSourceIllisible` et
`marquerEchec`. Un journal reste libre de tout garder, puisque le motif exige que le nom déballé soit
celui du **paramètre de la lambda**.

La deuxième, elle, s'est révélée en falsifiant la première : rendre le défaut à
l'IHM la faisait bien rougir, mais le même défaut dans une commande CLI la laissait verte - son motif
ne connaissait que le vocabulaire de l'IHM (`erreur`, `avertissement`…), là où la CLI écrit sur
`getErr()`. La garde CLI suit donc la **variable attrapée** (`catch (RegleMetierException refus)`) et
non tous les `getMessage()` : une `IOException` sur un CSV n'a pas de besoin à nommer, et son message
brut est le bon.

## Alternatives écartées

**Borner dans le constructeur, pour tous les textes.** Simple, et faux : il aurait coupé des phrases que
nous avons relues, au lieu de couper un déversement. Le type ne peut pas deviner l'origine d'une
`String` - c'est précisément pourquoi la décision porte sur la **porte** et non sur le champ.

**Laisser JavaFX s'en charger.** L'hypothèse de départ de l'issue, démentie par la mesure : il n'y a
pas de troncature, il y a un enroulement.

**Tronquer à l'affichage, dans le bandeau.** Le composant aurait coupé sans distinguer non plus, et
aurait masqué le vrai problème : ce n'est pas le rendu qui déborde, c'est ce qu'on lui donne.

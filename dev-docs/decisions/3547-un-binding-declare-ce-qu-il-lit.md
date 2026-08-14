# ADR 3547 - Un binding déclare ce qu'il lit, et rien ne le vérifie

- **Statut** : Accepté - 2026-08-14, **amendé** par [ADR 3540](3540-un-cliquet-qui-compte-n-est-pas-la-preuve-de-la-regle.md) : sa vérification est **humaine** (le relevé consigné), le cliquet ne prouvant que le comptage
- **Chantier** : #3547 (lot 3 du chantier #3536)
- **Vérification** : certaine - `DeclarationDesBindingsTest#tout_nouveau_binding_est_vu`
- **Prolonge** : [ADR 3664](3664-un-releve-qui-n-a-pas-ouvert-les-fichiers-est-une-hypothese.md)

## Contexte

`Bindings.createXBinding(calcul, dependances…)` ne recalcule `calcul` que si l'une des `dependances`
s'invalide. **Rien ne vérifie que `calcul` n'en lit pas d'autres** : ni le compilateur, ni PMD, ni un
test. Le manquement est silencieux tant qu'une propriété correctement déclarée change juste après celle
qui manque.

Deux cas avaient été trouvés à la main pendant le lot 3 (#3546, #3548). La question de cette revue était
de savoir s'ils étaient les deux seuls. Les 63 sites du dépôt ont été ouverts, un par un.

## Ce que la revue a mesuré

| Verdict | Sites |
|---|---|
| complet | 61 |
| incomplet mais **inoffensif** | 1 |
| incomplet et **atteignable** | 1 |

L'issue en annonçait 62 : il y en avait **63**. Le 63ᵉ (`ImportationController:396`) est arrivé le
10/08, **deux jours après** la rédaction de l'issue, et rien ne l'a signalé. C'est ce petit écart qui a
décidé du garde.

**Le cas atteignable** est `SonsValidationController:353`, la barre de statut de la vue audio : une seule
dépendance déclarée pour trois lectures, dont un **champ** `source` qui alimente la zone gauche. Sur le
chemin d'erreur d'ouverture, `reinitialiser()` repose `ComptageAudio.VIDE`, la **même instance
constante** : `ObjectPropertyBase.set` compare par référence, zéro invalidation mesurée, la barre ne
peut pas se mettre à jour. Cinquième écran de la même famille (#3752).

**Le cas inoffensif** est `ActiviteController:221` : le calcul lit la liste `tous`, non déclarée. Il
faudrait que `tous` change **pendant qu'un filtre masque tout** pour que le message d'état vide mente ;
or l'écran charge toujours avant de filtrer, sur un contrôleur neuf, et n'a aucun rechargement. Inoffensif
**par un ordre**, donc : un bouton « rafraîchir » suffirait à le rendre atteignable.

## Décision

**Un binding déclare tout ce que son calcul lit, méthodes appelées comprises.** Et puisque cette règle
n'est pas vérifiable, ce qui l'entoure l'est :

1. **Un cliquet compte les sites** (`DeclarationDesBindingsTest`). Il ne vérifie **aucune déclaration** :
   il garantit qu'un nouveau site ne peut pas entrer sans être vu. C'est peu, et c'est exactement ce qui
   manquait quand le 63ᵉ est passé.
2. **Une lambda qui lit un champ n'a pas une dépendance manquante**, elle a une dépendance **absente du
   modèle**. Les deux se ressemblent à la lecture ; le remède diffère : rendre la valeur observable,
   comme #3548 l'a fait pour le contexte du passage sur trois écrans.
3. **Un « inoffensif » dit par quel ordre il l'est.** C'est ce qui permet de rejuger le site quand cet
   ordre change, au lieu de relire tout le calcul.

## Ce que nous avons écarté

**Une règle statique « lu ⊆ déclaré ».** Il faudrait suivre les appels depuis la lambda : ArchUnit ne
sait pas le faire, et une heuristique textuelle produirait ses faux positifs sur les **références de
méthode**, c'est-à-dire exactement là où le défaut se cache - les trois cas trouvés sont tous derrière
une référence de méthode ou un appel indirect.

**Se fier au sondage d'entrée.** L'issue s'appuyait sur une dizaine de sites déjà regardés, sans rien
trouver. Les dix ont été rouverts. Le sondage disait vrai, mais l'[ADR 3664](3664-un-releve-qui-n-a-pas-ouvert-les-fichiers-est-une-hypothese.md)
demande qu'un relevé se rejoue, et la conclusion de cette revue reposait entièrement sur la qualité de
ses négatifs.

## Conséquences

La revue est **datée** : elle vaut pour le commit qu'elle nomme, et pour lui seul. Le cliquet ne la
prolonge pas, il signale quand elle est à reprendre - sur un site, pas sur 63.

L'inventaire des 63 lignes, avec un verdict et une raison par ligne, est publié en commentaire de #3547.
Le relire coûte moins que le rejouer : c'est sa raison d'être.

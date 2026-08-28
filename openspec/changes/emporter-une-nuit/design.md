## Context

Le lot 0 de l'EPIC #3848 instruit le périmètre de « reprendre le travail d'une nuit sur un autre
poste ». Il rend un document, pas du code. Voir `proposal.md` pour le pourquoi.

Trois contraintes mesurées cadrent l'approche, et aucune n'était dans l'énoncé de l'EPIC.

**Une seule sélection d'écoute par passage, garantie par le schéma.**

```sql
-- V01__schema.sql
passage_id INTEGER NOT NULL UNIQUE REFERENCES passage(id) ON DELETE CASCADE
```

**Ce qu'un relecteur produit tient dans deux colonnes.** `selection_sequence.verdict`, un `TEXT`
nullable posé par V27, et `passage.verification_verdict`, cache dérivé recalculable. L'aller pèse les
séquences d'une nuit ; le retour pèse quelques kilo-octets.

**L'avis d'expert ne se saisit pas localement.** V26 pose que `taxon_validator` et
`validator_certainty` sont « toujours un REFLET du serveur, jamais une saisie locale ». Ce que le
MNHN tranche vient du serveur des deux côtés, et n'a rien à faire dans un paquet.

## Goals / Non-Goals

**Goals**

- Décider le régime d'emport d'une nuit, et le contenu du paquet.
- Nommer ce que le schéma actuel ne sait pas représenter, avant qu'un lot suivant s'y heurte.

**Non-Goals**

- Écrire le code : les lots 1 à 3 le font.
- Trancher la réconciliation de deux avis d'experts divergents. Ce n'est pas un conflit technique
  mais une question de domaine, et elle déborde ce chantier.
- Toucher au contrôle de concurrence de la plateforme : il n'est pas sous notre contrôle.

## Decisions

### D1. Le paquet emporte la sélection d'écoute, et elle est figée

**Tranché.** Le paquet emporte les séquences de la **sélection d'écoute**, et cette sélection est
**figée** pour le relecteur.

Le motif tient à l'arithmétique de l'échantillonnage. La sélection tire dix à trente séquences
réparties sur une nuit qui en porte beaucoup plus. Si le relecteur régénérait la sienne, les deux
échantillons se recouvriraient à hauteur de `t × t / N` séquences, où `t` est la taille du tirage et
`N` celle de la nuit : à trente sur cinq cents, cela fait **deux séquences jugées par les deux**. Les
avis ne seraient plus comparables, et la reprise devrait fusionner deux échantillons disjoints.

Figer la sélection rend les deux verdicts comparables ligne à ligne, ce qui est la condition pour que
les colonnes de la décision D3b aient un sens.

*Écarté : toutes les séquences transformées de la nuit.* C'était le choix initial, retenu pour
permettre au relecteur de régénérer sa sélection. L'arithmétique ci-dessus l'a défait : la
régénération produit deux échantillons quasi disjoints, donc deux avis qu'on ne peut pas confronter.
Emporter la nuit entière coûtait alors un ordre de grandeur de volume pour une faculté dont on ne
savait plus quoi faire au retour.

*Écarté : tout, bruts compris.* Les enregistrements sont en 384 kHz : le volume d'une nuit entière
de bruts se compte en dizaines de gigaoctets, et le lot 1 de l'EPIC exige déjà « l'estimation de
volume annoncée avant écriture ». Le besoin que les bruts servent, réactiver la nuit ailleurs, n'est
pas celui d'une relecture.

### D2. Les identifiants de plateforme ne voyagent pas dans le paquet

**Tranché, par ricochet du régime.** L'EPIC hésitait : les embarquer « permet de poursuivre le dépôt
depuis l'autre poste, mais fait circuler un lien vers un compte qui n'est pas celui du
destinataire ». Le relecteur juge, l'expéditeur publie : le lien vers le compte n'a aucune raison de
partir, et l'hésitation tombe sans arbitrage.

### D3. Le régime d'emport : la copie signée

**Tranché.** Le relecteur juge sur son exemplaire, et son avis revient comme un artefact **signé de
son pseudo**, jamais fondu dans celui de l'expéditeur. Les deux postes travaillent pendant ce temps.

Quatre régimes ont été pesés, avec leur coût mesuré.

| Régime | Le relecteur juge | Le travail revient | L'origine pendant |
|---|---|---|---|
| Prêt | oui | oui, automatiquement | verrouillée, lisible |
| Déménagement | oui | oui, par le même geste | n'a plus la nuit |
| Copie signée | oui | oui, comme artefact **attribué** | continue de travailler |
| Consultation | non | non | continue de travailler |

*La consultation est déjà écartée par l'EPIC lui-même*, dont le tableau d'ouverture range « export
des observations avec leurs sons » parmi les mécanismes qui ne conviennent pas : « conçu pour faire
écouter ; rien ne se réimporte ».

**La copie signée a son précédent dans le dépôt**, et il n'était pas sur la table. V26 devait loger
l'avis d'un expert du MNHN sur une détection déjà jugée par Tadarida puis corrigée par l'observateur.
Elle n'a pas dupliqué l'observation, elle a ajouté `taxon_validator` et `validator_certainty` **à
côté** : trois avis sur la même ligne, aucun écrasé, aucune réconciliation.

Ce régime épouse une propriété du domaine que les trois autres manquent : **une validation
s'attribue**. Deux experts qui divergent ne produisent pas un conflit à résoudre, mais deux avis,
chacun signé.

**Pourquoi lui plutôt que les deux autres.** Une validation naturaliste s'attribue à une personne :
deux avis divergents sur la même séquence ne sont pas une anomalie à réduire, ce sont deux jugements
d'experts, et les écraser l'un par l'autre détruirait une information que le domaine considère comme
la donnée elle-même. C'est aussi le seul régime où personne n'est jamais bloqué.

*Écarté : le prêt.* Compatible avec le schéma actuel sans rien y changer, puisque le gel de l'origine
garantit qu'une seule sélection évolue à la fois. Rejeté parce qu'il immobilise l'expéditeur pendant
toute la relecture, et parce qu'il écrase l'avis reçu sur le sien au lieu de le conserver à côté.

*Écarté : le déménagement.* Le moins de code neuf des trois, et compatible lui aussi. Rejeté pour la
même raison, aggravée : l'expéditeur n'a plus rien du tout, et perdre le paquet perd la nuit.

*Écarté : la consultation.* Récusée par l'EPIC avant ce lot.

### D3b. L'avis du relecteur se range en colonnes, à côté du nôtre

La copie signée est **le seul des trois régimes à ne pas tenir dans le schéma actuel**. Les deux
postes travaillent, donc les deux régénèrent leur sélection, donc deux sélections légitimes existent
pour un passage que `listening_selection.passage_id UNIQUE` n'autorise qu'à en avoir une.

**Tranché.** `selection_sequence` gagne deux colonnes additives, `verdict_relecteur` et
`relecteur_pseudo`, comme V27 lui avait ajouté `verdict`.

**Le dépôt a déjà tranché ce problème une fois, dans le même sens.** V26 devait loger l'avis d'un
expert du MNHN sur une détection déjà jugée par Tadarida puis corrigée par l'observateur. Elle n'a
pas dupliqué l'observation : elle a ajouté des colonnes à côté.

```sql
-- V26__validation_expert.sql
ALTER TABLE observation ADD COLUMN taxon_validator TEXT REFERENCES taxon(code);
ALTER TABLE observation ADD COLUMN validator_certainty TEXT;
```

Trois avis sur la même détection, sur la même ligne. Le verdict d'un relecteur sur une séquence est
le même motif, et le résoudre autrement créerait deux façons de dire « quelqu'un d'autre a jugé
ceci ».

*Écarté : retirer le `UNIQUE` sur `listening_selection.passage_id`.* Ce serait la voie générale, et
elle porterait N relecteurs. Mesuré : **vingt-trois classes lisent le verdict d'un passage**, du
tableau multisite et ses filtres au solde de saison, en passant par quatre commandes de la ligne de
commande et la vue du passage. « Le verdict du passage » est une notion consommée partout ; la faire
passer à N valeurs déborde très largement ce chantier.

*Écarté : garder l'avis revenu comme un document à côté.* Un tableur signé, déposé auprès de la nuit,
ne coûterait presque rien à produire. Rejeté parce qu'un avis qui vit dans un document
**se lit, il ne se manipule pas** : il ne s'affiche pas près de la séquence qu'il juge, ne se filtre
pas, ne s'agrège pas. Le relecteur aurait travaillé pour un fichier que personne n'ouvre.

**La limite se dit plutôt qu'elle ne se cache.** Deux colonnes portent **un** relecteur, pas N,
exactement comme V26 porte un validateur. C'est ce que le régime décrit : une nuit se confie à
quelqu'un, pas à un comité. Le jour où il en faudra plusieurs, la voie générale sera toujours là, et
elle sera un chantier à elle seule.

**Un second avis ne se glisse pas en silence.** Puisque les deux colonnes portent un relecteur, un
avis qui arrive sur une nuit qui en porte déjà un remplace le précédent. Le remplacement reste
possible, jamais tacite : double confirmation qui nomme le relecteur présent et le nombre de verdicts
perdus, sur le patron de `ConfirmationsImport.confirmerEcrasement` (#279, #2223). *Écarté : refuser
l'import.* Rien ne serait effacé, mais le premier relecteur qui répond verrouillerait la nuit, et
aucun geste ne permettrait plus d'en accueillir un autre.

### D4. L'attribution, si le régime la demande, coûte peu

Mesuré dans `connexion/model/StockageConnexion.java` : l'identité de l'utilisateur est déjà persistée
localement dans `connexion.json`, avec son `id`, son **`pseudo`** et son `role`, et se lit **hors
connexion**. Le nom lisible est donc déjà là, et le relecteur n'a rien à saisir : une identité qu'on
ne tape pas ne se tape pas de travers.

**Une limite à traiter dans la conception.** `profil()` passe par `sessionValide()`, qui filtre sur
la péremption du jeton à quatorze jours. Au-delà, l'identité est rendue vide alors qu'elle est
physiquement dans le fichier. Un relecteur qui juge sans s'être reconnecté depuis deux semaines
produirait des verdicts non attribuables. Le pseudo n'est jamais saisi : une identité qu'on ne tape
pas ne se tape pas de travers.

*Le remède ne coûte rien* : apposer l'identité sur le paquet **à son ouverture**, tant qu'elle est
valide, plutôt que de la lire au moment du jugement.

### D5. Le plan précède l'écriture, et se lit avant de copier

**Le dépôt porte déjà ce patron, et l'a chiffré trois fois.** `CompacteurDepot.planifier()` porte la
mention « **Planifie sans rien écrire** » (#1994) et rend une partition déterministe.
`estimationTailleDepot()` est pure et statique, « exposée pour que l'IHM anticipe avec **le même
calcul** que le garde-fou avant écriture » (#808). `verifierEspaceDisque()` refuse avant d'écrire
quand le disque n'a manifestement pas la place (#769).

Le lot 1 de l'EPIC exige déjà « l'estimation de volume annoncée avant écriture ». Ce n'est donc pas
une précaution à inventer : c'est le patron de la maison, qu'il faut suivre plutôt que refaire.

## Risks / Trade-offs

**Le relecteur ne peut pas contester l'échantillonnage.** → Assumé. Il juge le tirage de
l'expéditeur, et s'il l'estime mal réparti, il le dit hors de l'outil. Ouvrir la régénération
rouvrirait le problème que D1 vient de fermer : deux échantillons quasi disjoints, et une reprise qui
n'aurait plus rien à comparer.

**Deux colonnes portent un relecteur, pas plusieurs.** → Assumé, et c'est le même choix que V26 pour
le validateur. Une nuit se confie à quelqu'un, pas à un comité. Un second prêt écraserait l'avis du
premier : l'écran doit donc le dire avant d'importer, jamais après.

**L'identité s'évapore au bout de quatorze jours.** → `profil()` filtre sur la péremption du jeton,
et rend une identité vide au-delà, alors qu'elle est écrite dans `connexion.json`. Un avis non
attribuable n'a plus de sens sous ce régime. Le paquet porte donc l'identité **apposée à son
ouverture**, tant qu'elle est valide, et non lue au moment du jugement.

**L'agrégation du verdict ignore le relecteur.** → `AgregationVerdict.deriver(List<VerdictFichier>)`
dérive le verdict du passage depuis les seuls verdicts de l'expéditeur, et vingt-trois classes
consomment ce verdict. Il **reste inchangé** : l'avis du relecteur s'affiche, il ne vote pas. Décider
qu'il pèse sur le verdict final serait une décision de domaine, et elle n'est pas dans ce lot.

**Le paquet pèse la sélection, soit dix à trente séquences.** → Un ordre de grandeur de moins que la
nuit entière. Le plan d'export annonce quand même l'estimation avant d'écrire, ventilée par nature :
une clé pleine reste une clé pleine, et personne ne découvre le volume après coup.

## Open Questions

Aucune. Le régime d'emport n'en est pas une : il change les specs et les tâches, il se tranche avant
que cette note soit close.

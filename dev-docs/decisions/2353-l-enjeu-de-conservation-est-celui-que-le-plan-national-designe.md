# ADR 2353 — L'enjeu de conservation est celui que le plan national désigne, pas celui qu'on déduit

- **Statut** : Accepté — 2026-07-28
- **Chantier** : #2353 (lot 3 de l'EPIC #2348)
- **Vérification** : certaine — `EspecesPrioritairesReferentielTest#marque_toutes_les_prioritaires_connues`

## Contexte

Sur une nuit à 4 000 contacts, les quelques espèces à enjeu de conservation sont noyées. C'est pourtant l'information qu'un naturaliste cherche en premier, et le produit ne la portait nulle part : la table `taxon` connaît le code, le binôme latin et le nom vernaculaire, rien d'autre.

Reste à dire **ce qu'est** un enjeu. Le mot n'a pas de sens en soi : toutes les chauves-souris de France sont intégralement protégées depuis l'arrêté du 23 avril 2007. Un marquage « espèce protégée » marquerait donc les 36 espèces et n'apprendrait rien. Il fallait une source qui **hiérarchise**, et qui le fasse sous une autorité que le produit puisse citer.

## Décision

**Est à enjeu ce que le Plan National d'Actions Chiroptères 2016-2025 désigne comme espèce prioritaire.** Le plan retient **19 espèces** sur les 36 de métropole, sélectionnées sur quatre critères qu'il annonce : la directive Habitats-Faune-Flore, l'accord EUROBATS, la Liste rouge nationale des mammifères de France métropolitaine, et le diagnostic des 34 espèces du bilan du plan précédent.

Trois choix découlent de cette source.

**Un booléen, parce que la source est binaire.** Le plan dit prioritaire ou non, sans gradation. Embarquer une échelle graduée demanderait de la fabriquer, donc de porter un jugement écologique que ce produit n'a pas à porter. On embarque ce que la source dit.

**Une jointure par nom latin, pas par code.** Le binôme est ce que la source nomme, et le pivot stable entre référentiels (TAXREF). Le code Tadarida est une convention de l'outil de détection ; joindre dessus reviendrait à traduire la source avant de l'enregistrer.

**Une table latérale de présence**, `taxon_prioritaire`, sur le patron de V34 `passage_opportuniste` et V35 `site_tiers` — et non une colonne sur `taxon`, comme l'issue le proposait. `taxon` est lu par toutes les projections et son record est construit en seize endroits ; les ~300 taxons non prioritaires ne coûtent aucune ligne ; et surtout, le plan courant **s'achève en 2025** : sa liste sera remplacée. Une table latérale se remplace d'un `DELETE` suivi d'un `INSERT`, sans toucher au référentiel taxonomique lui-même.

## Conséquences

- **Dix-sept espèces sont marquées, pas dix-neuf.** Deux des espèces prioritaires n'existent pas dans le référentiel Tadarida embarqué (V05) : *Rhinolophus mehelyi*, d'une extrême rareté en France, et *Myotis escalerai*. C'est un fait à constater, pas un écart à corriger en forçant la correspondance. Le jour où le référentiel les portera, le même `SELECT` les marquera sans être réécrit — et un test le rappellera.
- **`Myotis sp. A ('southern' Natterer)` (code `MyospA`) n'est pas marqué**, bien qu'il recouvre vraisemblablement *Myotis escalerai*, issu de la scission du complexe *nattereri*. Vraisemblablement, pas certainement : aucune source consultée ne l'affirme. Marquer sur une déduction taxonomique reviendrait à fabriquer de la donnée de référence sous couvert de la lire.
- Le marquage est **en lecture seule depuis l'application** : `TaxonPrioritaireDao.insert/update` refusent. La liste vient du plan national, elle ne se négocie pas depuis une IHM.
- Un taxon absent du référentiel de conservation **n'est ni marqué, ni signalé comme anormal** : l'immense majorité des taxons détectés (oiseaux, orthoptères, micromammifères) sont dans ce cas, et le plan ne parle que de chiroptères.
- Le port `EspecesPrioritaires` livre l'ensemble des codes en une lecture ; les écrans en gardent un instantané et le consultent par ligne affichée, comme pour les nuits opportunistes (ADR 2614).

## Alternatives écartées

- **La Liste rouge UICN France (mammifères continentaux, 2017).** Recommandation initiale, écartée avec l'utilisateur : elle offre une échelle graduée (CR/EN/VU/NT/LC/DD) et une belle traçabilité, mais elle répond à « quel est le risque d'extinction ? » là où le produit veut répondre à « sur quoi la politique publique demande-t-elle de l'attention ? ». Elle reste un **enrichissement possible** le jour où un statut gradué serait voulu.
- **La seule annexe II de la directive Habitats.** Binaire et juridique, mais elle ignore les espèces communes dont le plan s'est justement saisi pour la mortalité éolienne — noctules, pipistrelles, sérotine commune. Une lecture patrimoniale classique qui manquerait la moitié du sujet actuel.
- **Les listes rouges régionales.** Plus proches du terrain, mais elles imposeraient un choix de région explicite, une couverture inégale et autant de sources à dater. À reconsidérer si le besoin s'exprime, pas avant.
- **La présence d'une fiche PNA** (ressource `especes-pna.properties`, 33 entrées) comme critère de marquage. Le plus court, et **faux** : avoir une fiche descriptive n'est pas être prioritaire. Ce raccourci aurait marqué la Barbastelle d'Europe, que le plan cite explicitement comme espèce **non** prioritaire bénéficiant des mesures prises pour le Murin de Bechstein.

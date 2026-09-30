---
type: adr
title: "La première ligne du journal ne date aucune nuit"
status: stable
article: A21
chantier: "#5629 (les nuits jugées à l'import, retour de terrain de la 2.193.0, EPIC #5596), lots #5600 et #5631"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "InspectionImportViewModelTest#une_nuit_absente_de_la_carte_n_est_pas_signalee"
  - "AnalyseCoherenceTest#carte_reutilisee_a_une_nuit_n_est_pas_incoherente"
verified:
  - by: machine:ci
    at: 2026-09-30
relations:
  complete: ["0009"]
generated:
  by: "process:assistance-par-agents"
---

# La première ligne du journal ne date aucune nuit

## Contexte

L'[ADR 0009](0009-la-nuit-est-l-unite-bornee-a-midi.md) fait de la nuit l'unité de traitement. Elle ne
dit pas d'où l'on tire la date d'une nuit, et le journal de l'enregistreur offrait une réponse
tentante : `JournalParse.dateDebut`, la date de sa première ligne.

Cette réponse est fausse sur le terrain. Le journal `LogPR` est **circulaire** (règle R19) : il
continue de raconter le déploiement bien après que les enregistrements de ces nuits ont été importés
puis effacés de la carte. Sur une carte réutilisée, sa première ligne est donc souvent une nuit qui
n'y est plus.

Le retour de terrain de Samuel sur la 2.193.0 l'a montré deux fois sur la même carte :

- l'inspection annonçait « Cette nuit a déjà été importée » en nommant un passage du 19 août, au-dessus
  de trois nuits neuves du 22 au 24 (#5600) ;
- le contrôle de cohérence déclarait le journal étranger à une carte qui ne portait qu'une nuit, parce
  que sa première ligne datait de cinq jours plus tôt (#5631).

L'import lui-même avait déjà cessé de dater d'après le journal : `ServiceImport` tire la date d'une
nuit de `partitionNuits()`, et son commentaire dit pourquoi. La règle vivait à un seul endroit sans
être écrite, et deux autres l'ignoraient.

## Décision

**Les nuits d'une carte sont celles de ses enregistrements**, tirées des horodatages de leurs noms et
bornées à midi. C'est vrai pour dater un passage, pour dire qu'une nuit est déjà importée et pour
reconnaître une nuit récupérée de Vigie-Chiro.

**Le journal n'est cru que pour ce qu'il raconte** : le numéro de série de l'enregistreur, et les nuits
dont il porte un cycle d'acquisition. La cohérence de date se juge sur ces nuits racontées : un
journal qui en raconte d'autres en plus de celles de la carte est cohérent.

**Sa première ligne n'est qu'un repli**, employé seulement quand ni les noms des enregistrements ni
les cycles ne disent rien. Un message qui nomme une date jugée nomme celle-là même que la règle a
jugée, et non la première ligne (#5653).

**L'exemption multi-nuits de la cohérence reste.** Sur une carte laissée tourner plusieurs nuits, le
journal circulaire ne raconte parfois que les premières. Juger chaque date des enregistrements contre
les nuits racontées y signalerait à tort les dernières. C'est une décision de ne pas étendre la règle,
prise à la passe 7 de la clôture de #5629.

## Conséquences

- La règle est tenue à trois endroits qui s'accordent : `ServiceImport` (la date du passage),
  `NuitsDeLaCarte` (les nuits déjà importées et récupérées, avant l'import) et `AnalyseCoherence` (la
  cohérence du journal). Les deux voies de la nuit déjà importée, avant et après l'import, aboutissent
  à la même requête, `passagesDeLaNuit(série, date)`.
- La spécification `importation/import-d-une-carte` porte les exigences et leurs scénarios. Cette ADR
  en porte le pourquoi.
- `JournalParse.dateDebut` reste lue, et doit le rester : c'est le repli, et l'analyseur en a besoin.
  Qui la voit appelée ailleurs pour dater une nuit a trouvé une régression.
- La recette peut rejouer les deux défauts : `sd-multi-nuits` pour la nuit déjà importée, et
  `sd-carte-reutilisee`, dont le garde-fou des cartes rougit si la règle des nuits racontées est
  neutralisée.

## Vérification

`certaine`. `InspectionImportViewModelTest#une_nuit_absente_de_la_carte_n_est_pas_signalee` rejoue la
carte de Samuel : un journal qui commence par une nuit importée puis effacée ne fait signaler aucune
nuit. `AnalyseCoherenceTest#carte_reutilisee_a_une_nuit_n_est_pas_incoherente` lit un vrai journal
circulaire par l'analyseur. Les deux étaient rouges avant leur correctif.

## Alternatives écartées

- **Corriger la première ligne en la sautant quand sa nuit est absente.** Cela garde le journal comme
  source de la date et ajoute une exception. Les enregistrements disent la date sans exception.
- **Juger la cohérence sur « au moins une nuit racontée en commun ».** Sur une carte à une nuit, c'est
  la même chose. Au-delà, l'exemption multi-nuits décide déjà, et une seconde règle aurait fait deux
  réponses à la même question.

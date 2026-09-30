---
type: adr
title: "Une carte de plusieurs nuits se juge sur les nuits que le journal raconte"
status: stable
article: A21
chantier: "#5629 (les nuits jugées à l'import), lot #5669 de sa clôture"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "AnalyseCoherenceTest#carte_multi_nuits_et_journal_etranger_incoherente"
  - "AnalyseCoherenceTest#carte_multi_nuits_journal_circulaire_coherente"
verified:
  - by: machine:ci
    at: 2026-09-30
relations:
  amende: ["5629"]
generated:
  by: "process:assistance-par-agents"
---

# Une carte de plusieurs nuits se juge sur les nuits que le journal raconte

## Contexte

L'[ADR 5629](5629-la-premiere-ligne-du-journal-ne-date-aucune-nuit.md) juge la cohérence de date du
journal sur les nuits qu'il raconte, et gardait une exemption : une carte dont les enregistrements
s'étalent sur plus d'une nuit n'était pas jugée sur la date. La raison tenait : le journal est
circulaire, et sur une carte laissée tourner plusieurs nuits il ne raconte parfois que les premières.
Exiger que chaque nuit soit racontée aurait signalé à tort une carte ordinaire.

Mais l'exemption couvrait aussi le cas qu'elle n'avait pas à couvrir : un journal d'un autre
déploiement, posé sur une carte de plusieurs nuits, et qui n'en raconte **aucune**. Il n'était signalé
que si sa série différait. La clôture de #5629 l'a montré comme défaut assumé, et le porteur a demandé
de le corriger.

## Décision

**Sur une carte de plusieurs nuits, une seule nuit racontée suffit, et aucune dit que le journal n'est
pas le sien.** La date y est jugée incohérente quand le journal raconte au moins un cycle et
qu'aucune date des enregistrements ne tombe dans la nuit de l'un d'eux.

**Sans cycle lisible, rien ne change** : la première ligne ne date qu'une nuit, et une carte de
plusieurs nuits n'est pas jugée sur elle.

**Une carte d'une nuit garde la règle de 5629** : chacune de ses dates doit tomber dans une nuit
racontée.

Le porteur a tranché entre deux règles pour la carte de plusieurs nuits : « aucune nuit racontée »,
retenue, et « une nuit non racontée », écartée parce qu'elle signalait à tort `sd-multi-nuits`, dont le
journal circulaire a perdu les deux dernières nuits.

## Conséquences

- L'exemption multi-nuits de 5629 est levée pour les journaux qui racontent des cycles. Le reste de
  5629 fait foi.
- La carte de recette `sd-journal-etranger-multi` rejoue le cas, et `apercu-import-journal-etranger.png`
  le montre, avec le détail qui nomme les nuits racontées (#5653).
- La spécification `importation/import-d-une-carte` porte la règle et ses deux scénarios de plusieurs
  nuits.

## Vérification

`certaine`. `AnalyseCoherenceTest#carte_multi_nuits_et_journal_etranger_incoherente` lit une vraie carte
de trois nuits sous un journal d'août : rouge avant ce lot. Son contrôle,
`#carte_multi_nuits_journal_circulaire_coherente`, garde cohérente la carte dont le journal ne raconte
que la première nuit. `GenerationCartesSDCliquetTest` juge les deux cartes de recette.
